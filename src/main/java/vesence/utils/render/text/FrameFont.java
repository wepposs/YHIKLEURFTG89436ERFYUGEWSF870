package vesence.utils.render.text;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.ResourceLocation;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * FrameFont — гладкий системный шрифт (Arial) для символов рамки скорборда
 * (AABB Drawing U+2500..257F и bar-dingbat ❚ U+275A), которых НЕТ в MSDF-атласе
 * vesence.
 *
 * <p>Порт идеи RelevantPremiumpp4 ({@code Fonts.getSystemFont("Arial")} +
 * {@code MCFontRenderer/CFont}, TTF-фолбэк для рамки). Оригинал берёт РОВНО тот
 * же системный Arial (в нём есть блок AABB Drawing на всех платформах) и грузит
 * атлас через {@link NativeImageBackedTexture} — безопасный путь MC.
 *
 * <p>Каждый нужный глиф рендерится AWT-шрифтом Arial (со сглаживанием) в
 * маленькую RGBA-текстуру (белый глиф на прозрачном), один раз, с кэшем, и
 * заливается в GL через {@link NativeImageBackedTexture} (как
 * {@link vesence.utils.render.ttf.TtfFont} и как {@code CFont} оригинала).
 *
 * <p>ВАЖНО: раньше здесь использовался сырой {@code glTexImage2D}, который
 * рассинхронизировал состояние GL 1.21.11 (Blaze3D/GlStateManager), из-за чего
 * рамка рисовалась «красными каракулями» и ронял драйвер NVIDIA
 * ({@code EXCEPTION_ACCESS_VIOLATION в nvoglv64.dll}). Теперь загрузка идёт
 * штатным путём MC, а строки текстуры — сверху вниз (как у {@code TtfFont}),
 * поэтому глиф рисуется через {@code Renderer2D.drawImage(..., flip=false)}.
 */
@Environment(EnvType.CLIENT)
public final class FrameFont {

    /** Кегль AWT-рендера глифа (px). Крупный → чёткое масштабирование вниз. */
    private static final int BASE_PX = 64;

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    /** Данные одного глифа: GL-текстура + метрики ячейки. */
    public static final class Glyph {
        public final int texId;
        public final int imgW;      // ширина текстуры (= advance, px)
        public final int imgH;      // высота текстуры (= cellH, px)
        public final int advance;   // шаг по X (px)

        Glyph(int texId, int imgW, int imgH, int advance) {
            this.texId = texId;
            this.imgW = imgW;
            this.imgH = imgH;
            this.advance = advance;
        }
    }

    private static Font font;
    private static FontMetrics metrics;
    private static int ascent = 0;
    private static int cellH = 0;   // высота ячейки (ascent + descent), px
    private static boolean initFailed = false;

    private static final Map<Character, Glyph> CACHE = new HashMap<>();
    private static final Map<Character, Boolean> MISSING = new HashMap<>();

    private FrameFont() {}

    private static void ensureFont() {
        if (font != null || initFailed) return;
        try {
            font = new Font("Arial", Font.PLAIN, BASE_PX);
            BufferedImage probe = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = probe.createGraphics();
            g.setFont(font);
            metrics = g.getFontMetrics();
            ascent = metrics.getAscent();
            cellH = metrics.getAscent() + metrics.getDescent();
            if (cellH <= 0) cellH = BASE_PX;
            g.dispose();
        } catch (Throwable t) {
            initFailed = true;
            font = null;
        }
    }

    /** Высота ячейки глифа (px) — база для масштабирования под шаг строки. */
    public static int cellHeight() {
        ensureFont();
        return cellH > 0 ? cellH : BASE_PX;
    }

    /** Шаг символа (px) без создания текстуры — для measure. 0 если нет глифа. */
    public static int advanceOf(char c) {
        ensureFont();
        if (font == null || metrics == null || !font.canDisplay(c)) return 0;
        return Math.max(1, metrics.charWidth(c));
    }

    /** Глиф символа (создаёт+кэширует текстуру). null если шрифт/символ недоступен. */
    public static Glyph getGlyph(char c) {
        ensureFont();
        if (font == null) return null;
        Glyph g = CACHE.get(c);
        if (g != null) return g;
        if (Boolean.TRUE.equals(MISSING.get(c))) return null;
        if (!font.canDisplay(c)) {
            MISSING.put(c, Boolean.TRUE);
            return null;
        }
        try {
            int advance = Math.max(1, metrics.charWidth(c));
            int w = advance;
            int h = cellH;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g2.setFont(font);
            g2.setColor(Color.WHITE);
            // Рисуем от левого края ячейки (x=0), базовая линия y=ascent — так
            // соседние символы рамки стыкуются (═ по горизонтали, ║ по вертикали).
            g2.drawString(String.valueOf(c), 0, ascent);
            g2.dispose();

            int texId = uploadGlyph(img, w, h);
            if (texId <= 0) {
                MISSING.put(c, Boolean.TRUE);
                return null;
            }
            g = new Glyph(texId, w, h, advance);
            CACHE.put(c, g);
            return g;
        } catch (Throwable t) {
            MISSING.put(c, Boolean.TRUE);
            return null;
        }
    }

    /**
     * Заливает RGBA-глиф из {@link BufferedImage} в GL-текстуру ШТАТНЫМ путём MC
     * ({@link NativeImageBackedTexture}) — тем же, что использует
     * {@link vesence.utils.render.ttf.TtfFont} и {@code CFont} оригинала. Строки
     * копируются сверху вниз (как в MC/TtfFont), поэтому глиф рисуется без
     * переворота ({@code Renderer2D.drawImage(..., flipVertically=false)}).
     * Никакого сырого {@code glTexImage2D} — он рассинхронизировал GL-состояние
     * и ронял драйвер.
     */
    private static int uploadGlyph(BufferedImage img, int w, int h) {
        NativeImage nativeImage = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                nativeImage.setColorArgb(x, y, img.getRGB(x, y));
            }
        }

        String name = "frameglyph_" + COUNTER.incrementAndGet();
        final String nameFinal = name;
        Supplier<String> nameSupplier = () -> nameFinal;
        NativeImageBackedTexture tex = new NativeImageBackedTexture(nameSupplier, nativeImage);
        tex.upload();
        ResourceLocation id = ResourceLocation.of("vesence", "frameglyph/" + name);
        Minecraft.getInstance().getTextureManager().registerTexture(id, tex);

        if (tex.getGlTexture() instanceof GlTexture glTex) {
            return glTex.getGlId();
        }
        return 0;
    }
}
