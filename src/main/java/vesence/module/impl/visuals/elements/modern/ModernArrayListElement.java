package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ArrayList: порт {@code ArrayListRenderer} из privatedlc (Excellent 1.16.5).
 * Включённые модули, отсортированные по ширине имени (по убыванию),
 * правый верх экрана. Строки выезжают справа с градиентной подсветкой
 * цвета темы, как в источнике — но в modern-дизайне vesence: чёрные
 * stepped-карточки с тенью, SF_SEMI, HSB-градиент по строкам.
 *
 * <p>Правое выравнивание сохранено из источника: карточки прижаты к
 * правому краю bounds, элемент ставится у правого края экрана.
 */
@Environment(EnvType.CLIENT)
public class ModernArrayListElement extends HudElement {

    private static final float PADDING_H = 12f;
    private static final float PADDING_V = 10f;
    private static final float FONT_SIZE = 28f;
    private static final float LINE_HEIGHT = 29f;
    private static final float ROW_BOX_HEIGHT = 25f;
    private static final float ROW_RADIUS = 8f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private final Map<String, Animation2> moduleAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    private float boundsW = 140f;
    private float boundsH = ROW_BOX_HEIGHT;

    /** Категории модулей для отображения (настройка элемента, ПКМ в гуи). */
    public final MultiBooleanSetting categoryFilter = new MultiBooleanSetting("Категории",
            new BooleanSetting("Combat", true),
            new BooleanSetting("Movement", true),
            new BooleanSetting("Visuals", true),
            new BooleanSetting("Player", true),
            new BooleanSetting("Misc", true),
            new BooleanSetting("Display", false)
    );

    public ModernArrayListElement() {
        super("ArrayList", 640f, 36f);
        addMultiBooleanSetting(categoryFilter);
        visibilityAnim.set(0.0);
        widthAnim.set(140f);
        this.enabled = false;
    }

    /** Пропускает ли фильтр категорий модуль. */
    private boolean categoryAllowed(Module mod) {
        return categoryFilter.get(mod.category.getName());
    }

    /** Включённые, видимые и прошедшие фильтр категорий модули, по убыванию ширины имени. */
    private List<Module> sortedEnabledModules(Renderer2D renderer, FontObject f) {
        List<Module> list = new ArrayList<>();
        for (Module mod : Vesence.get.manager.getModules()) {
            if (!mod.enable) continue;
            if (mod.hiddenFromGui) continue;
            if (!categoryAllowed(mod)) continue;
            list.add(mod);
        }
        FontObject font = f != null ? f : FontRegistry.SF_SEMI;
        if (font == null) {
            list.sort(Comparator.comparing(m -> m.name, Comparator.reverseOrder()));
            return list;
        }
        list.sort(Comparator.comparingDouble((Module m) ->
                -renderer.measureText(font, m.getDisplayName(), FONT_SIZE, -0.1f).width));
        return list;
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        List<Module> modules = sortedEnabledModules(renderer, f);

        // Анимации строк: включённые выезжают, выключенные уезжают.
        for (Module mod : modules) {
            Animation2 anim = moduleAnims.computeIfAbsent(mod.name, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(1.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(mod.name, k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
        }

        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, Animation2> e : moduleAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = modules.stream().anyMatch(m -> m.name.equals(key));
            if (!stillActive) {
                e.getValue().update();
                e.getValue().run(0.0, 0.15, Easings.CUBIC_OUT);
                Animation2 slot = slotAnims.get(key);
                if (slot != null) {
                    slot.update();
                    slot.run(0.0, 0.15, Easings.CUBIC_OUT);
                    if (slot.get() < 0.5 && e.getValue().get() < 0.005) dead.add(key);
                }
            }
        }
        for (String key : dead) {
            moduleAnims.remove(key);
            slotAnims.remove(key);
        }

        boolean hasAny = !modules.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;

        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        // Максимальная ширина имени + иконка категории слева (stepped:
        // каждая строка своей ширины, bounds — по самой широкой).
        float maxNameW = 0f;
        for (Module mod : modules) {
            float nameW = renderer.measureText(f, mod.getDisplayName(), FONT_SIZE, -0.1f).width;
            if (nameW > maxNameW) maxNameW = nameW;
        }

        float contentW = Math.max(110f, maxNameW + 34f);
        float autoRectW = contentW + PADDING_H + 6f;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        // rectW — анимированная максимальная ширина списка.
        float rectW = (float) widthAnim.get();

        // Правый край списка фиксирован от x: панель растёт вправо и всегда
        // остаётся внутри bounds (x .. x+widestRow), без вылета за край.
        float rightX = x + rectW;

        int visibleRows = 0;

        // ---- Единая stepped-панель (слитая, без отдельных плашек) ----
        // Сначала собираем видимые строки (после анимаций), потом одной
        // панелью с per-corner скруглениями на переходах ширин.
        List<Module> visibleMods = new ArrayList<>();
        List<Float> visibleAlphas = new ArrayList<>();
        List<Animation2> visibleSlots = new ArrayList<>();
        for (Module mod : modules) {
            Animation2 anim = moduleAnims.get(mod.name);
            Animation2 slot = slotAnims.get(mod.name);
            if (anim == null || slot == null) continue;
            if (anim.get() < 0.01 && slot.get() < 0.5) continue;
            float modAlpha = (float) (anim.get() * globalAlpha);
            if (modAlpha <= 0.005f) continue;
            visibleMods.add(mod);
            visibleAlphas.add(modAlpha);
            visibleSlots.add(slot);
        }

        // Ширина каждой строки = контент строки (stepped: длинные сверху).
        // Левый край ступенек, правый — общий у всех строк.
        // ВЫСОТА строки панели = фактическая slot-высота (анимируется выездом) —
        // раньше была фикс. 25px при текст-шаге 29px, панель «уезжала» вверх
        // и нижние строки оставались без фона.
        float[] rowWidths = new float[visibleMods.size()];
        float[] rowHeights = new float[visibleMods.size()];
        for (int i = 0; i < visibleMods.size(); i++) {
            float nameW = renderer.measureText(f, visibleMods.get(i).getDisplayName(), FONT_SIZE, -0.1f).width;
            float w = Math.max(70f, nameW + 34f) + PADDING_H + 6f;
            // Ширина тоже выезжает вместе с alpha строки.
            w *= 0.6f + 0.4f * visibleAlphas.get(i);
            rowWidths[i] = w;
            rowHeights[i] = Math.max(ROW_BOX_HEIGHT, (float) visibleSlots.get(i).get());
        }

        if (!visibleMods.isEmpty()) {
            // Blur по stepped-геометрии: каждую строку блюрим в ЕЁ клипе
            // (строго своей высотой) — blur не вылезает за ступеньки и не
            // наслаивается, никакого лишнего прямоугольника позади списка.
            if (isBlurEnabled()) {
                float blurY = y;
                for (int i = 0; i < rowWidths.length; i++) {
                    float w = rowWidths[i];
                    float h = rowHeights[i];
                    if (w > 0.5f && h > 0.5f) {
                        renderer.pushClipRect(rightX - w, blurY, w, h);
                        renderer.blur(rightX - w, blurY, w, h,
                                ClickGui.blurStrengthHud.get().floatValue(), ROW_RADIUS, globalAlpha);
                        renderer.popClipRect();
                    }
                    blurY += h;
                }
            }
            drawSteppedRect(renderer, rightX, y, rowWidths, rowHeights,
                    ClickGui.getBackgroundColor((int) (200f * ClickGui.getHudAlpha() * globalAlpha)),
                    ROW_RADIUS, ROW_RADIUS * 0.75f);
        }

        // ---- Контент строк поверх панели ----
        float curY = y;
        float visibleSlotHeight = 0f;
        for (int i = 0; i < visibleMods.size(); i++) {
            Module mod = visibleMods.get(i);
            float modAlpha = visibleAlphas.get(i);
            int modTextAlpha = (int) (255 * modAlpha);
            visibleRows++;

            float rowContentW = rowWidths[i];
            float rowX = rightX - rowContentW;

            // Иконка категории цветом темы.
            renderer.text(FontRegistry.MON, rowX + 8f, curY + 19f, 28f, mod.category.getIcon(),
                    ColorUtil.replAlpha(themeColor, modTextAlpha));

            // Имя модуля (ширина строки рассчитана под контент — клип не нужен).
            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            renderer.text(f, rowX + 30f, curY + 18.5f, FONT_SIZE, mod.getDisplayName(), nameColor, -0.1f);

            float slotHeight = (float) visibleSlots.get(i).get();
            curY += slotHeight;
            visibleSlotHeight += slotHeight;
        }

        // Уехавшие строки дорисовывают высоту.
        for (Map.Entry<String, Animation2> e : slotAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = modules.stream().anyMatch(m -> m.name.equals(key));
            if (!stillActive) {
                float sh = (float) e.getValue().get();
                if (sh > 0.5f) visibleSlotHeight += sh;
            }
        }

        // Bounds = полная ширина панели (rightX - x), stepped-ступеньки
        // слева — часть дизайна; хитбокс и снапы работают по полной ширине.
        boundsW = rectW;
        boundsH = Math.max(ROW_BOX_HEIGHT, totalRowsHeight(rowHeights));
    }

    private static float totalRowsHeight(float[] heights) {
        float h = 0f;
        for (float v : heights) if (v > 0.5f) h += v;
        return h;
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return boundsW;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        FontObject f = FontRegistry.SF_SEMI != null ? FontRegistry.SF_SEMI : font;
        if (f == null) return boundsW;
        float maxNameW = 0f;
        for (Module mod : Vesence.get.manager.getModules()) {
            if (!mod.enable || mod.hiddenFromGui) continue;
            if (!categoryAllowed(mod)) continue;
            float nameW = renderer.measureText(f, mod.getDisplayName(), FONT_SIZE, -0.1f).width;
            if (nameW > maxNameW) maxNameW = nameW;
        }
        float contentW = Math.max(110f, maxNameW + 34f);
        return contentW + PADDING_H + 6f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        float totalSlotHeight = 0f;
        for (Map.Entry<String, Animation2> e : slotAnims.entrySet()) {
            totalSlotHeight += (float) e.getValue().get();
        }
        return Math.max(ROW_BOX_HEIGHT, totalSlotHeight);
    }
}
