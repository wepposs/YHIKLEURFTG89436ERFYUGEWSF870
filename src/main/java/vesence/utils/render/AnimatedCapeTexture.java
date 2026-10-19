package vesence.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.ResourceLocation;
import vesence.mixin.AbstractTextureAccessor;

import java.io.InputStream;
import java.util.Arrays;

/**
 * Анимированный плащ (sprite-sheet).
 *
 * Формат: кадры сложены вертикально в один PNG.
 * Каждый кадр билинейно масштабируется и вписывается в UV-зону плаща.
 * Файл: assets/vesence/textures/cape/cape_animated.png
 *
 * <h2>Почему асинхронно (фикс фриза)</h2>
 * Раньше {@code load()} выполнялся ЦЕЛИКОМ на render-потоке при первом показе
 * плаща: декод PNG + билинейный ресемпл 150 кадров (~22 млн выборок, ~490 МБ
 * нативных изображений) + загрузка 150 GL-текстур. Это давало разовый фриз
 * ~1 секунду прямо во время отрисовки игроков (см. диагностику FreezeWatchdog:
 * стек AnimatedCapeTexture.load ← getCurrentFrame ← Cape.getCapeTexture ←
 * WorldRenderer.fillEntityRenderStates).
 *
 * Теперь тяжёлый ЦП-этап (декод + ресемпл всех кадров в {@link NativeImage})
 * вынесен в фоновый поток. Пока кадры не готовы — {@link #getCurrentFrame()}
 * возвращает {@code null}, и модуль {@link vesence.module.impl.visuals.Cape}
 * показывает статичный плащ. Загрузка кадра в GPU ({@link NativeImageBackedTexture})
 * обязана идти на render-потоке (нужен GL-контекст), поэтому делается ЛЕНИВО —
 * по одному кадру в момент его первого показа (1 загрузка на ~45 мс,
 * ~1-3 мс каждая = незаметно). Итог: фриза нет, визуал прежний.
 */
public final class AnimatedCapeTexture {

    private static final int FRAME_COUNT    = 150;
    private static final int FRAME_DELAY_MS = 45;  // ~22 fps — плавно
    private static final String SPRITE_PATH = "assets/vesence/textures/cape/cape_animated.png";

    // Размер итоговой текстуры плаща (UV-развёртка)
    private static final int CAPE_W = 1280;
    private static final int CAPE_H = 640;

    // Зона вставки кадра внутри UV-развёртки
    private static final int DST_X = 0;
    private static final int DST_Y = 0;
    private static final int DST_W = 438;
    private static final int DST_H = 340;

    private static final ResourceLocation[] frameIds = new ResourceLocation[FRAME_COUNT];

    /** Ресемпленные на ЦП кадры (готовит фон-поток). Публикуется через volatile. */
    private static volatile NativeImage[] cpuFrames;
    /** Какие кадры уже загружены в GPU (лениво, на render-потоке). */
    private static final boolean[] uploaded = new boolean[FRAME_COUNT];

    private static volatile boolean framesReady   = false;
    private static volatile boolean loadingStarted = false;
    private static volatile boolean failed         = false;

    private static int  currentFrame  = 0;
    private static long lastFrameTime = 0;

    /** LINEAR-сэмплер резолвится один раз на render-потоке при первой загрузке. */
    private static GpuSampler linearSampler;

    private AnimatedCapeTexture() {}

    /**
     * Возвращает ResourceLocation текущего кадра анимации.
     * {@code null} если анимация ещё не готова (идёт фоновая подготовка) или
     * недоступна (файл не найден / ошибка) — тогда показывается статичный плащ.
     */
    public static ResourceLocation getCurrentFrame() {
        if (failed) {
            return null;
        }
        if (!framesReady) {
            // Кадры ещё готовятся в фоне (или подготовка не начата) — не блокируем
            // render-поток, отдаём null → модуль покажет статичный плащ.
            if (!loadingStarted) {
                startBackgroundLoad();
            }
            return null;
        }

        long now = System.currentTimeMillis();
        if (now - lastFrameTime >= FRAME_DELAY_MS) {
            currentFrame = (currentFrame + 1) % FRAME_COUNT;
            lastFrameTime = now;
        }
        // Лениво грузим GL-текстуру этого кадра (на render-потоке), если ещё нет.
        ensureUploaded(currentFrame);
        return frameIds[currentFrame];
    }

    /**
     * Запускает фоновую подготовку кадров: декод PNG + билинейный ресемпл всех
     * кадров в {@link NativeImage}. НИКАКИХ GL-вызовов здесь (нет контекста в
     * фон-потоке) — только ЦП-работа. По готовности публикует {@link #cpuFrames}
     * и выставляет {@link #framesReady}.
     */
    private static void startBackgroundLoad() {
        loadingStarted = true;
        Thread worker = new Thread(() -> {
            try (InputStream stream = AnimatedCapeTexture.class.getClassLoader()
                    .getResourceAsStream(SPRITE_PATH)) {
                if (stream == null) {
                    failed = true;
                    return;
                }

                NativeImage spriteSheet = NativeImage.read(stream);
                int srcFrameH = spriteSheet.getHeight() / FRAME_COUNT;
                int srcFrameW = spriteSheet.getWidth();

                NativeImage[] frames = new NativeImage[FRAME_COUNT];
                for (int i = 0; i < FRAME_COUNT; i++) {
                    int frameOffsetY = i * srcFrameH;
                    NativeImage capeFrame = new NativeImage(CAPE_W, CAPE_H, true);

                    // Билинейное масштабирование кадра в зону плаща
                    for (int dx = 0; dx < DST_W; dx++) {
                        for (int dy = 0; dy < DST_H; dy++) {
                            float srcX = ((float) dx + 0.5f) / DST_W * srcFrameW - 0.5f;
                            float srcY = ((float) dy + 0.5f) / DST_H * srcFrameH - 0.5f;
                            int color = sampleBilinear(spriteSheet, srcX, srcY, frameOffsetY, srcFrameW, srcFrameH);
                            capeFrame.setColorArgb(DST_X + dx, DST_Y + dy, color);
                        }
                    }

                    frames[i] = capeFrame;
                    frameIds[i] = ResourceLocation.of("vesence", "cape/anim_" + i);
                }

                spriteSheet.close();
                cpuFrames = frames;   // volatile-публикация (happens-before для render-потока)
                framesReady = true;
            } catch (Throwable t) {
                failed = true;
            }
        }, "vesence-CapeLoader");
        worker.setDaemon(true);
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    /**
     * Лениво загружает GL-текстуру кадра {@code i}. Вызывается на render-потоке
     * (из {@link #getCurrentFrame()} во время отрисовки), поэтому GL-контекст
     * доступен. Освобождает ЦП-копию кадра после передачи владения текстуре.
     */
    private static void ensureUploaded(int i) {
        if (uploaded[i]) {
            return;
        }
        NativeImage[] frames = cpuFrames;
        if (frames == null || frames[i] == null) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (linearSampler == null) {
                linearSampler = RenderSystem.getSamplerCache().get(
                        AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                        FilterMode.LINEAR, FilterMode.LINEAR, false);
            }
            final int fi = i;
            NativeImageBackedTexture tex =
                    new NativeImageBackedTexture(() -> "vesence_cape_anim_" + fi, frames[i]);
            try {
                ((AbstractTextureAccessor) (Object) tex).vesence$setSampler(linearSampler);
            } catch (Throwable ignored) {}
            mc.getTextureManager().registerTexture(frameIds[i], tex);
            uploaded[i] = true;
            // Текстура теперь владеет NativeImage — снимаем нашу ссылку, чтобы
            // не держать ~3 МБ на кадр в куче дольше нужного.
            frames[i] = null;
        } catch (Throwable t) {
            // Оставляем uploaded[i]=false — попробуем на следующем показе кадра.
        }
    }

    /**
     * Билинейная выборка ARGB-цвета из кадра спрайт-листа.
     * frameOffsetY — смещение начала кадра по Y в общем спрайте.
     */
    private static int sampleBilinear(NativeImage img, float fx, float fy,
                                      int frameOffsetY, int frameW, int frameH) {
        int x0 = (int) Math.floor(fx);
        int y0 = (int) Math.floor(fy);
        float wx = fx - x0;
        float wy = fy - y0;

        int x0c = clamp(x0, 0, frameW - 1);
        int x1c = clamp(x0 + 1, 0, frameW - 1);
        int y0c = clamp(y0, 0, frameH - 1);
        int y1c = clamp(y0 + 1, 0, frameH - 1);

        int c00 = img.getColorArgb(x0c, frameOffsetY + y0c);
        int c10 = img.getColorArgb(x1c, frameOffsetY + y0c);
        int c01 = img.getColorArgb(x0c, frameOffsetY + y1c);
        int c11 = img.getColorArgb(x1c, frameOffsetY + y1c);

        return lerpArgb(lerpArgb(c00, c10, wx), lerpArgb(c01, c11, wx), wy);
    }

    /** Линейная интерполяция двух ARGB-цветов по всем каналам. */
    private static int lerpArgb(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** Сброс состояния (например, при смене мира или отключении модуля). */
    public static void reset() {
        // Освобождаем ещё не загруженные в GPU ЦП-кадры, чтобы не текла нативная память.
        NativeImage[] frames = cpuFrames;
        if (frames != null) {
            for (NativeImage img : frames) {
                if (img != null) {
                    try {
                        img.close();
                    } catch (Throwable ignored) {}
                }
            }
        }
        cpuFrames = null;
        framesReady = false;
        loadingStarted = false;
        failed = false;
        currentFrame  = 0;
        lastFrameTime = 0;
        Arrays.fill(uploaded, false);
    }
}
