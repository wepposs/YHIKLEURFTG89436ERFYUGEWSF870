package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import vesence.event.EventInit;
import vesence.event.player.AttackEvent;
import vesence.event.player.EventMotion;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.HueSetting;
import net.minecraft.util.Mth;
import vesence.module.impl.visuals.world.CubesEngine;
import vesence.module.impl.visuals.world.LineGlyphsEngine;
import vesence.renderengine.render.Renderer2D;

import java.util.ArrayList;
import java.util.List;

@IModule(name = "Custom Level", description = "Изменяет время, туман, кубы и линии вокруг мира", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Ambience extends Module {

    public static final String FOG_DO_NOTHING = "Ничего не делать";
    public static final String FOG_CLEAR = "Очистить";
    public static final String FOG_OVERRIDE = "Переопределить";

    public static ModeSetting mode = new ModeSetting("Время игры", "День", "День", "Ночь", "Вечер", "Закат", "Рассвет", "Кастом");
    public static SliderSetting customTime = new SliderSetting("Ваше время", 6000, 0, 24000, 100, false)
            .hidden(() -> !mode.is("Кастом"));

    // --- НОВЫЕ НАСТРОЙКИ ПОГОДЫ ---
    public static ModeSetting weather = new ModeSetting("Погода", "Обычная", "Обычная", "Ясно", "Дождь", "Гроза");
    public static BooleanSetting muteWeatherSounds = new BooleanSetting("Убрать звуки погоды", false);
    // ------------------------------

    public static ModeSetting fog = new ModeSetting("Туман", FOG_DO_NOTHING, FOG_DO_NOTHING, FOG_CLEAR, FOG_OVERRIDE);
    public static SliderSetting fogStart = (SliderSetting) new SliderSetting("Начало тумана", 0.5F, 0.05F, 1.5F, 0.05F, false).hidden(() -> !fog.is(FOG_OVERRIDE));
    public static SliderSetting fogEnd = (SliderSetting) new SliderSetting("Конец тумана", 1.0F, 0.1F, 3.0F, 0.05F, false).hidden(() -> !fog.is(FOG_OVERRIDE));
    // Цвет тумана: тема клиента или свой цвет.
    public static ModeSetting fogColorMode = new ModeSetting("Цвет тумана", "Тема", "Тема", "Свой")
            .hidden(() -> !fog.is(FOG_OVERRIDE));
    public static HueSetting fogColor = (HueSetting) new HueSetting("Свой цвет тумана", 40.0F)
            .hidden(() -> !fog.is(FOG_OVERRIDE) || !fogColorMode.is("Свой"));

    // Размытие мира (полный порт Relevant WorldTweaks FogBlur).
    public static BooleanSetting blurWorld = new BooleanSetting("Размытие мира", false);
    public static SliderSetting blurStrength = (SliderSetting) new SliderSetting("Сила размытия", 3.0F, 1.0F, 20.0F, 0.5F, false)
            .hidden(() -> !blurWorld.get());
    public static SliderSetting blurFogDistance = (SliderSetting) new SliderSetting("Дистанция тумана", 80.0F, 0.0F, 200.0F, 1.0F, false)
            .hidden(() -> !blurWorld.get());
    public static BooleanSetting blurLinear = new BooleanSetting("Линейная выборка", true)
            .hidden(() -> !blurWorld.get());
    public static BooleanSetting blurRgbPuke = new BooleanSetting("Fog RGB Puke", false)
            .hidden(() -> !blurWorld.get());
    public static SliderSetting blurRgbOpacity = (SliderSetting) new SliderSetting("RGB прозрачность", 15.0F, 1.0F, 100.0F, 1.0F, false)
            .hidden(() -> !blurWorld.get() || !blurRgbPuke.get());
    public static SliderSetting blurRgbSaturation = (SliderSetting) new SliderSetting("RGB насыщенность", 70.0F, 0.0F, 100.0F, 1.0F, false)
            .hidden(() -> !blurWorld.get() || !blurRgbPuke.get());
    public static SliderSetting blurRgbBrightness = (SliderSetting) new SliderSetting("RGB яркость", 100.0F, 0.0F, 100.0F, 1.0F, false)
            .hidden(() -> !blurWorld.get() || !blurRgbPuke.get());

    public static BooleanSetting skyShader = new BooleanSetting("Шейдер для неба", false);
    public static BooleanSetting hideSunMoon = new BooleanSetting("Убирать солнце и луну", false);
    public static ModeSetting skyShaderMode = new ModeSetting("Тип шейдера неба",
            "Космос",
            "Космос", "Лето", "Облака", "Закат", "Сияние",
            "Галактика", "Рассвет", "Гроза", "Туманность", "Неон",
            "Метеоры", "Инферно", "Психоделика", "Радуга", "Луна",
            "Затмение", "Сумерки", "Кристалл", "Океан", "Вулкан",
            "Зима", "Лава", "Призма", "Матрица", "Звездопад",
            "Пастель", "Ледник", "Плазма", "Сакура", "Бездна",
            "Спектр", "Кровавая луна", "Изумруд")
            .hidden(() -> !skyShader.get());
    public static SliderSetting skySpeed = (SliderSetting) new SliderSetting("Скорость неба", 1.0F, 0.1F, 5.0F, 0.1F, false)
            .hidden(() -> !skyShader.get());
    public static SliderSetting skyIntensity = (SliderSetting) new SliderSetting("Яркость неба", 1.0F, 0.2F, 2.0F, 0.05F, false)
            .hidden(() -> !skyShader.get());
    public static BooleanSetting changeSaturation = new BooleanSetting("Изменить насыщенность игры", false);
    public static SliderSetting saturationLevel = (SliderSetting) new SliderSetting("Уровень насыщенности", 1.0F, 0.0F, 2.0F, 0.05F, false).hidden(() -> !changeSaturation.get());

    private final BooleanSetting cubesEnabled = new BooleanSetting("Кубы", false);
    private final BooleanSetting glyphsEnabled = new BooleanSetting("Линии", false);
    private final CubesEngine cubes = new CubesEngine();
    private final LineGlyphsEngine glyphs = new LineGlyphsEngine();

    private static Ambience instance;

    public Ambience() {
        this.addSettings(this.buildSettings());
        instance = this;
    }

    private Setting[] buildSettings() {
        List<Setting> list = new ArrayList<>();
        list.add(mode);
        list.add(customTime);
        list.add(weather);            // Добавлено
        list.add(muteWeatherSounds);  // Добавлено
        list.add(fog);
        list.add(fogStart);
        list.add(fogEnd);
        list.add(fogColorMode);
        list.add(fogColor);
        list.add(blurWorld);
        list.add(blurStrength);
        list.add(blurFogDistance);
        list.add(blurLinear);
        list.add(blurRgbPuke);
        list.add(blurRgbOpacity);
        list.add(blurRgbSaturation);
        list.add(blurRgbBrightness);
        list.add(skyShader);
        list.add(hideSunMoon);
        list.add(skyShaderMode);
        list.add(skySpeed);
        list.add(skyIntensity);
        list.add(changeSaturation);
        list.add(saturationLevel);

        list.add(this.cubesEnabled);
        for (Setting s : this.cubes.settings()) {
            list.add(this.gate(s, this.cubesEnabled));
        }
        list.add(this.glyphsEnabled);
        for (Setting s : this.glyphs.settings()) {
            list.add(this.gate(s, this.glyphsEnabled));
        }
        return list.toArray(new Setting[0]);
    }

    private Setting gate(Setting setting, BooleanSetting toggle) {
        java.util.function.Supplier<Boolean> previous = setting.hidden;
        setting.hidden = () -> !toggle.get() || (previous != null && previous.get());
        return setting;
    }

    public static long getVisualTime(long realTime) {
        if (instance == null || !instance.enable) return realTime;

        if (mode.is("День")) return 6000;
        if (mode.is("Ночь")) return 18000;
        if (mode.is("Вечер")) return 13000;
        if (mode.is("Закат")) return 12000;
        if (mode.is("Рассвет")) return 23000;
        if (mode.is("Кастом")) return (long) customTime.current;

        return realTime;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    /**
     * Включено ли скрытие ванильных солнца и луны (звёзды остаются).
     */
    public static boolean shouldHideSunMoon() {
        return instance != null && instance.enable && hideSunMoon.get();
    }

    public static Ambience getInstance() {
        return instance;
    }

    public static float getSaturation() {
        if (instance == null || !instance.enable || !changeSaturation.get()) return 1.0F;
        return saturationLevel.get().floatValue();
    }

    // --- НОВЫЕ МЕТОДЫ ДЛЯ ПОГОДЫ ---

    /**
     * Проверяет, включено ли переопределение погоды.
     */
    public static boolean isWeatherOverridden() {
        return instance != null && instance.enable && !weather.is("Обычная");
    }

    /**
     * Возвращает градиент дождя (0.0 - нет дождя, 1.0 - максимальный дождь).
     * Вызывать из миксина в ClientLevel (например, в методе getRainGradient).
     */
    public static float getRainGradient(float original) {
        if (!isWeatherOverridden()) return original;
        if (weather.is("Ясно")) return 0.0F;
        if (weather.is("Дождь") || weather.is("Гроза")) return 1.0F;
        return original;
    }

    /**
     * Возвращает градиент грозы (0.0 - нет грозы, 1.0 - максимальная гроза).
     * Вызывать из миксина в ClientLevel (например, в методе getThunderGradient).
     */
    public static float getThunderGradient(float original) {
        if (!isWeatherOverridden()) return original;
        if (weather.is("Ясно") || weather.is("Дождь")) return 0.0F;
        if (weather.is("Гроза")) return 1.0F;
        return original;
    }

    /**
     * Проверяет, нужно ли отключать звуки дождя и грозы.
     * Вызывать из миксина в SoundManager или SoundSystem перед воспроизведением звука.
     */
    public static boolean shouldMuteWeatherSounds() {
        return instance != null && instance.enable && muteWeatherSounds.get();
    }

    // --------------------------------

    public boolean shouldOverrideFog() {
        return this.enable && fog.is(FOG_OVERRIDE);
    }

    public boolean isFogClearMode() {
        return this.enable && fog.is(FOG_CLEAR);
    }

    public boolean isFogOverrideMode() {
        return this.enable && fog.is(FOG_OVERRIDE);
    }

    public float getFogStartMultiplier() {
        return fogStart.get().floatValue();
    }

    public float getFogEndMultiplier() {
        return fogEnd.get().floatValue();
    }

    public boolean isBlurEnabled() {
        return this.enable && blurWorld.get();
    }

    public float getBlurStrength() {
        return blurStrength.get().floatValue();
    }

    public float getBlurFogDistance() {
        return blurFogDistance.get().floatValue();
    }

    public boolean isBlurLinear() {
        return blurLinear.get();
    }

    private static vesence.renderengine.render.pipeline.FogBlurPipeline blurPipeline;

    /**
     * Рендер размытия мира (depth-masked fog blur) — вызывается из
     * WorldRendererMixin сразу после отрисовки мира, до HUD. Размывает только
     * дальние пиксели по глубине (туман), а не весь экран.
     */
    public void renderFogBlur() {
        if (!isBlurEnabled()) return;
        if (mc.player == null || mc.level == null) return;
        // TODO(port26): GameRenderer.isRenderingPanorama() удалён в 26.3 (панорама перенесена в CubeMap) — флага нет.
        if (mc.gameRenderer == null) return;
        try {
            if (blurPipeline == null) {
                blurPipeline = new vesence.renderengine.render.pipeline.FogBlurPipeline();
            }
            blurPipeline.apply(mc, getBlurStrength(), getBlurFogDistance(), isBlurLinear(),
                    blurRgbPuke.get(),
                    blurRgbOpacity.get().floatValue() / 100.0F,
                    blurRgbSaturation.get().floatValue() / 100.0F,
                    blurRgbBrightness.get().floatValue() / 100.0F);
        } catch (Exception ignored) {
        }
    }

    public int getFogColorValue() {
        if (fogColorMode.is("Свой")) {
            return fogColor.getRGB();
        }
        return Renderer2D.ColorUtil.getClientColor();
    }

    @vesence.event.EventInit
    public void onWorldRender(vesence.event.render.WorldRenderEvent event) {
        // Небо теперь рисуется из SkyRenderingMixin на стадии неба (порт Relevant).
        // Здесь ничего не делаем.
    }

    @Override
    public void onDisable() {
        this.cubes.clear();
        this.glyphs.clear();
        super.onDisable();
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (this.glyphsEnabled.get()) {
            this.glyphs.onMotion();
        } else {
            this.glyphs.clear();
        }
    }

    @EventInit
    public void onAttack(AttackEvent event) {
        if (this.cubesEnabled.get() && mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            this.cubes.onAttack(mc.gameRenderer.mainCamera());
        }
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        PoseStack matrices = event.getMatrixStack();
        Camera camera = mc.gameRenderer.mainCamera();

        if (this.cubesEnabled.get()) {
            this.cubes.onRender3D(matrices, camera);
        } else {
            this.cubes.clear();
        }

        if (this.glyphsEnabled.get()) {
            this.glyphs.onRender3D(matrices, event.getTickDelta());
        }
    }
}