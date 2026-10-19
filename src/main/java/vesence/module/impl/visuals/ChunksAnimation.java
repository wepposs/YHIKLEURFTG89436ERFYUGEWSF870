package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;

/**
 * ChunksAnimation — полный порт из RelevantPremiumpp4.
 * Анимация появления чанков при загрузке мира: мир "поднимается" снизу/сверху
 * или "сжимается". Используется через {@link #computeOffsetY()} в миксине
 * WorldRenderer.
 */
@IModule(name = "ChunksAnimation", description = "Анимация появления чанков при загрузке мира", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ChunksAnimation extends Module {

    public static ChunksAnimation INSTANCE;

    public final ModeSetting direction = new ModeSetting("Направление", "Снизу", "Снизу", "Сверху", "Сжатие");
    public final SliderSetting amplitude = new SliderSetting("Амплитуда", 64.0, 8.0, 200.0, 1.0);
    public final SliderSetting duration = new SliderSetting("Длительность (мс)", 700.0, 200.0, 2500.0, 50.0);
    public final ModeSetting easing = new ModeSetting("Сглаживание", "OutCubic", "Linear", "OutCubic", "OutQuint", "OutBack");

    private long birthTimestamp = 0L;
    private Object lastWorldRef = null;

    public ChunksAnimation() {
        INSTANCE = this;
        this.addSettings(new Setting[]{this.direction, this.amplitude, this.duration, this.easing});
    }

    @Override
    public void onEnable() {
        this.birthTimestamp = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        this.lastWorldRef = mc != null ? mc.world : null;
        super.onEnable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        Object currentWorld = mc.world;
        if (currentWorld != null && currentWorld != this.lastWorldRef) {
            this.birthTimestamp = System.currentTimeMillis();
            this.lastWorldRef = currentWorld;
        } else if (currentWorld == null) {
            this.lastWorldRef = null;
        }
    }

    /**
     * Возвращает вертикальное смещение (в блоках) для cameraY в renderLayer.
     * Когда анимация закончилась — 0.
     */
    public float computeOffsetY() {
        if (!this.enable) return 0.0F;
        long now = System.currentTimeMillis();
        long elapsed = now - this.birthTimestamp;
        float dur = (float) (double) this.duration.get();
        if (elapsed >= (long) dur || elapsed < 0L) return 0.0F;

        float t = elapsed / dur;
        float remaining = 1.0F - this.applyEasing(t);
        float amp = (float) (double) this.amplitude.get();

        if (this.direction.is("Сверху")) {
            return -amp * remaining;
        }
        if (this.direction.is("Сжатие")) {
            return amp * remaining * (float) Math.cos(t * Math.PI * 2.0);
        }
        // Снизу (default)
        return amp * remaining;
    }

    private float applyEasing(float t) {
        t = MathHelper.clamp(t, 0.0F, 1.0F);
        if (this.easing.is("Linear")) return t;
        if (this.easing.is("OutQuint")) return 1.0F - (float) Math.pow(1.0F - t, 5.0);
        if (this.easing.is("OutBack")) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            return 1.0F + c3 * (float) Math.pow(t - 1.0, 3.0) + c1 * (float) Math.pow(t - 1.0, 2.0);
        }
        // OutCubic (default)
        return 1.0F - (float) Math.pow(1.0F - t, 3.0);
    }
}
