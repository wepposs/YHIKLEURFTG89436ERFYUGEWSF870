package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import com.mojang.blaze3d.platform.NativeImage;

import java.io.File;

/**
 * Диагностика «чёрного экрана» порта 26.3: активируется ТОЛЬКО при
 * {@code VESENCE_UITEST=1}. Ждёт {@link #WARMUP_TICKS} тиков (главное меню уже
 * должно быть отрисовано), затем снимает main render target в
 * {@code run/uitest-shots/} и печатает статистику яркости — по ней сразу видно,
 * отрисовался ли кадр (чёрный экран даёт ~0 по среднему и максимуму).
 */
@Environment(EnvType.CLIENT)
public final class UiShotTest {
    private static final boolean ACTIVE = System.getenv("VESENCE_UITEST") != null
            || System.getenv("VESENCE_UITEST_WORLD") != null;
    /** В world-режиме ждём, пока загрузится мир (quickPlay), и только потом снимаем кадр. */
    private static final boolean WORLD_MODE = System.getenv("VESENCE_UITEST_WORLD") != null;
    private static final int WARMUP_TICKS = 200;
    private static final int SHOTS = 2;
    private static final int SHOT_INTERVAL = 100;

    private static int ticks = 0;
    private static int shots = 0;
    private static boolean finished = false;

    private UiShotTest() {}

    public static void init() {
        if (!ACTIVE) return;
        ClientTickEvents.END_CLIENT_TICK.register(UiShotTest::tick);
        System.out.println("[uitest] harness active");
    }

    private static void tick(Minecraft mc) {
        if (finished) return;
        if (WORLD_MODE && (mc.level == null || mc.player == null)) return;
        ticks++;
        if (ticks < WARMUP_TICKS) return;
        if ((ticks - WARMUP_TICKS) % SHOT_INTERVAL != 0) return;

        String name = (WORLD_MODE ? "world-" : "menu-") + shots + ".png";
        new File("uitest-shots").mkdirs();
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try {
                report(name, image);
                image.close();
            } catch (Throwable t) {
                System.out.println("[uitest] failed: " + t);
            }
        });

        shots++;
        if (shots >= SHOTS) {
            finished = true;
            System.out.println("[uitest] done, stopping");
            mc.stop();
        }
    }

    private static void report(String name, NativeImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        long sum = 0;
        int max = 0;
        int nonBlack = 0;
        long total = (long) w * h;
        for (int y = 0; y < h; y += 2) {
            for (int x = 0; x < w; x += 2) {
                int argb = image.getPixel(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                int lum = (r + g + b) / 3;
                sum += lum;
                if (lum > max) max = lum;
                if (lum > 8) nonBlack++;
            }
        }
        long sampled = (long) ((w + 1) / 2) * ((h + 1) / 2);
        double avg = sampled == 0 ? 0 : (double) sum / sampled;
        double pct = sampled == 0 ? 0 : 100.0 * nonBlack / sampled;
        System.out.printf("[uitest] %s %dx%d avg=%.2f max=%d nonBlack=%.1f%%%n",
                name, w, h, avg, max, pct);

        try {
            File out = new File("uitest-shots", name);
            image.writeToFile(out);
            System.out.println("[uitest] saved " + out.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[uitest] save failed: " + t);
        }

        ascii(name, image);
    }

    /** Текстовая «миниатюра» кадра: по ней видно композицию без просмотра PNG. */
    private static void ascii(String name, NativeImage image) {
        int cols = 60;
        int rows = 24;
        int w = image.getWidth();
        int h = image.getHeight();
        StringBuilder sb = new StringBuilder();
        sb.append("[uitest] thumbnail ").append(name).append('\n');
        for (int ry = 0; ry < rows; ry++) {
            StringBuilder line = new StringBuilder();
            for (int rx = 0; rx < cols; rx++) {
                int x0 = rx * w / cols;
                int x1 = Math.max(x0 + 1, (rx + 1) * w / cols);
                int y0 = ry * h / rows;
                int y1 = Math.max(y0 + 1, (ry + 1) * h / rows);
                long sum = 0;
                int n = 0;
                for (int y = y0; y < y1; y += 3) {
                    for (int x = x0; x < x1; x += 3) {
                        int argb = image.getPixel(x, y);
                        sum += (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3;
                        n++;
                    }
                }
                int lum = n == 0 ? 0 : (int) (sum / n);
                line.append(ramp(lum));
            }
            sb.append("  |").append(line).append("|\n");
        }
        System.out.print(sb);
    }

    private static char ramp(int lum) {
        String chars = " .:-=+*#%@";
        int idx = Math.min(chars.length() - 1, Math.max(0, lum * chars.length() / 256));
        return chars.charAt(idx);
    }
}
