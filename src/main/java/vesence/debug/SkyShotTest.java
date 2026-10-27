package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import vesence.module.impl.visuals.Ambience;
import vesence.renderengine.render.SkyShaderRenderer;

import java.io.File;

/**
 * Временный авто-тест неба: активируется ТОЛЬКО при переменной окружения
 * VESENCE_SKYTEST=1. Заходит в мир (через --quickPlaySingleplayer), включает
 * модуль Custom Level с шейдером неба, фотографирует все режимы по очереди
 * и выключает клиент. Скриншоты — в run/skytest-shots/.
 */
@Environment(EnvType.CLIENT)
public final class SkyShotTest {
    public static final boolean ACTIVE = System.getenv("VESENCE_SKYTEST") != null;
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

    private static void tick(Minecraft mc) {
        if (finished || mc.player == null || mc.level == null) return;
        // Клиент в фоне теряет фокус → ваниль ставит паузу, и в скриншот
        // попадает меню паузы. Снимаем её и закрываем экран.
        if (mc.isPaused()) mc.pauseGame(false);
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        ticks++;

        // Камера у спавна (там чанки точно загружены), взгляд выше горизонта.
        // Телепортировать высоко нельзя: в точке телепорта чанки не
        // прогружены, и вместо неба рендерится геометрия ближайших блоков.
        var spawn = mc.level.getRespawnData().pos();
        mc.player.setPos(spawn.getX() + 0.5, spawn.getY() + 20.0, spawn.getZ() + 0.5);
        mc.player.setDeltaMovement(0.0, 0.0, 0.0);
        mc.player.setYRot(35.0F);
        mc.player.setXRot(-22.0F);

        // Модель игрока убираем физически: в первом лице она не рисуется,
        // но клиент успевает отрисовать её до применения режима камеры.
        // Скрытая сущность в кадр не попадает вообще.
        mc.player.setInvisible(true);
        if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle();

        if (ticks < WARMUP_TICKS) return;

        if (!moduleOn) {
            Ambience amb = Ambience.getInstance();
            if (amb == null) return;
            amb.enable = true;
            Ambience.skyShader.set(true);
            Ambience.hideSunMoon.set(true);
            // Туман мира подмешивается в низ неба (finishSky) и в тесте
            // перекрашивает дневные сцены в серый. Для оценки шейдера он
            // должен быть выключен.
            Ambience.fog.currentMode = Ambience.FOG_DO_NOTHING;
            // HUD-элементы vesence рисуются поверх неба и засоряют кадр.
            if (vesence.Vesence.get != null && vesence.Vesence.get.manager != null) {
                for (var m : vesence.Vesence.get.manager.getModules()) {
                    if (m instanceof vesence.module.impl.visuals.Hud) m.enable = false;
                }
            }
            // Ясная погода и полдень: частицы дождя и ночная темнота портят
            // кадр и к качеству шейдера отношения не имеют. Телепорт не нужен:
            // позицию выставляет тик, а лишний tp рассинхронизирует клиент.
            // Ясная погода и полдень: частицы дождя и ночная темнота портят
            // кадр и к качеству шейдера отношения не имеют.
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("weather clear");
                mc.player.connection.sendCommand("time set noon");
            }
            moduleOn = true;
            new File("skytest-shots").mkdirs();
            System.out.println("[skytest] module enabled, starting shots");
        }

        int rel = ticks - WARMUP_TICKS;
        int target = rel / TICKS_PER_MODE;
        if (target >= SkyShaderRenderer.MODE_NAMES.length) {
            finished = true;
            System.out.println("[skytest] all modes captured, stopping");
            mc.stop();
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
            System.out.println("[skytest] shot " + file
                    + " pos=" + String.format("%.1f/%.1f/%.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ())
                    + " pipeline=" + SkyShaderRenderer.getInstance().debugPipelineReady(
                            SkyShaderRenderer.SHADER_NAMES[modeIdx])
                    + " screen=" + mc.gui.screen()
                    + " paused=" + mc.isPaused());
            Screenshot.grab(new File("skytest-shots"), file,
                    mc.gameRenderer.mainRenderTarget(), 1, t -> System.out.println("[skytest] saved " + file));
        }
    }
}
