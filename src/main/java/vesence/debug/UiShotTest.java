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
            || System.getenv("VESENCE_UITEST_WORLD") != null
            || System.getenv("VESENCE_UITEST_GUI") != null
            || System.getenv("VESENCE_UITEST_ESC") != null
            || System.getenv("VESENCE_UITEST_F3") != null
            || System.getenv("VESENCE_UITEST_INV") != null
            || System.getenv("VESENCE_UITEST_SCROLL") != null;
    /** В world-режиме ждём, пока загрузится мир (quickPlay), и только потом снимаем кадр. */
    private static final boolean WORLD_MODE = System.getenv("VESENCE_UITEST_WORLD") != null
            || System.getenv("VESENCE_UITEST_GUI") != null
            || System.getenv("VESENCE_UITEST_ESC") != null
            || System.getenv("VESENCE_UITEST_F3") != null
            || System.getenv("VESENCE_UITEST_INV") != null
            || System.getenv("VESENCE_UITEST_SCROLL") != null;
    /** Открыть ClickGui перед снятием кадров — проверка, что кастомный интерфейс рисуется. */
    private static final boolean GUI_MODE = System.getenv("VESENCE_UITEST_GUI") != null
            || System.getenv("VESENCE_UITEST_SCROLL") != null;
    /** Открыть меню паузы: HUD должен оказаться ПОД ним, а не поверх. */
    private static final boolean ESC_MODE = System.getenv("VESENCE_UITEST_ESC") != null;
    /** Включить отладку F3: HUD не должен её перекрывать. */
    private static final boolean F3_MODE = System.getenv("VESENCE_UITEST_F3") != null;
    /** Открыть инвентарь: проверка HandledScreen-хуков (кнопки сундука). */
    private static final boolean INV_MODE = System.getenv("VESENCE_UITEST_INV") != null;
    /** Печатать состояние экрана — чтобы отличать «застрял» от «ждёт нажатия». */
    private static final boolean DIAG = System.getenv("VESENCE_UITEST_DIAG") != null;
    /** Прокрутка ClickGui колесом: проверяет, что scroll доходит до списка модулей. */
    private static final boolean SCROLL_MODE = System.getenv("VESENCE_UITEST_SCROLL") != null;
    private static final int WARMUP_TICKS = 200;
    private static final int SHOTS = 2;
    private static final int SHOT_INTERVAL = 100;

    private static int ticks = 0;
    private static int shots = 0;
    private static boolean finished = false;
    private static boolean guiOpened = false;
    private static boolean backupSkipped = false;
    private static int autoConfirms = 0;

    private UiShotTest() {}

    public static void init() {
        if (!ACTIVE) return;
        ClientTickEvents.END_CLIENT_TICK.register(UiShotTest::tick);
        System.out.println("[uitest] harness active");
    }

    private static void tick(Minecraft mc) {
        if (finished) return;

        // Диагностика: без неё «клиент застрял на загрузке» не отличить от «клиент
        // стоит на титульном экране и ждёт действия».
        if (DIAG && ticks % 40 == 0) {
            System.out.println("[uitest] diag ticks=" + ticks
                    + " screen=" + (mc.gui.screen() == null ? "null" : mc.gui.screen().getClass().getSimpleName())
                    + " overlay=" + mc.gui.overlay()
                    + " level=" + (mc.level == null ? "null" : "present")
                    + " player=" + (mc.player == null ? "null" : "present"));
        }

        // Экран онбординга доступности встаёт ПЕРЕД титульным и перед quickPlay —
        // в автоматическом прогоне его никто не закроет, и клиент навсегда остаётся
        // на нём. Для теста выключаем флаг и снимаем экран, иначе прогон не доходит
        // до мира (и выглядит как «quickPlay сломался»).
        if (mc.options != null && mc.options.onboardAccessibility) {
            mc.options.onboardAccessibility = false;
        }
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen onboarding) {
            // onClose() прогоняет цепочку next -> quickPlay, поэтому не подменяем экран,
            // а именно закрываем онбординг.
            onboarding.onClose();
            return;
        }

        // Мир skytest создан в 1.21.11, поэтому 26.3 при открытии предлагает сделать
        // резервную копию. В автотесте нажимать некому — вызываем колбэк «продолжить
        // без копии» напрямую. Поле protected, но это тестовый код, а не прод-логика.
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.BackupConfirmScreen backup) {
            if (!backupSkipped) {
                backupSkipped = true;
                try {
                    var field = net.minecraft.client.gui.screens.BackupConfirmScreen.class
                            .getDeclaredField("onProceed");
                    field.setAccessible(true);
                    var listener = (net.minecraft.client.gui.screens.BackupConfirmScreen.Listener) field.get(backup);
                    listener.proceed(true, false);
                    System.out.println("[uitest] backup confirm skipped (proceed without backup)");
                } catch (Throwable t) {
                    System.out.println("[uitest] backup confirm skip failed: " + t);
                }
            }
            return;
        }

        // Дальше 26.3 может показать ещё одно подтверждение (несовпадение версии мира
        // и т.п.) — в автотесте нажимать некому, поэтому подтверждаем автоматически.
        // Ограничиваем число срабатываний, чтобы не зациклиться на реально сломанном мире.
        if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.ConfirmScreen confirm
                && mc.gui.screen() != null && autoConfirms < 4) {
            autoConfirms++;
            try {
                var field = net.minecraft.client.gui.screens.ConfirmScreen.class.getDeclaredField("callback");
                field.setAccessible(true);
                ((it.unimi.dsi.fastutil.booleans.BooleanConsumer) field.get(confirm)).accept(true);
                System.out.println("[uitest] auto-confirmed screen #" + autoConfirms);
            } catch (Throwable t) {
                System.out.println("[uitest] auto-confirm failed: " + t);
            }
            return;
        }

        if (WORLD_MODE && (mc.level == null || mc.player == null)) return;
        ticks++;

        // Открываем экран за половину прогрева, чтобы к первому кадру он уже был отрисован.
        if (!guiOpened && ticks >= WARMUP_TICKS / 2) {
            guiOpened = true;
            if (GUI_MODE) {
                try {
                    mc.gui.setScreen(new vesence.ui.clickgui.GuiClient());
                    System.out.println("[uitest] ClickGui opened: " + mc.gui.screen());
                } catch (Throwable t) {
                    System.out.println("[uitest] ClickGui open failed: " + t);
                }
            } else if (ESC_MODE) {
                try {
                    mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(false));
                    System.out.println("[uitest] PauseScreen opened: " + mc.gui.screen());
                } catch (Throwable t) {
                    System.out.println("[uitest] PauseScreen open failed: " + t);
                }
            } else if (INV_MODE) {
                try {
                    mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    System.out.println("[uitest] InventoryScreen opened: " + mc.gui.screen());
                } catch (Throwable t) {
                    System.out.println("[uitest] InventoryScreen open failed: " + t);
                }
            } else if (F3_MODE) {
                try {
                    mc.debugEntries.setOverlayVisible(true);
                    System.out.println("[uitest] debug overlay visible=" + mc.debugEntries.isOverlayVisible());
                } catch (Throwable t) {
                    System.out.println("[uitest] debug toggle failed: " + t);
                }
            }
        }

        if (ticks < WARMUP_TICKS) return;

        // Прокрутка ClickGui: гоняем колесо над правой панелью и смотрим,
        // сдвинулся ли список. Проверять глазами бессмысленно — визуально
        // «не листается» и «листается, но не туда» выглядят одинаково.
        if (SCROLL_MODE && ticks == WARMUP_TICKS) {
            runScrollTest(mc);
        }

        if ((ticks - WARMUP_TICKS) % SHOT_INTERVAL != 0) return;

        String name = (GUI_MODE ? "gui-" : ESC_MODE ? "esc-" : F3_MODE ? "f3-" : INV_MODE ? "inv-" : WORLD_MODE ? "world-" : "menu-") + shots + ".png";
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

    /**
     * Проверка прокрутки ClickGui.
     *
     * <p>Берём точку в правой панели (список модулей), переводим её обратно в
     * экранные координаты и крутим колесо через тот же {@code mouseScrolled},
     * который вызывает ваниль. Смотрим, сдвинулся ли {@code moduleScroll}.
     */
    private static void runScrollTest(Minecraft mc) {
        try {
            var screen = mc.gui.screen();
            System.out.println("[uitest] scroll screen=" + screen);

            float cx = vesence.ui.clickgui.compact.CompactGuiScreen.x
                    + vesence.ui.clickgui.compact.CompactGuiScreen.GUI_W / 2f;
            float cy = vesence.ui.clickgui.compact.CompactGuiScreen.y
                    + vesence.ui.clickgui.compact.CompactGuiScreen.GUI_H / 2f;

            // Точка в правой панели, в виртуальных координатах compact-меню.
            float vx = vesence.ui.clickgui.compact.CompactGuiScreen.x
                    + vesence.ui.clickgui.compact.CompactGuiScreen.LEFT_PANEL_W
                    + vesence.ui.clickgui.compact.CompactGuiScreen.LEFT_PANEL_GAP * 2 + 40f;
            float vy = cy;

            double s = Math.max(0.0001, vesence.ui.clickgui.compact.CompactGuiScreen.guiScale);
            double rawX = cx + (vx - cx) * s;
            double rawY = cy + (vy - cy) * s;

            var scroll = vesence.ui.clickgui.compact.CompactGuiScreen.moduleScroll;
            float before = scroll.getScroll();

            boolean handled = false;
            if (screen instanceof vesence.ui.clickgui.GuiClient client) {
                handled = client.mouseScrolled(rawX, rawY, 0.0, -3.0);
            }

            System.out.println("[uitest] scroll at=" + (int) rawX + "," + (int) rawY
                    + " guiScale=" + s
                    + " handled=" + handled
                    + " before=" + String.format("%.2f", before)
                    + " targetAfter=" + String.format("%.2f", scroll.getScroll()));

            // Доводим анимацию до конца, чтобы увидеть итоговое положение.
            for (int i = 0; i < 40; i++) {
                scroll.update();
            }
            float after = scroll.getScroll();
            System.out.println("[uitest] scroll RESULT before=" + String.format("%.2f", before)
                    + " after=" + String.format("%.2f", after)
                    + " moved=" + (Math.abs(after - before) > 0.5f));
        } catch (Throwable t) {
            System.out.println("[uitest] scroll test failed: " + t);
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
