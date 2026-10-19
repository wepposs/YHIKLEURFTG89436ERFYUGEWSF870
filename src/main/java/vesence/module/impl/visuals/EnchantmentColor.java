package vesence.module.impl.visuals;

import java.awt.Color;
import java.util.Random;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.render.ColorUtil;


@IModule(name = "EnchantmentColor", description = "Изменяет цвет зачарований", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class EnchantmentColor extends Module {

    public static EnchantmentColor INSTANCE;

    public final ModeSetting colorMode = new ModeSetting("Цвет", "Радужный", "Радужный", "Клиент");
    public final SliderSetting brightness = new SliderSetting("Яркость", 1.0, 0.5, 2.0, 0.05);
    public final BooleanSetting shineEnabled = new BooleanSetting("Блик", true);
    public final SliderSetting shineDuration = new SliderSetting("Длительность блика", 900.0, 300.0, 1500.0, 50.0)
            .hidden(() -> !shineEnabled.get());
    public final SliderSetting shineStrength = new SliderSetting("Сила блика", 1.0, 0.2, 1.0, 0.05)
            .hidden(() -> !shineEnabled.get());

    private static long nextPulseAt = 0L;
    private static long pulseStartAt = 0L;
    private static final long PULSE_MIN_INTERVAL_MS = 3000L;
    private static final long PULSE_MAX_INTERVAL_MS = 8000L;
    private static final Random RNG = new Random();

    /** Флаг: true когда сейчас рисуется glint (ставится/сбрасывается миксином). */
    public static volatile boolean glintDrawing = false;

    public EnchantmentColor() {
        INSTANCE = this;
        this.addSettings(new Setting[]{
                this.colorMode,
                this.brightness,
                this.shineEnabled,
                this.shineDuration,
                this.shineStrength
        });
    }

    @Override
    public void onEnable() {
        super.onEnable();
        nextPulseAt = System.currentTimeMillis() + 1500L;
        pulseStartAt = 0L;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        nextPulseAt = 0L;
        pulseStartAt = 0L;
    }

    public static boolean isActive() {
        return INSTANCE != null && INSTANCE.enable;
    }

    /**
     * Вызывается из миксина когда glint pipeline подготавливает uniforms.
     * Возвращает модифицированный ColorModulator (RGBA float).
     */
    public static org.joml.Vector4f getGlintColorModulator() {
        int color = getColor();
        float mul = currentBrightness();
        float r = ((color >> 16) & 0xFF) / 255.0f * mul;
        float g = ((color >> 8) & 0xFF) / 255.0f * mul;
        float b = (color & 0xFF) / 255.0f * mul;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a <= 0.0f) a = 1.0f;
        return new org.joml.Vector4f(r, g, b, a);
    }

    public static int getColor() {
        int baseColor = computeBaseColor();
        if (INSTANCE == null || !INSTANCE.shineEnabled.get()) return baseColor;
        float pulse = computePulseFactor();
        if (pulse <= 0.001f) return baseColor;
        // Усиливаем к белому: на пике осколок вспыхивает чистым белым.
        // clamp даёт «плато» яркого белого в середине вспышки.
        float whiteMix = Math.min(1.0f, pulse * 1.5f);
        return ColorUtil.interpolate(baseColor, 0xFFFFFFFF, whiteMix);
    }

    public static float currentBrightness() {
        if (INSTANCE == null) return 1.0f;
        float base = (float) (double) INSTANCE.brightness.get();
        if (!INSTANCE.shineEnabled.get()) return base;
        float pulse = computePulseFactor();
        return base + pulse * 0.8f;
    }

    private static float computePulseFactor() {
        if (INSTANCE == null) return 0.0f;
        long now = System.currentTimeMillis();
        if (nextPulseAt == 0L) nextPulseAt = now + scheduleInterval();
        if (pulseStartAt == 0L && now >= nextPulseAt) pulseStartAt = now;
        if (pulseStartAt == 0L) return 0.0f;
        long duration = (long) Math.max(50.0, INSTANCE.shineDuration.get());
        long elapsed = now - pulseStartAt;
        if (elapsed >= duration) {
            pulseStartAt = 0L;
            nextPulseAt = now + scheduleInterval();
            return 0.0f;
        }
        float t = elapsed / (float) duration;
        double s = Math.sin(Math.PI * t);
        // sin^8 — очень узкий резкий пик (яркая быстрая белая вспышка,
        // а не размытое затухание).
        double s2 = s * s;
        double s8 = s2 * s2 * s2 * s2;
        return (float) s8 * (float) (double) INSTANCE.shineStrength.get();
    }

    private static int computeBaseColor() {
        if (INSTANCE == null) return 0xFFFFFFFF;
        if (INSTANCE.colorMode.is("Клиент")) {
            return normalizeBrightness(ColorUtil.fade());
        }
        float hue = (System.currentTimeMillis() % 4500L) / 4500.0f;
        return Color.HSBtoRGB(hue, 1.0f, 1.0f) | 0xFF000000;
    }

    private static int normalizeBrightness(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int a = (rgb >> 24) & 0xFF;
        if (a == 0) a = 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float sat = Math.max(hsb[1], 0.8f);
        int boosted = Color.HSBtoRGB(hsb[0], sat, 1.0f);
        return (a << 24) | (boosted & 0x00FFFFFF);
    }

    private static long scheduleInterval() {
        long span = PULSE_MAX_INTERVAL_MS - PULSE_MIN_INTERVAL_MS;
        return PULSE_MIN_INTERVAL_MS + (long) (RNG.nextFloat() * span);
    }
}
