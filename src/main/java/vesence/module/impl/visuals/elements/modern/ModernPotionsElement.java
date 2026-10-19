package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix3x2fStack;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ModernPotionsElement extends HudElement {

    private static final float PANEL_PAD   = 6f;
    private static final float COL_GAP     = 6f;
    private static final float HEADER_H    = 38f;
    private static final float HEADER_GAP  = 6f;
    private static final float ICON_COL_W  = 35;
    private static final float LINE_HEIGHT = 23;
    private static final float COL_PAD_V   = 5f;
    private static final float RADIUS      = 8f;

    private static final float MIN_WIDTH   = 190f;
    private static final float TITLE_SIZE  = 33f;
    private static final float NAME_SIZE   = 30.5f;
    private static final float ROW_ICON    = 16;

    private static final int   PILL_ALPHA  = 12;

    private static final String TITLE = "Effects";
    private static final String HEADER_GLYPH = "B";

    private static final class RowState {
        String name, value;
        MobEffectInstance inst;
        boolean active;
        final Animation2 anim = new Animation2();
        final Animation2 slot = new Animation2();
        RowState() { anim.set(0.0); slot.set(0.0); }
    }

    private final Map<String, RowState> rows = new LinkedHashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = MIN_WIDTH, boundsH = HEADER_H + PANEL_PAD * 2f;

    public ModernPotionsElement() {
        super("Potions List", 10f, 200f);
        visibilityAnim.set(0.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, GuiGraphics ctx) {
        Minecraft mc = Minecraft.getInstance();
        boolean chatOpen = mc.screen instanceof ChatScreen;

        for (RowState st : rows.values()) st.active = false;
        for (MobEffectInstance inst : sortedEffects(mc)) {
            RowState st = rows.computeIfAbsent(effectName(inst), k -> new RowState());
            st.inst = inst;
            st.name = effectName(inst) + amplifier(inst);
            st.value = formatDuration(inst);
            st.active = true;
        }

        float totalSlotHeight = 0f, maxNameW = 0f, maxValueW = 0f;
        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, RowState> e : rows.entrySet()) {
            RowState st = e.getValue();
            st.anim.update();
            st.slot.update();
            st.anim.run(st.active ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT, true);
            st.slot.run(st.active ? LINE_HEIGHT : 0.0, 0.15, Easings.CUBIC_OUT, true);
            totalSlotHeight += st.slot.get();
            if (st.active) {
                float nameW  = r.measureText(FontRegistry.SF_MEDIUM, st.name, NAME_SIZE, -0.1f).width;
                float valueW = r.measureText(FontRegistry.SF_SEMI, st.value, NAME_SIZE).width;
                if (nameW > maxNameW) maxNameW = nameW;
                if (valueW > maxValueW) maxValueW = valueW;
            }
            if (!st.active && st.anim.get() < 0.005 && st.slot.get() < 0.5) dead.add(e.getKey());
        }
        for (String k : dead) rows.remove(k);

        boolean hasAny = !rows.isEmpty();
        boolean shouldShow = chatOpen || hasAny;
        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT, true);
        float alpha = (float) visibilityAnim.get();
        if (alpha < 0.005f) return;

        int theme = Renderer2D.ColorUtil.getClientColor();

        float nameColW  = maxNameW  + 24f;
        float valueColW = maxValueW + 24f;

        float titleW = r.measureText(FontRegistry.SF_SEMI, TITLE, TITLE_SIZE, -0.15f).width;
        float headerPillMin = 14f + titleW + 40f;
        float rightBlockW = Math.max(headerPillMin, nameColW + COL_GAP + valueColW);
        if (rightBlockW > nameColW + COL_GAP + valueColW) {
            nameColW = rightBlockW - COL_GAP - valueColW;
        }

        float panelTarget = Math.max(MIN_WIDTH,
                PANEL_PAD * 2f + ICON_COL_W + COL_GAP + nameColW + COL_GAP + valueColW);
        widthAnim.run(panelTarget, 0.2, Easings.CUBIC_OUT, true);
        float panelW = (float) widthAnim.get();

        float x0 = x + PANEL_PAD;
        float iconColX  = x0;
        float valueColX = x + panelW - PANEL_PAD - valueColW;
        float nameColX  = iconColX + ICON_COL_W + COL_GAP;
        float nameColW2 = valueColX - COL_GAP - nameColX;

        float colsH = totalSlotHeight < 0.5f ? 0f : (totalSlotHeight + COL_PAD_V * 2f);
        float panelH = PANEL_PAD + HEADER_H + (colsH > 0.5f ? HEADER_GAP + colsH : 0f) + PANEL_PAD;

        r.blur(x, y, panelW, panelH, 15, 12, alpha);
        r.rect(x, y, panelW, panelH, 12, ColorUtil.replAlpha(ColorUtil.BLACK, (int) (155 * alpha)));

        float headerTop = y + PANEL_PAD;
        TtfFonts.RELAKE.drawStringWithGlow(r, "f", iconColX + ICON_COL_W / 2f - 12, headerTop + HEADER_H / 2f - 11f, 24,
                ColorUtil.replAlpha(theme, (int) (230 * alpha)));

        float headerPillX = nameColX;
        float headerPillW = valueColX + valueColW - headerPillX + 1;
        r.rect(headerPillX, headerTop, headerPillW, HEADER_H, 3, 6, hasAny ? 3 : 6, 3, ColorUtil.replAlpha(-1, (int) (PILL_ALPHA * alpha)));
        r.text(FontRegistry.SF_SEMI, headerPillX + 12, headerTop + HEADER_H / 2f + 7.5f, TITLE_SIZE, TITLE,
                ColorUtil.replAlpha(-1, (int) (255 * alpha)), -0.15f);
        r.textRight(FontRegistry.SF_BOLD, headerPillX + headerPillW - 12, headerTop + HEADER_H / 2f + 6, 30, ":::",
                ColorUtil.replAlpha(-1, (int) (75 * alpha)), 1);

        if (colsH < 0.5f) {
            boundsW = panelW;
            boundsH = panelH;
            return;
        }

        float colsTop = headerTop + HEADER_H + HEADER_GAP;
        int colColor = ColorUtil.replAlpha(-1, (int) (PILL_ALPHA * alpha));
        r.rect(iconColX,  colsTop, ICON_COL_W, colsH, 3, 3, 3, 6, colColor);
        r.rect(nameColX,  colsTop, nameColW2,  colsH, 3, 3, 3, 3, colColor);
        r.rect(valueColX, colsTop, valueColW,  colsH, 3, 3, 6, 3, colColor);

        float rowTop = colsTop + COL_PAD_V;
        for (RowState st : rows.values()) {
            float rowAnim = (float) st.anim.get();
            if (rowAnim < 0.01f) continue;

            float rowAlpha = rowAnim * alpha;
            float baseY = rowTop + LINE_HEIGHT / 2f + NAME_SIZE * 0.33f - 4;

            drawEffectSprite(ctx, st.inst, iconColX + (ICON_COL_W - ROW_ICON) / 2f, rowTop + (LINE_HEIGHT - ROW_ICON) / 2f, ROW_ICON, rowAlpha);
            r.text(FontRegistry.SF_MEDIUM, nameColX + 10, baseY, NAME_SIZE, st.name,
                    ColorUtil.replAlpha(-1, (int) (255 * rowAlpha)), -0.1f);
            r.textCenter(FontRegistry.SF_SEMI, valueColX + valueColW / 2f + 0.5f, baseY, NAME_SIZE, st.value,
                    ColorUtil.replAlpha(theme, (int) (255 * rowAlpha)));

            rowTop += (float) st.slot.get();
        }

        boundsW = panelW;
        boundsH = panelH;
    }

    private void drawEffectSprite(GuiGraphics ctx, MobEffectInstance inst, float px, float py, float size, float alpha) {
        if (inst == null || alpha <= 0.01f) return;
        try {
            ResourceLocation effId = BuiltInRegistries.STATUS_EFFECT.getId(inst.getEffectType().value());
            if (effId == null) return;
            ResourceLocation spriteId = ResourceLocation.of(effId.getNamespace(), "mob_effect/" + effId.getPath());

            float elemScale = getScale();
            float guiScale = (float) Minecraft.getInstance().getWindow().getScaleFactor();
            float sx = this.x + (px - this.x) * elemScale;
            float sy = this.y + (py - this.y) * elemScale;
            float drawSize = size * elemScale;

            Matrix3x2fStack m = ctx.getMatrices();
            m.pushMatrix();
            m.translate(sx / guiScale, sy / guiScale);
            float sc = (drawSize / guiScale) / 18f;
            m.scale(sc, sc);
            ctx.drawGuiTexture(RenderPipelines.GUI_TEXTURED, spriteId, 0, 0, 18, 18, alpha);
            m.popMatrix();
        } catch (Exception ignored) {}
    }

    private String effectName(MobEffectInstance inst) {
        return inst.getEffectType().value().getName().getString();
    }

    private String formatDuration(MobEffectInstance inst) {
        if (inst.isInfinite()) return "\u221E";
        int totalSec = inst.getDuration() / 20;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        if (min > 0) return min + "\u043C " + String.format("%02d", sec) + "\u0441";
        return sec + "\u0441";
    }

    private String amplifier(MobEffectInstance inst) {
        int amp = inst.getAmplifier();
        return amp <= 0 ? "" : " " + (amp + 1);
    }

    private List<MobEffectInstance> sortedEffects(Minecraft mc) {
        if (mc.player == null) return List.of();
        Collection<MobEffectInstance> raw = mc.player.getStatusEffects();
        List<MobEffectInstance> beneficial = new ArrayList<>();
        List<MobEffectInstance> neutral = new ArrayList<>();
        List<MobEffectInstance> harmful = new ArrayList<>();
        for (MobEffectInstance e : raw) {
            switch (e.getEffectType().value().getCategory()) {
                case BENEFICIAL -> beneficial.add(e);
                case HARMFUL    -> harmful.add(e);
                default         -> neutral.add(e);
            }
        }
        List<MobEffectInstance> result = new ArrayList<>();
        result.addAll(beneficial);
        result.addAll(neutral);
        result.addAll(harmful);
        return result;
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) { return (float) widthAnim.get(); }

    @Override
    public float getWidth(Renderer2D r, FontObject font) { return boundsW; }

    @Override
    public float getHeight(Renderer2D r, FontObject font) {
        float total = 0f;
        boolean hasAny = false;
        for (RowState st : rows.values()) {
            float sh = (float) st.slot.get();
            total += sh;
            if (sh > 0.5f) hasAny = true;
        }
        float colsH = (!hasAny && total < 0.5f) ? 0f : (total + COL_PAD_V * 2f);
        return PANEL_PAD + HEADER_H + (colsH > 0.5f ? HEADER_GAP + colsH : 0f) + PANEL_PAD;
    }
}
