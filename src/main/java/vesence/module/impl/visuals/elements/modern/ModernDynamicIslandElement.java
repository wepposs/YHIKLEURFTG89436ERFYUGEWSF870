package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import vesence.module.api.Module;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.other.StopWatch;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

/**
 * Dynamic Island: порт островка из rockstar-client (IiIiiIIII_Class177).
 *
 * <p>Архитектура источника, перенесённая на рендер vesence:
 * <ul>
 *   <li><b>Приоритет статусов</b>: Music (диск в руке) > Module (переключение
 *       модуля, 3 с) > Alert (аура-таргет) > Default (логотип-«точка» + время).
 *       Виден статус с максимальным приоритетом (в источнике — последний
 *       выбранный из списка canShow()).</li>
 *   <li><b>Анимации как в источнике</b>: ширина 500 мс easeOut, радиус 500 мс,
 *       альфа 500 мс, раскрытие 260 мс easeOut.</li>
 *   <li><b>Ховер-раскрытие</b>: наведение 210 мс → островок раскрывается в
 *       панель повыше (125px) с пингом и сигнальными полосками
 *       (пороги 450/300/150/75 мс) — порт I_method_58a034b9.</li>
 *   <li><b>Фон</b>: blur 45 + чёрный squircle 85% (216.75/255), радиус
 *       анимируется 7 → 12 при раскрытии — порт I_method_386424f9.</li>
 *   <li><b>Music</b>: обложка-диск, название, прогресс-бар снизу,
 *       эквалайзер 4 полоски с индивидуальными фазами (Nested1_cb9fdd80).</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public class ModernDynamicIslandElement extends HudElement {

    // Геометрия источника.
    private static final float PILL_H        = 15f;
    private static final float EXPANDED_H    = 125f;
    private static final float MIN_W         = 48f;
    private static final float MUSIC_MAX_W   = 144f;
    private static final float PILL_PAD      = 8f;
    private static final float TEXT_SIZE     = 15f;
    private static final float DISC_SIZE     = 11f;
    private static final float RADIUS_BASE   = 7.0f;
    private static final float RADIUS_EXPAND = RADIUS_BASE + 5.0f;
    private static final float BLUR_STRENGTH = 45f;
    private static final long  HOVER_DELAY_MS = 210L;
    private static final long  MODULE_SHOW_MS = 3000L;
    private static final int[] PING_THRESHOLDS = {450, 300, 150, 75};

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    // Анимации источника: ширина 500, радиус 500, раскрытие 260, альфа 500.
    private final Animation2 widthAnim    = new Animation2();
    private final Animation2 radiusAnim   = new Animation2();
    private final Animation2 expandAnim   = new Animation2();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 pingAnim     = new Animation2();
    // Ховер-задержка 210 мс (I_field_4a = 210L).
    private final StopWatch hoverWatch = new StopWatch();
    private boolean hoverArmed = false;

    // Эквалайзер (Nested1_cb9fdd80): 4 полоски с фазами и высотами.
    private static final float[] EQ_PHASES = {0.0f, 1.9f, 4.2f, 2.7f};
    private static final float[] EQ_SPEEDS = {1.0f, 1.5f, 0.5f, 2.0f};
    private static final float EQ_MAX_H = 7.0f;

    /** Хук переключения модуля: имя + включён/выключен (порт Module.setEnabled). */
    private static volatile String lastToggledModule;
    private static volatile boolean lastToggledState;
    private static final StopWatch moduleWatch = new StopWatch();

    private float boundsW = MIN_W;
    private float boundsH = PILL_H;

    private long musicStartMs = 0L;

    public ModernDynamicIslandElement() {
        super("Dynamic Island", 320f, 7f);
        widthAnim.set(MIN_W);
        radiusAnim.set(RADIUS_BASE);
        expandAnim.set(0.0);
        visibilityAnim.set(0.0);
        pingAnim.set(0.0);
    }

    /** Вызывается из {@link Module#toggle()}. */
    public static void onModuleToggle(String name, boolean enabled) {
        lastToggledModule = name;
        lastToggledState = enabled;
        moduleWatch.reset();
    }

    private enum Mode { MUSIC, MODULE, ALERT, DEFAULT }

    private record IslandState(Mode mode, String title, float progress, ItemStack disc) {}

    private IslandState computeState(Minecraft mc) {
        // 1. Music — диск в руке (JukeboxSong).
        if (mc.player != null) {
            ItemStack main = mc.player.getMainHandItem();
            ItemStack hand = main != null && isMusicDisc(main) ? main : mc.player.getOffhandItem();
            if (hand != null && isMusicDisc(hand)) {
                if (musicStartMs == 0L) musicStartMs = System.currentTimeMillis();
                long elapsed = (System.currentTimeMillis() - musicStartMs) % 10_000L;
                return new IslandState(Mode.MUSIC, discName(hand), elapsed / 10_000f, hand);
            } else {
                musicStartMs = 0L;
            }
        }

        // 2. Module — свежее переключение (3 с).
        if (lastToggledModule != null && !moduleWatch.finished(MODULE_SHOW_MS)) {
            String state = lastToggledState ? "ON" : "OFF";
            return new IslandState(Mode.MODULE, lastToggledModule + " " + state, -1f, null);
        }

        // 3. Alert — таргет ауры (порт IiIiiiiIi_Class190/Class187 alerts).
        var aura = vesence.Vesence.get != null
                ? vesence.Vesence.get.manager.get(vesence.module.impl.combat.AttackAura.class)
                : null;
        if (aura != null && aura.enable && vesence.module.impl.combat.AttackAura.target != null) {
            var target = vesence.module.impl.combat.AttackAura.target;
            String hp = String.valueOf(Math.round(target.getHealth()));
            return new IslandState(Mode.ALERT, target.getName().getString() + " " + hp, -1f, null);
        }

        // 4. Default — время.
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        return new IslandState(Mode.DEFAULT, time, -1f, null);
    }

    private static boolean isMusicDisc(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        try {
            return JukeboxSong.fromStack(stack).isPresent();
        } catch (Exception e) {
            return stack.is(Items.MUSIC_DISC_CAT) || stack.is(Items.MUSIC_DISC_PIGSTEP);
        }
    }

    private static String discName(ItemStack stack) {
        try {
            var holderOpt = JukeboxSong.fromStack(stack);
            if (holderOpt.isPresent()) {
                var desc = holderOpt.get().value().description();
                if (desc != null) {
                    String s = desc.getString();
                    if (s != null && !s.isEmpty()) return s;
                }
            }
        } catch (Exception ignored) {}
        return "Music";
    }

    private static int ping(Minecraft mc) {
        if (mc.player == null || mc.getConnection() == null) return -1;
        PlayerInfo entry = mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return entry == null ? -1 : entry.getLatency();
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        IslandState state = computeState(mc);

        // ---- Ховер-раскрытие (порт: наведение 210 мс -> expand) ----
        float elemScale = getScale();
        float hoverW = boundsW * elemScale;
        float hoverH = boundsH * elemScale;
        Minecraft mouseMc = Minecraft.getInstance();
        double mx = 0, my = 0;
        if (mouseMc.gui.screen() != null) {
            double ratioX = (double) screenWidth / Math.max(1, mouseMc.getWindow().getWidth());
            double ratioY = (double) screenHeight / Math.max(1, mouseMc.getWindow().getHeight());
            mx = mouseMc.mouseHandler.xpos() * ratioX;
            my = mouseMc.mouseHandler.ypos() * ratioY;
        }
        boolean hovered = mx >= x && mx <= x + hoverW && my >= y && my <= y + hoverH;
        if (hovered && !hoverArmed) {
            hoverArmed = true;
            hoverWatch.reset();
        } else if (!hovered) {
            hoverArmed = false;
        }
        boolean shouldExpand = hoverArmed && !hoverWatch.finished(HOVER_DELAY_MS * 1000); // finished(ms)
        // Честная проверка: finished использует миллисекунды.
        shouldExpand = hoverArmed && hoverWatch.isReached(HOVER_DELAY_MS);

        expandAnim.update();
        expandAnim.run(shouldExpand ? 1.0 : 0.0, 0.26, Easings.CUBIC_OUT);
        float expandT = (float) expandAnim.get();

        // ---- Пинг справа появляется при раскрытии (порт ping-блока) ----
        int ping = ping(mc);
        boolean showPing = expandT > 0.01f && ping >= 0;
        pingAnim.update();
        pingAnim.run(showPing ? 1.0 : 0.0, 0.2, Easings.CUBIC_OUT);
        float pingT = (float) pingAnim.get();

        // ---- Геометрия: ширина по контенту, высота раскрывается до 125 ----
        // Ширина текста: measureText уже считает по эффективному размеру
        // (Renderer2D внутри делит size/2) — НЕ делим повторно. Длинные имена
        // модулей усекаем с «…», чтобы влезали в капы ширины.
        float iconW = state.disc() != null ? DISC_SIZE + 5f : 7f;
        float eqW = state.mode() == Mode.MUSIC ? 9f : 0f;
        float maxW = state.mode() == Mode.MUSIC ? MUSIC_MAX_W : 240f;
        float maxTextW = maxW - PILL_PAD * 2f - iconW - eqW;

        String displayText = state.title();
        float textW = renderer.measureText(f, displayText, TEXT_SIZE, -0.05f).width;
        while (textW > maxTextW && displayText.length() > 1) {
            displayText = displayText.substring(0, displayText.length() - 1);
            textW = renderer.measureText(f, displayText + "…", TEXT_SIZE, -0.05f).width;
        }
        if (!displayText.equals(state.title()) && displayText.length() > 1) {
            displayText = displayText.substring(0, displayText.length() - 1) + "…";
            textW = renderer.measureText(f, displayText, TEXT_SIZE, -0.05f).width;
        }

        float targetW;
        if (state.mode() == Mode.MUSIC) {
            targetW = Math.max(MIN_W, Math.min(MUSIC_MAX_W, PILL_PAD * 2f + iconW + textW + eqW + 4f));
        } else {
            targetW = Math.max(MIN_W, PILL_PAD * 2f + iconW + textW);
        }
        float targetH = PILL_H + (EXPANDED_H - PILL_H) * expandT;

        // Анимации источника: ширина 500 мс, радиус 500 мс, альфа 500 мс.
        widthAnim.update();
        widthAnim.run(targetW, 0.5, Easings.CUBIC_OUT);
        radiusAnim.update();
        radiusAnim.run(RADIUS_BASE + (RADIUS_EXPAND - RADIUS_BASE) * expandT, 0.5, Easings.CUBIC_OUT);
        visibilityAnim.update();
        visibilityAnim.run(1.0, 0.5, Easings.CUBIC_OUT);

        float rectW = (float) widthAnim.get();
        float rectH = targetH;
        float radius = (float) radiusAnim.get();
        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        // ---- Фон: blur 45 + чёрный squircle 85% (порт I_method_386424f9) ----
        if (isBlurEnabled()) {
            renderer.blur(x, y, rectW, rectH, BLUR_STRENGTH, radius, globalAlpha);
        }
        renderer.shadow(x, y, rectW, rectH, radius, 6f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, (int) (55 * globalAlpha)));
        renderer.rect(x, y, rectW, rectH, radius,
                ColorUtil.getColor(0, 0, 0, (int) (216.75f * globalAlpha)));

        float contentX = x + PILL_PAD;
        float centerY = y + PILL_H / 2f;

        // ---- Иконка слева ----
        // Все элементы контента центрируются по геометрическому центру таблетки.
        // Текст: Renderer2D делит размер шрифта пополам (эффективный 7.5 при
        // TEXT_SIZE=15); тело глифов = [baseline - 0.81*7.5, baseline - 0.11*7.5],
        // его центр = baseline - 3.45. Чтобы центр тела был в centerY:
        // baseline = centerY + 3.45. (Старый захардкоженный офсет 7.45 был
        // посчитан для TEXT_SIZE=25 и уводил текст ниже центра, точку — выше.)
        float glyphCenterOffset = 0.46f * TEXT_SIZE / 2f;  // = 3.45 при TEXT_SIZE=15

        if (state.disc() != null) {
            drawItemIcon(ctx, state.disc(), contentX, y + (PILL_H - DISC_SIZE) / 2f, DISC_SIZE, globalAlpha);
            contentX += DISC_SIZE + 5f;
        } else if (state.mode() == Mode.MODULE) {
            // Точка-индикатор ON/OFF: центр по центру таблетки.
            int dotColor = state.title().endsWith("ON")
                    ? ColorUtil.getColor(34, 187, 94)
                    : ColorUtil.getColor(239, 68, 68);
            renderer.rect(contentX, centerY - 2.5f, 5f, 5f, 2.5f,
                    ColorUtil.replAlpha(dotColor, (int) (255 * globalAlpha)));
            contentX += 7f;
        } else if (state.mode() == Mode.ALERT) {
            // Малый «бейдж» у алерта (как chips в источнике).
            renderer.rect(contentX, centerY - 2.5f, 5f, 5f, 2.5f,
                    ColorUtil.replAlpha(themeColor, (int) (200 * globalAlpha)));
            contentX += 7f;
        } else {
            // Default: маленький «логотип» — квадратик 5x5 цветом темы (порт logo 8px).
            renderer.rect(contentX, centerY - 2.5f, 5f, 5f, 2.5f,
                    ColorUtil.replAlpha(themeColor, (int) (200 * globalAlpha)));
            contentX += 7f;
        }

        // ---- Текст: базовая линия так, чтобы центр тела глифов = centerY ----
        float textY = centerY + glyphCenterOffset;
        renderer.text(f, contentX, textY, TEXT_SIZE, displayText,
                ColorUtil.replAlpha(-1, (int) (245 * globalAlpha)), -0.05f);

        // ---- Эквалайзер музыки (порт Nested1_cb9fdd80) ----
        if (state.mode() == Mode.MUSIC) {
            float eqX = contentX + textW + 4f;
            float eqY = centerY - EQ_MAX_H / 2f;
            float time = System.currentTimeMillis() / 1000f;
            for (int i = 0; i < 4; i++) {
                float h = 2.5f + 4.5f * (0.5f + 0.5f * (float) Math.sin(time * 4f * EQ_SPEEDS[i] + EQ_PHASES[i]));
                renderer.rect(eqX + i * 2.2f, eqY + (EQ_MAX_H - h) / 2f, 1.6f, h, 0.8f,
                        ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha)));
            }
        }

        // ---- Прогресс музыки: тонкая полоса по низу таблетки (порт Nested1_cb9fd9c0) ----
        if (state.progress() >= 0f) {
            float barY = y + PILL_H - 1.5f;
            renderer.rect(x + 2f, barY, rectW - 4f, 1f, 0.5f,
                    ColorUtil.replAlpha(-1, (int) (64 * globalAlpha)));
            renderer.rect(x + 2f, barY, (rectW - 4f) * state.progress(), 1f, 0.5f,
                    ColorUtil.replAlpha(-1, (int) (150 * globalAlpha)));
        }

        // ---- Пинг-блок справа при раскрытии (порт I_method_58a034b9) ----
        if (pingT > 0.01f) {
            float pingX = x + rectW + 4f + 4f * pingT;
            String pingStr = ping + " ms";
            // Текст пинга: та же центровка по телу глифов (эффективный размер 3.5).
            renderer.text(f, pingX, centerY + 0.46f * 7f / 2f, 7f, pingStr,
                    ColorUtil.replAlpha(-1, (int) (200 * globalAlpha * pingT)));

            // Сигнальные полоски: 4 штуки, высота растёт, цвет по порогам.
            float pingTextW = renderer.measureText(f, pingStr, 7f).width * 0.5f;
            float barsX = pingX + pingTextW + 3f;
            int activeColor = ColorUtil.getColor(34, 187, 94);
            int dimColor = ColorUtil.getColor(-1, 60);
            for (int i = 0; i < PING_THRESHOLDS.length; i++) {
                float bh = 2.5f + i * 1.5f;
                boolean active = ping < PING_THRESHOLDS[i];
                renderer.rect(barsX + i * 2.0f, centerY - bh / 2f, 1.5f, bh, 0.75f,
                        ColorUtil.replAlpha(active ? activeColor : dimColor,
                                (int) (255 * globalAlpha * pingT)));
            }
        }

        boundsW = rectW;
        boundsH = rectH;
    }

    private void drawItemIcon(GuiGraphicsExtractor ctx, ItemStack stack, float px, float py, float size, float alpha) {
        if (stack == null || stack.isEmpty() || alpha <= 0.01f) return;
        float elemScale = getScale();
        float guiScale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        float sx = this.x + (px - this.x) * elemScale;
        float sy = this.y + (py - this.y) * elemScale;
        float drawSize = size * elemScale;

        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        float cx = (sx + drawSize / 2f) / guiScale;
        float cy = (sy + drawSize / 2f) / guiScale;
        m.translate(cx, cy);
        float sc = (drawSize / guiScale) / 16f * alpha;
        m.scale(sc, sc);
        m.translate(-8f, -8f);
        ctx.item(stack, 0, 0);
        m.popMatrix();
    }

    @Override
    public float getEffectiveWidth(Renderer2D renderer, FontObject font) {
        return boundsW;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return boundsW > 0f ? boundsW : MIN_W;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return boundsH > 0f ? boundsH : PILL_H;
    }
}
