package vesence.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import vesence.mixin.AbstractTextureAccessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Видео-фон главного меню: заранее нарезанные кадры JPEG в ресурсах
 * ({@code assets/vesence/videos/menu/f%04d.jpg} + {@code meta.txt}).
 *
 * <p>H.264 в рантайме не трогаем. Кадр JPEG на 1080p декодируется ~25-35 мс —
 * один поток НЕ успевает за 30 fps (33 мс/кадр) и плеер «пропускал кадры».
 * Поэтому декодируем НЕСКОЛЬКИМИ потоками в буфер переупорядочивания: кадры
 * готовятся «на опережение» с запасом, render-поток всегда берёт нужный кадр
 * без ожидания. Воспроизведение — по системным часам, цикл бесшовный.
 *
 * <p>Отрисовка: кадр как обычная текстура MC (TextureManager, Blaze3D) с
 * LINEAR-фильтром — на весь экран без пикселей.
 */
@Environment(EnvType.CLIENT)
public final class VideoBackground {

    /** Потоки-декодеры (параллелят JPEG-декод, чтобы успевать за fps на 1080p+). */
    private static final int DECODER_THREADS = 3;
    /** На сколько кадров вперёд декодируем (буфер плавности; ограничивает память). */
    private static final int BUFFER_AHEAD = 6;
    private static final double DEFAULT_FPS = 30.0;

    private static final Map<String, VideoBackground> CACHE = new ConcurrentHashMap<>();

    private final String dir;
    private final String key;
    private final Identifier texId;

    private volatile boolean started = false;
    private volatile boolean ready = false;
    private volatile boolean failed = false;

    private volatile int count;
    private volatile int w, h;
    private long frameDurationNanos;

    // Буфер переупорядочивания: absIndex -> декодированный кадр. absIndex растёт
    // монотонно; файл кадра = absIndex % count (зацикливание).
    private final Map<Integer, NativeImage> buffer = new ConcurrentHashMap<>();
    private final Set<Integer> failedFrames = ConcurrentHashMap.newKeySet();
    private final AtomicInteger decodeCursor = new AtomicInteger(0);
    private volatile int playHead = 0;

    // только render-поток
    private boolean shownAny = false;
    private long startNanos = -1L;
    private long nextDueNanos = 0L;
    private long lastProgressNanos = 0L;

    private static GpuSampler linearSampler;

    private VideoBackground(String dir) {
        this.dir = dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
        this.key = this.dir.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
        this.texId = Identifier.fromNamespaceAndPath("vesence", "menu_video/" + key + ".png");
    }

    public static VideoBackground of(String framesDir) {
        VideoBackground v = CACHE.get(framesDir);
        if (v == null) {
            v = new VideoBackground(framesDir);
            VideoBackground prev = CACHE.putIfAbsent(framesDir, v);
            if (prev != null) return prev;
        }
        return v;
    }

    public int getWidth()  { return w; }
    public int getHeight() { return h; }

    /** render-поток: id текущего кадра по системным часам или null (пока не готово). */
    public Identifier currentFrameId() {
        if (failed) return null;
        if (!started) start();
        if (!ready) return null;

        long now = System.nanoTime();
        if (startNanos < 0L) { startNanos = now; nextDueNanos = now; lastProgressNanos = now; }

        if (!shownAny || now >= nextDueNanos) {
            // Пропускаем кадры, которые декодер не смог собрать (редко).
            while (failedFrames.remove(playHead)) { buffer.remove(playHead); playHead++; }

            NativeImage img = buffer.remove(playHead);
            if (img != null) {
                uploadFrame(playHead, img);
                playHead++;
                lastProgressNanos = now;
                nextDueNanos += frameDurationNanos;
                if (now - nextDueNanos > frameDurationNanos * 4L) nextDueNanos = now + frameDurationNanos;
            } else if (now - lastProgressNanos > 1_000_000_000L) {
                // Кадр слишком долго не готов — не залипаем, шагаем дальше.
                playHead++;
                lastProgressNanos = now;
                nextDueNanos = now + frameDurationNanos;
            }
        }
        return shownAny ? texId : null;
    }

    private void uploadFrame(int absIdx, NativeImage img) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.getTextureManager() == null) { img.close(); return; }
            if (linearSampler == null) {
                linearSampler = RenderSystem.getSamplerCache().getSampler(
                        AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                        FilterMode.LINEAR, FilterMode.LINEAR, false);
            }
            final int fi = absIdx;
            DynamicTexture tex =
                    new DynamicTexture(() -> "vesence_menu_video_" + key + "_" + fi, img);
            try {
                ((AbstractTextureAccessor) (Object) tex).vesence$setSampler(linearSampler);
            } catch (Throwable ignored) {}
            // Перерегистрация того же id закрывает предыдущую текстуру (и её NativeImage).
            mc.getTextureManager().register(texId, tex);
            try { tex.getTextureView(); } catch (Throwable ignored) {}
            shownAny = true;
        } catch (Throwable t) {
            try { img.close(); } catch (Throwable ignored) {}
        }
    }

    private synchronized void start() {
        if (started) return;
        // meta.txt: "count fps w h"
        try (InputStream is = res(dir + "/meta.txt")) {
            if (is == null) { failed = true; return; }
            String[] p = new String(is.readAllBytes()).trim().split("\\s+");
            int cnt = Integer.parseInt(p[0]);
            double fps = p.length > 1 ? Double.parseDouble(p[1]) : DEFAULT_FPS;
            if (cnt <= 0) { failed = true; return; }
            if (fps <= 0) fps = DEFAULT_FPS;
            this.count = cnt;
            this.frameDurationNanos = Math.max(1L, (long) (1_000_000_000.0 / fps));
            if (p.length > 3) { this.w = Integer.parseInt(p[2]); this.h = Integer.parseInt(p[3]); }
        } catch (Throwable e) {
            System.err.println("[weppos] VideoBackground meta load failed: " + e);
            failed = true;
            return;
        }
        started = true;
        ready = true;
        for (int i = 0; i < DECODER_THREADS; i++) {
            Thread t = new Thread(this::decodeLoop, "vesence-VideoBg-" + key + "-" + i);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        }
    }

    private void decodeLoop() {
        try {
            while (started) {
                int abs = decodeCursor.getAndIncrement();
                // Не убегаем слишком далеко вперёд от того, что показывается.
                while (started && abs - playHead >= BUFFER_AHEAD) {
                    Thread.sleep(2);
                }
                if (!started) return;
                NativeImage img = decodeFrame(abs % count);
                if (img != null) {
                    if (w == 0) { w = img.getWidth(); h = img.getHeight(); }
                    buffer.put(abs, img);
                } else {
                    failedFrames.add(abs);
                }
            }
        } catch (InterruptedException ignored) {
        } catch (Throwable e) {
            System.err.println("[weppos] VideoBackground decode thread error: " + e);
        }
    }

    private NativeImage decodeFrame(int fileIdx) {
        String path = dir + "/" + String.format(Locale.ROOT, "f%04d.jpg", fileIdx);
        byte[] bytes;
        try (InputStream is = res(path)) {
            if (is == null) return null;
            bytes = is.readAllBytes();
        } catch (Throwable t) {
            return null;
        }
        // Быстрый путь: STB (поддерживает JPEG), без попиксельного Java-цикла.
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (Throwable ignored) {}
        // Фолбэк: ImageIO.
        try {
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bi == null) return null;
            int iw = bi.getWidth(), ih = bi.getHeight();
            int[] argb = bi.getRGB(0, 0, iw, ih, null, 0, iw);
            NativeImage img = new NativeImage(iw, ih, false);
            for (int y = 0; y < ih; y++) {
                int rowo = y * iw;
                for (int x = 0; x < iw; x++) {
                    img.setPixel(x, y, 0xFF000000 | (argb[rowo + x] & 0x00FFFFFF));
                }
            }
            return img;
        } catch (Throwable t) {
            return null;
        }
    }

    private static InputStream res(String path) {
        InputStream is = VideoBackground.class.getClassLoader().getResourceAsStream(path);
        if (is == null) is = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        return is;
    }
}
