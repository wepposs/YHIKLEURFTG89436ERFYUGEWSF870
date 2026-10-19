package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.render.math.animation.AnimationUtils;
import vesence.utils.render.math.animation.anim.util.Easing;
import vesence.utils.render.math.animation.anim.util.Easings;

/**
 * Полный порт Relevant {@code Animations}. Анимирует выбранные UI-действия:
 * <ul>
 *   <li><b>Список игроков</b> — вертикальный сдвиг TabList'а
 *       ({@link vesence.mixin.PlayerListHudAnimationMixin}).</li>
 *   <li><b>Инвентарь</b> — масштаб 0.5→1.0 от центра при открытии
 *       ({@link vesence.mixin.HandledScreenAnimationMixin}).</li>
 *   <li><b>Приближение камеры</b> — мягкий FOV-зум при открытии экрана
 *       ({@link vesence.mixin.GameRendererAnimationFovMixin}).</li>
 *   <li><b>Изменение перспективы</b> — bump FOV при смене вида
 *       ({@link vesence.mixin.GameRendererAnimationFovMixin}).</li>
 * </ul>
 * Кривая фиксирована {@link Easings#CUBIC_OUT}, скорость настраивается.
 */
@IModule(name = "Animations", description = "Анимирует выбранные действия", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Animations extends Module {

    /** Чекбоксы выбора того, что анимировать (аналог Relevant ListSetting). */
    public static MultiBooleanSetting mode = new MultiBooleanSetting(
            "Что анимировать",
            new BooleanSetting("Список игроков", true),
            new BooleanSetting("Инвентарь", true),
            new BooleanSetting("Приближение камеры", false),
            new BooleanSetting("Изменение перспективы", true)
    );

    /** Скорость анимации. Дефолт 12.0 — точное значение из Relevant/Divine. */
    public static SliderSetting speed = new SliderSetting("Скорость", 12.0, 4.0, 24.0, 1.0, false);

    /** Кривая интерполяции — фиксированный CUBIC_OUT, как в Relevant. */
    private static final Easing FIXED_EASE = Easings.CUBIC_OUT;

    /** Локальный аниматор TabList'а (используется из миксина). */
    private final AnimationUtils playerListAnim = new AnimationUtils(0F, 12.0F, FIXED_EASE);

    /**
     * Общий аниматор открытия инвентаря (0.5→1.0). Один на клиент — открыт
     * только один инвентарь за раз. Используется И миксином масштаба панели
     * ({@link vesence.mixin.HandledScreenAnimationMixin}), И редиректом рендера
     * куклы ({@link vesence.mixin.InventoryScreenDollAnimationMixin}) — чтобы
     * панель и кукла масштабировались синхронно.
     */
    private final AnimationUtils inventoryOpenAnim = new AnimationUtils(0.5F, 12.0F, FIXED_EASE);

    private static Animations instance;

    public Animations() {
        this.addSettings(new Setting[]{mode, speed});
        instance = this;
    }

    public static Animations getInstance() {
        return instance;
    }

    /* ─── Public API для миксинов ─── */

    public boolean isPlayerListEnabled() {
        return this.enable && mode.get("Список игроков");
    }

    public boolean isInventoryEnabled() {
        return this.enable && mode.get("Инвентарь");
    }

    public boolean isCameraZoomEnabled() {
        return this.enable && mode.get("Приближение камеры");
    }

    public boolean isPerspectiveEnabled() {
        return this.enable && mode.get("Изменение перспективы");
    }

    public Easing currentEase() {
        return FIXED_EASE;
    }

    public float currentSpeed() {
        return (float) speed.current;
    }

    /** Аниматор TabList'а с применёнными текущими настройками. */
    public AnimationUtils playerListAnim() {
        this.playerListAnim.setSpeed((float) speed.current);
        this.playerListAnim.setEasing(FIXED_EASE);
        return this.playerListAnim;
    }

    /** Общий аниматор открытия инвентаря (панель + кукла). */
    public AnimationUtils inventoryOpenAnim() {
        this.inventoryOpenAnim.setSpeed((float) speed.current);
        this.inventoryOpenAnim.setEasing(FIXED_EASE);
        return this.inventoryOpenAnim;
    }
}
