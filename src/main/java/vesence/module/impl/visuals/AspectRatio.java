package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;

/**
 * Screen Ratio (порт Relevant AspectRatio) — растяжение/сужение проекционной
 * матрицы по выбранному соотношению сторон. Применяется в
 * {@link vesence.mixin.GameRendererAspectMixin}: множитель для m00 берётся из
 * {@link #computeAnimatedScale(int, int)}.
 * <p>
 * При смене пресета/включении соотношение меняется НЕ резко, а плавно
 * интерполируется к целевому значению (экспоненциальное сглаживание по
 * реальному времени кадра).
 */
@IModule(name = "Screen Ratio", description = "Меняет соотношение сторон экрана", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class AspectRatio extends Module {

    public static ModeSetting aspect = new ModeSetting(
            "Соотношение",
            "16:9",
            "16:9", "4:3", "1:1", "16:10", "21:9", "32:9", "5:4", "2:1", "Кастомное"
    );
    public static SliderSetting customAspect = (SliderSetting) new SliderSetting(
            "Сила растяжения", 1.5, 1.0, 3.0, 0.05, false)
            .hidden(() -> !aspect.is("Кастомное"));

    private static AspectRatio instance;

    /** Текущее (анимированное) значение множителя m00. */
    private float animatedScale = 1.0f;
    private long lastUpdateNanos = 0L;

    public AspectRatio() {
        this.addSettings(new Setting[]{aspect, customAspect});
        instance = this;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    public static AspectRatio getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // Стартуем анимацию с "без растяжения", чтобы соотношение плавно
        // вползло к целевому, а не появилось резко.
        this.animatedScale = 1.0f;
        this.lastUpdateNanos = 0L;
    }

    /**
     * Целевой множитель для m00 проекционной матрицы (без анимации).
     * <ul>
     *   <li>1.0F — растяжение не нужно (соотношение уже совпадает).</li>
     *   <li>иначе — currentAspect / targetAspect.</li>
     * </ul>
     */
    private float computeTargetScale(int fbWidth, int fbHeight) {
        if (fbWidth <= 0 || fbHeight <= 0) return 1.0f;

        float targetAspect = resolveTargetAspect();
        if (targetAspect <= 1.0E-4F) return 1.0f;

        float currentAspect = (float) fbWidth / (float) fbHeight;
        if (Math.abs(currentAspect - targetAspect) < 1.0E-3F) return 1.0f;
        return currentAspect / targetAspect;
    }

    /**
     * Плавно анимированный множитель для m00. Вызывается из миксина каждый
     * кадр. Интерполирует {@link #animatedScale} к целевому значению с
     * экспоненциальным сглаживанием, независимым от FPS.
     */
    public float computeAnimatedScale(int fbWidth, int fbHeight) {
        float target = computeTargetScale(fbWidth, fbHeight);

        long now = System.nanoTime();
        if (this.lastUpdateNanos == 0L) {
            this.lastUpdateNanos = now;
            this.animatedScale = target;
            return this.animatedScale;
        }

        float dt = (now - this.lastUpdateNanos) / 1.0E9f;
        this.lastUpdateNanos = now;
        // Ограничим dt чтобы после лага/паузы не было резкого рывка.
        if (dt > 0.1f) dt = 0.1f;

        // Скорость сглаживания (чем больше — тем быстрее применяется).
        float speed = 7.0f;
        float t = 1.0f - (float) Math.exp(-speed * dt);
        this.animatedScale += (target - this.animatedScale) * t;

        // Снэп к цели когда почти дошли — убирает бесконечно малый хвост.
        if (Math.abs(target - this.animatedScale) < 1.0E-4f) {
            this.animatedScale = target;
        }
        return this.animatedScale;
    }

    private float resolveTargetAspect() {
        if (aspect.is("16:9"))  return 16.0f / 9.0f;
        if (aspect.is("4:3"))   return 4.0f  / 3.0f;
        if (aspect.is("1:1"))   return 1.0f;
        if (aspect.is("16:10")) return 16.0f / 10.0f;
        if (aspect.is("21:9"))  return 21.0f / 9.0f;
        if (aspect.is("32:9"))  return 32.0f / 9.0f;
        if (aspect.is("5:4"))   return 5.0f  / 4.0f;
        if (aspect.is("2:1"))   return 2.0f;
        // Кастомное
        return (float) customAspect.current;
    }
}
