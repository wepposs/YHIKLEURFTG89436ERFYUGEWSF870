package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import vesence.module.impl.visuals.Ambience;
import vesence.renderengine.render.SkyShaderRenderer;

import java.io.File;

/**
 * Временный авто-тест неба: активируется ТОЛЬКО при переменной окружения
 * VESENCE_SKYTEST=1. Заходит в мир (через --quickPlaySingleplayer), включает
 * модуль Custom World с шейдером неба, фотографирует все режимы по очереди
 * и выключает клиент. Скриншоты — в run/skytest-shots/.
 */
@Environment(EnvType.CLIENT)
public final class SkyShotTest {
    private static final boolean ACTIVE = System.getenv("VESENCE_SKYTEST") != null;
    private static final int WARMUP_TICKS = 140;
    private static final int TICKS_PER_MODE = 45;

    private static int ticks = 0;
    private static int modeIdx = -1;
    private static boolean moduleOn = false;
    private static boolean finished = false;

    private SkyShotTest() {}

    public static void init() {
        if (!ACTIVE) return;
        ClientTickEvents.END_CLIENT_TICK.register(SkyShotTest::tick);
        System.out.println("[skytest] harness active");
    }

    private static void tick(MinecraftClient mc) {
        if (finished || mc.player == null || mc.world == null) return;
        ticks++;

        // Фиксируем ракурс: как у оффлайн-рендера, только взгляд чуть вверх.
        mc.player.setYaw(35.0F);
        mc.player.setPitch(-12.0F);
        mc.options.hudHidden = true;

        if (ticks < WARMUP_TICKS) return;

        if (!moduleOn) {
            Ambience amb = Ambience.getInstance();
            if (amb == null) return;
            amb.enable = true;
            Ambience.skyShader.set(true);
            Ambience.hideSunMoon.set(true);
            moduleOn = true;
            new File("skytest-shots").mkdirs();
            System.out.println("[skytest] module enabled, starting shots");
        }

        int rel = ticks - WARMUP_TICKS;
        int target = rel / TICKS_PER_MODE;
        if (target >= SkyShaderRenderer.MODE_NAMES.length) {
            finished = true;
            System.out.println("[skytest] all modes captured, stopping");
            mc.scheduleStop();
            return;
        }

        if (target != modeIdx) {
            modeIdx = target;
            Ambience.skyShaderMode.currentMode = SkyShaderRenderer.MODE_NAMES[modeIdx];
            System.out.println("[skytest] mode -> " + SkyShaderRenderer.MODE_NAMES[modeIdx]);
            return;
        }

        // Последний тик режима: скриншот.
        if (rel % TICKS_PER_MODE == TICKS_PER_MODE - 2) {
            String file = SkyShaderRenderer.SHADER_NAMES[modeIdx] + ".png";
            ScreenshotRecorder.saveScreenshot(new File("skytest-shots"), file,
                    mc.getFramebuffer(), 1, t -> System.out.println("[skytest] saved " + file));
        }
    }
}
