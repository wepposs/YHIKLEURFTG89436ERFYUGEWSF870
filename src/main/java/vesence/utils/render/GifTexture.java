package vesence.utils.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.w3c.dom.Node;
import vesence.renderengine.render.Renderer2D;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Анимированный GIF, отрисовываемый БЕЗ просадок FPS и БЕЗ артефактов.
 *
 * Надёжный «профессиональный» путь: декод и композит GIF делаем ОДИН раз
 * в фоновом потоке, а каждый кадр регистрируем как обычную текстуру Minecraft
 * ({@link NativeImageBackedTexture} в TextureManager). Заливкой в GPU,
 * форматом и состоянием занимается сам Blaze3D — поэтому нет ни «шума», ни
 * перевёрнутости, ни битого stride (это и была причина артефактов у сырого
 * glTexImage2D в середине батча). Рисуем через тот же путь, что и остальные
 * картинки клиента — {@link Renderer2D#drawImage(Identifier, float, float, float, float, int, boolean, float)}.
 *
 * Кадры грузятся ПОЛНОСТЬЮ заранее: пока не зарегистрированы все кадры,
 * не рисуем ничего (никакого «предпросмотра» полузагруженной гифки).
 * Временные CPU-буферы (PNG-байты кадров) освобождаются сразу после
 * регистрации — мусор не копится.
 */
@Environment(EnvType.CLIENT)
public final class GifTexture {

   private static final Map<String, GifTexture> CACHE = new ConcurrentHashMap<>();
   /** Сколько кадров регистрируем за один кадр рендера (сглаживает нагрузку). */
   private static final int REGISTER_PER_FRAME = 32;

   private final String resourcePath;
   private final String key;

   private volatile boolean decoded = false;
   private volatile boolean failed = false;
   private volatile boolean ready = false;

   // Готовится фоновым потоком, публикуется через volatile decoded.
   private byte[][] framePng;    // PNG-байты кадра; обнуляются после регистрации
   private int[] delaysMs;
   private int frameW, frameH;
   private int frameCount;
   private int totalDurationMs;

   // Только render-поток.
   private Identifier[] frameIds;
   private int registeredCount = 0;
   private long startTimeMs = -1L;

   private GifTexture(String resourcePath) {
      this.resourcePath = resourcePath;
      this.key = resourcePath.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
   }

   /** Возвращает (и при первом обращении стартует фоновый декод) гифку по
    *  пути ресурса в classpath, напр. "assets/vesence/images/logo.gif". */
   public static GifTexture of(String resourcePath, int maxSize) {
      GifTexture t = CACHE.get(resourcePath);
      if (t == null) {
         t = new GifTexture(resourcePath);
         GifTexture prev = CACHE.putIfAbsent(resourcePath, t);
         if (prev != null) {
            return prev;
         }
         t.startDecode(maxSize <= 0 ? 128 : maxSize);
      }
      return t;
   }

   public boolean isReady() {
      return ready;
   }

   /** Рисует гифку, полностью заполняя рект (object-fit: cover), по центру,
    *  с обрезкой по скруглённому ректу. Пока не готова — не рисует ничего. */
   public void drawCover(Renderer2D r, float x, float y, float w, float h, float rounding) {
      Identifier id = this.currentId();
      if (id == null) return;

      float ar = (float) frameW / (float) frameH;
      float rectAr = w / h;
      float dw, dh;
      if (ar > rectAr) { dh = h; dw = h * ar; } else { dw = w; dh = w / ar; }
      float dx = x + (w - dw) / 2f;
      float dy = y + (h - dh) / 2f;

      r.pushRoundedClipRect(x, y, w, h, rounding, rounding, rounding, rounding);
      r.drawImage(id, dx, dy, dw, dh, -1, false, 0f);
      r.popClipRect();
   }

   /** Рисует гифку, растянутую под рект (со скруглением). */
   public void draw(Renderer2D r, float x, float y, float w, float h, float rounding) {
      Identifier id = this.currentId();
      if (id != null) r.drawImage(id, x, y, w, h, -1, false, rounding);
   }

   private Identifier currentId() {
      if (failed) return null;
      if (!ready) {
         this.ensureRegistered();
         if (!ready) return null;
      }
      return frameIds[this.currentFrameIndex()];
   }

   private int currentFrameIndex() {
      if (frameCount <= 1) return 0;
      long now = System.currentTimeMillis();
      if (startTimeMs < 0) startTimeMs = now;
      long elapsed = (now - startTimeMs) % totalDurationMs;
      int acc = 0;
      for (int i = 0; i < frameCount; i++) {
         acc += delaysMs[i];
         if (elapsed < acc) return i;
      }
      return frameCount - 1;
   }

   /** Регистрация кадров как текстур MC (render-поток). Инкрементально, но
    *  ready=true только когда зарегистрированы ВСЕ кадры — без показа
    *  недогруженной гифки. */
   private void ensureRegistered() {
      if (ready || failed || !decoded) return;
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc == null || mc.getTextureManager() == null) return;
      try {
         if (frameIds == null) frameIds = new Identifier[frameCount];
         int end = Math.min(frameCount, registeredCount + REGISTER_PER_FRAME);
         for (int i = registeredCount; i < end; i++) {
            final int fi = i;
            NativeImage img = NativeImage.read(new ByteArrayInputStream(framePng[i]));
            NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "vesence_gif_" + key + "_" + fi, img);
            // ВАЖНО: путь идентификатора ДОЛЖЕН оканчиваться на ".png" — только
            // тогда Renderer2D.resolveTextureId ищет текстуру в TextureManager
            // (resolveFromTextureManager) и берёт её GL-id. Без ".png" картинка
            // не находится и квадрат пустой.
            Identifier id = Identifier.of("vesence", "gifframe/" + key + "/" + i + ".png");
            mc.getTextureManager().registerTexture(id, tex);
            try { tex.getGlTextureView(); } catch (Throwable ignored) {} // форсим заливку в GPU сейчас
            frameIds[i] = id;
            framePng[i] = null; // освобождаем PNG-байты — держит GPU-текстура
         }
         registeredCount = end;
         if (registeredCount >= frameCount) {
            framePng = null;
            ready = true;
         }
      } catch (Throwable e) {
         failed = true;
      }
   }

   // ── decode (фоновый поток) ────────────────────────────────────────────────

   private void startDecode(int maxSize) {
      Thread th = new Thread(() -> {
         try {
            this.decode(maxSize);
         } catch (Throwable e) {
            this.failed = true;
         }
      }, "vesence-gif-decode");
      th.setDaemon(true);
      th.setPriority(Thread.MIN_PRIORITY);
      th.start();
   }

   private void decode(int maxSize) throws Exception {
      InputStream in = GifTexture.class.getClassLoader().getResourceAsStream(resourcePath);
      if (in == null) { failed = true; return; }

      List<byte[]> pngs = new ArrayList<>();
      List<Integer> delays = new ArrayList<>();
      int outW, outH;

      try (ImageInputStream iis = ImageIO.createImageInputStream(in)) {
         ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
         reader.setInput(iis, false);
         int n = reader.getNumImages(true);
         if (n <= 0) { failed = true; return; }

         BufferedImage first = reader.read(0);
         int canvasW = first.getWidth();
         int canvasH = first.getHeight();
         try {
            IIOMetadata sm = reader.getStreamMetadata();
            if (sm != null) {
               IIOMetadataNode root = (IIOMetadataNode) sm.getAsTree(sm.getNativeMetadataFormatName());
               IIOMetadataNode lsd = child(root, "LogicalScreenDescriptor");
               if (lsd != null) {
                  int lw = intAttr(lsd, "logicalScreenWidth", canvasW);
                  int lh = intAttr(lsd, "logicalScreenHeight", canvasH);
                  if (lw > 0) canvasW = lw;
                  if (lh > 0) canvasH = lh;
               }
            }
         } catch (Throwable ignored) {}

         float scale = 1f;
         int maxDim = Math.max(canvasW, canvasH);
         if (maxDim > maxSize) scale = (float) maxSize / maxDim;
         outW = Math.max(1, Math.round(canvasW * scale));
         outH = Math.max(1, Math.round(canvasH * scale));

         BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
         Graphics2D g = canvas.createGraphics();
         BufferedImage prevSnapshot = null;

         for (int i = 0; i < n; i++) {
            BufferedImage frame = (i == 0) ? first : reader.read(i);
            int left = 0, top = 0, delayCenti = 10, disposal = 0;
            try {
               IIOMetadata meta = reader.getImageMetadata(i);
               IIOMetadataNode mroot = (IIOMetadataNode) meta.getAsTree(meta.getNativeMetadataFormatName());
               IIOMetadataNode idesc = child(mroot, "ImageDescriptor");
               if (idesc != null) {
                  left = intAttr(idesc, "imageLeftPosition", 0);
                  top = intAttr(idesc, "imageTopPosition", 0);
               }
               IIOMetadataNode gce = child(mroot, "GraphicControlExtension");
               if (gce != null) {
                  delayCenti = intAttr(gce, "delayTime", 10);
                  disposal = disposalCode(gce.getAttribute("disposalMethod"));
               }
            } catch (Throwable ignored) {}

            if (disposal == 3) {
               prevSnapshot = copyImage(canvas);
            }

            g.drawImage(frame, left, top, null);

            // Снимок текущего кадра (полный композит), даунскейл -> PNG.
            BufferedImage out;
            if (outW == canvasW && outH == canvasH) {
               out = copyImage(canvas);
            } else {
               out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB);
               Graphics2D sg = out.createGraphics();
               sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
               sg.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
               sg.drawImage(canvas, 0, 0, outW, outH, null);
               sg.dispose();
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
            ImageIO.write(out, "png", baos);
            pngs.add(baos.toByteArray());

            int d = delayCenti <= 0 ? 100 : delayCenti * 10;
            if (d < 20) d = 100;
            delays.add(d);

            if (disposal == 2) {
               Composite oc = g.getComposite();
               g.setComposite(AlphaComposite.Clear);
               g.fillRect(left, top, frame.getWidth(), frame.getHeight());
               g.setComposite(oc);
            } else if (disposal == 3 && prevSnapshot != null) {
               Composite oc = g.getComposite();
               g.setComposite(AlphaComposite.Src);
               g.drawImage(prevSnapshot, 0, 0, null);
               g.setComposite(oc);
            }
         }
         g.dispose();
         reader.dispose();
      }

      this.frameW = outW;
      this.frameH = outH;
      this.frameCount = pngs.size();
      this.framePng = pngs.toArray(new byte[0][]);
      this.delaysMs = new int[delays.size()];
      int total = 0;
      for (int i = 0; i < delays.size(); i++) {
         this.delaysMs[i] = delays.get(i);
         total += delays.get(i);
      }
      this.totalDurationMs = Math.max(1, total);
      this.decoded = true; // публикует все поля выше
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static IIOMetadataNode child(IIOMetadataNode root, String name) {
      if (root == null) return null;
      for (int i = 0; i < root.getLength(); i++) {
         Node node = root.item(i);
         if (node instanceof IIOMetadataNode && node.getNodeName().equalsIgnoreCase(name)) {
            return (IIOMetadataNode) node;
         }
      }
      return null;
   }

   private static int intAttr(IIOMetadataNode node, String attr, int def) {
      try {
         String v = node.getAttribute(attr);
         if (v == null || v.isEmpty()) return def;
         return Integer.parseInt(v.trim());
      } catch (Exception e) {
         return def;
      }
   }

   private static int disposalCode(String dm) {
      if (dm == null) return 0;
      switch (dm) {
         case "doNotDispose": return 1;
         case "restoreToBackgroundColor": return 2;
         case "restoreToPrevious": return 3;
         default: return 0;
      }
   }

   private static BufferedImage copyImage(BufferedImage src) {
      BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = c.createGraphics();
      g.drawImage(src, 0, 0, null);
      g.dispose();
      return c;
   }
}
