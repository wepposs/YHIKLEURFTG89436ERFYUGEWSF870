package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.InputConstants;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.TitleSetting;
import vesence.utils.render.utils.ClientSound;
import vesence.utils.render.utils.SoundUtil;

@IModule(name = "ClickGui", description = "Открывает ClickGui", category = Category.MISC, bind = InputConstants.KEY_RSHIFT)
@Environment(EnvType.CLIENT)
public class ClickGui extends Module {

    public static final TitleSetting basic = new TitleSetting("Interface");
    public static final BooleanSetting blurHud = new BooleanSetting("Размытие", true);
    public static final SliderSetting blurStrengthHud = new SliderSetting("Сила размытия", 25, 1, 45, 1).hidden(() -> !blurHud.get());
    public static final SliderSetting cornerHud = new SliderSetting("Скругление углов", 10, 1, 25, 1);
    public static final SliderSetting hudAlpha = new SliderSetting("Прозрачность", 0.75, 0.0, 1.0, 0.05);
    public static final BooleanSetting squircleHud = new BooleanSetting("Сквиркл скругление", false);

    public static final TitleSetting hudEditorTitle = new TitleSetting("Редактор HUD");
    public static final BooleanSetting hudGrid = new BooleanSetting("Сетка", true);
    public static final SliderSetting hudGridDensity = new SliderSetting("Плотность сетки", 10, 4, 40, 1).hidden(() -> !hudGrid.get());
    public static final BooleanSetting hudGuides = new BooleanSetting("Направляющие", true);
    public static final ModeSetting hudGuideStyle = new ModeSetting("Стиль линий", "Сплошные", "Сплошные", "Пунктир").hidden(() -> !hudGuides.get());
    public static final SliderSetting hudGuideOpacity = new SliderSetting("Прозрачность линий", 0.6, 0.05, 1.0, 0.05).hidden(() -> !hudGuides.get());
    public static final BooleanSetting hudGuideClientColor = new BooleanSetting("Линии цветом клиента", true).hidden(() -> !hudGuides.get());
    public static final HueSetting hudGuideColor = new HueSetting("Цвет линий", 60f).hidden(() -> !hudGuides.get() || hudGuideClientColor.get());
    public static final BooleanSetting hudCoords = new BooleanSetting("Координаты", true);
    public static final BooleanSetting hudCenterSnap = new BooleanSetting("Привязка к центру", true);
    public static final BooleanSetting hudEdgeSnap = new BooleanSetting("Привязка к краям", true);
    public static final BooleanSetting hudElementSnap = new BooleanSetting("Привязка к элементам", true);
    public static final SliderSetting hudSnapStrength = new SliderSetting("Сила привязки", 6, 1, 20, 1);
    public static final BooleanSetting hudEditHints = new BooleanSetting("Подсказки редактора", true);

    public static final TitleSetting gui = new TitleSetting("Click GUI");
    public static final BooleanSetting blurGui = new BooleanSetting("Размытие", true);
    public static final SliderSetting blurStrengthGui = new SliderSetting("Сила размытия", 25, 1, 45, 1).hidden(() -> !blurGui.get());
    public static final SliderSetting cornerGui = new SliderSetting("Скругление углов", 10, 0, 25, 1);
    public static final SliderSetting guiAlpha = new SliderSetting("Прозрачность", 0.75, 0.0, 1.0, 0.05);
    public static final BooleanSetting squircleGui = new BooleanSetting("Сквиркл скругление", false);
    public static final ModeSetting sort = new ModeSetting("Сортировка модулей", "По алфавиту", "По алфавиту", "По длине");
    public static final SliderSetting guiScale = new SliderSetting("Масштаб GUI", 1.0, 0.5, 2.0, 0.05);

    public static final TitleSetting colorTitle = new TitleSetting("Цвет и тема клиента");
    public static final ModeSetting colorMode = new ModeSetting("Режим цвета", "Установки", "Установки", "Двух-Цветный");
    public static final HueSetting customColor1 = new HueSetting("Цвет 1", 40f).hidden(() -> !colorMode.is("Двух-Цветный"));
    public static final HueSetting customColor2 = new HueSetting("Цвет 2", 72f).hidden(() -> !colorMode.is("Двух-Цветный"));
    public static final BooleanSetting themeTintBg = new BooleanSetting("Оттенок темы в GUI и HUD", false);

    public static final TitleSetting moduleSoundTitle = ClientSound.moduleSoundTitle;
    public static final ModeSetting moduleEnableSound = ClientSound.moduleSound;
    public static final SliderSetting moduleSoundVolume = ClientSound.moduleSoundVolume;

    private static final double HUD_CORNER_LIMIT = 15.0;
    private static final double GUI_CORNER_LIMIT = 15.0;

    public static boolean hudCornerOverflow = false;
    public static boolean guiCornerOverflow = false;
    private static long lastHudOverflowSound = 0;
    private static long lastGuiOverflowSound = 0;

    public ClickGui() {
        this.hiddenFromGui = true;
        this.addSettings(
            blurHud, blurStrengthHud, cornerHud, hudAlpha, squircleHud,
            hudEditorTitle, hudGrid, hudGridDensity, hudGuides, hudGuideStyle, hudGuideOpacity,
            hudGuideClientColor, hudGuideColor, hudCoords, hudCenterSnap, hudEdgeSnap,
            hudElementSnap, hudSnapStrength, hudEditHints,
            blurGui, blurStrengthGui, cornerGui, guiAlpha, squircleGui, sort, guiScale,
            colorTitle, colorMode, customColor1, customColor2, themeTintBg,
            moduleSoundTitle, moduleEnableSound, moduleSoundVolume
        );
    }

    public static void tickLimits() {
        hudCornerOverflow = false;
        if (!squircleHud.get() && cornerHud.current > HUD_CORNER_LIMIT) {
            hudCornerOverflow = true;
            long now = System.currentTimeMillis();
            if (now - lastHudOverflowSound > 300) {
                SoundUtil.playUi("unknowncommand", 0.5F, 200L);
                lastHudOverflowSound = now;
            }
            cornerHud.current = HUD_CORNER_LIMIT;
        }

        guiCornerOverflow = false;
        if (!squircleGui.get() && cornerGui.current > GUI_CORNER_LIMIT) {
            guiCornerOverflow = true;
            long now = System.currentTimeMillis();
            if (now - lastGuiOverflowSound > 300) {
                SoundUtil.playUi("unknowncommand", 0.5F, 200L);
                lastGuiOverflowSound = now;
            }
            cornerGui.current = GUI_CORNER_LIMIT;
        }
    }

    public static float getHudCorner() {
        return (float) cornerHud.current;
    }

    public static float getHudSquirt() {
        return squircleHud.get() ? 3 : 0f;
    }

    public static boolean isHudSquircle() {
        return squircleHud.get();
    }

    public static float getHudAlpha() {
        return hudAlpha.get().floatValue();
    }

    /** Цвет направляющих редактора HUD с заданной альфой: либо цвет клиента, либо кастомный из HueSetting. */
    public static int getGuideColor(int alpha) {
        int a = Math.max(0, Math.min(255, alpha));
        if (hudGuideClientColor.get()) {
            return vesence.utils.render.ColorUtil.replAlpha(
                    vesence.renderengine.render.Renderer2D.ColorUtil.getClientColor(), a);
        }
        java.awt.Color c = hudGuideColor.getColor();
        return vesence.utils.render.ColorUtil.getColor(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** Сила подмешивания цвета темы в чёрный фон (0..1); небольшой, чтобы фон лишь оттенялся темой. */
    private static final float THEME_TINT_STRENGTH = 0.18f;

    /** База фона карточек/панелей: чистый чёрный или чёрный с оттенком цвета темы. */
    private static int getBackgroundBase() {
        if (!themeTintBg.get()) {
            return vesence.utils.render.ColorUtil.BLACK;
        }
        return vesence.utils.render.ColorUtil.overCol(
                vesence.utils.render.ColorUtil.BLACK,
                vesence.utils.render.ColorUtil.theme(255), THEME_TINT_STRENGTH);
    }

    /** Фон карточек HUD/панелей GUI с заданной альфой (0..255). */
    public static int getBackgroundColor(int alpha) {
        return vesence.utils.render.ColorUtil.replAlpha(getBackgroundBase(), alpha);
    }

    /** Фон карточек HUD/панелей GUI с заданной альфой (0..1). */
    public static int getBackgroundColor(float alpha01) {
        return vesence.utils.render.ColorUtil.replAlpha(getBackgroundBase(), alpha01);
    }

    public static float getGuiCorner() {
        return (float) cornerGui.current;
    }

    public static float getGuiSquirt() {
        return squircleGui.get() ? 3 : 0f;
    }

    public static boolean isGuiSquircle() {
        return squircleGui.get();
    }

}
