package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2fStack;
import vesence.mixin.ItemCooldownManagerAccessor;
import vesence.mixin.ItemCooldownManagerEntryAccessor;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class ModernCooldownsElement extends HudElement {

    private static final float PANEL_PAD   = 6f;
    private static final float COL_GAP     = 6f;
    private static final float HEADER_H    = 38f;
    private static final float HEADER_GAP  = 6f;
    private static final float ICON_COL_W  = 35;
    private static final float LINE_HEIGHT = 23;
    private static final float COL_PAD_V   = 5f;

    private static final float MIN_WIDTH   = 215;
    private static final float TITLE_SIZE  = 33f;
    private static final float NAME_SIZE   = 30.5f;
    private static final float ROW_ICON    = 16;

    private static final int   PILL_ALPHA  = 12;
    private static final String TITLE = "Cooldowns";

    private static final class RowState {
        String name, value;
        ItemStack stack;
        boolean active;
        final Animation2 anim = new Animation2();
        final Animation2 slot = new Animation2();
        RowState() { anim.set(0.0); slot.set(0.0); }
    }

    private record CooldownEntry(String key, String displayName, ItemStack stack, float progress, int remainingTicks) {}

    private final Map<String, RowState> rows = new LinkedHashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = MIN_WIDTH, boundsH = HEADER_H + PANEL_PAD * 2f;

    public ModernCooldownsElement() {
        super("Cooldowns", 10f, 320f);
        visibilityAnim.set(0.0);
        widthAnim.set(MIN_WIDTH);
    }

    @Override
    public void render(Renderer2D r, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;

        for (RowState st : rows.values()) st.active = false;
        for (CooldownEntry entry : collectCooldowns(mc)) {
            RowState st = rows.computeIfAbsent(entry.key(), k -> new RowState());
            st.name = entry.displayName();
            st.value = formatCooldown(entry.progress(), entry.remainingTicks());
            st.stack = entry.stack();
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
        TtfFonts.ICONS.drawStringWithGlow(r, "T", iconColX + ICON_COL_W / 2f - 9.5f, headerTop + HEADER_H / 2f - 9, 20,
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
            float baseY = rowTop + LINE_HEIGHT / 2f + NAME_SIZE * 0.33f - 2.5f;

            drawItemIcon(ctx, st.stack, iconColX + (ICON_COL_W - ROW_ICON) / 2f, rowTop + (LINE_HEIGHT - ROW_ICON) / 2f, ROW_ICON, rowAlpha);
            r.text(FontRegistry.SF_MEDIUM, nameColX + 10, baseY, NAME_SIZE, st.name,
                    ColorUtil.replAlpha(-1, (int) (255 * rowAlpha)), -0.1f);
            r.textCenter(FontRegistry.SF_SEMI, valueColX + valueColW / 2f + 0.5f, baseY, NAME_SIZE, st.value,
                    ColorUtil.replAlpha(theme, (int) (255 * rowAlpha)));

            rowTop += (float) st.slot.get();
        }

        boundsW = panelW;
        boundsH = panelH;
    }

    private void drawItemIcon(DrawContext ctx, ItemStack stack, float px, float py, float size, float alpha) {
        if (stack == null || stack.isEmpty() || alpha <= 0.01f) return;
        float elemScale = getScale();
        float guiScale = (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
        float sx = this.x + (px - this.x) * elemScale;
        float sy = this.y + (py - this.y) * elemScale;
        float drawSize = size * elemScale;

        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        float cx = (sx + drawSize / 2f) / guiScale;
        float cy = (sy + drawSize / 2f) / guiScale;
        m.translate(cx, cy);
        float sc = (drawSize / guiScale) / 16f * alpha;
        m.scale(sc, sc);
        m.translate(-8f, -8f);
        ctx.drawItem(stack, 0, 0);
        m.popMatrix();
    }

    private List<CooldownEntry> collectCooldowns(MinecraftClient mc) {
        if (mc.player == null) return List.of();
        List<CooldownEntry> result = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>();

        ItemCooldownManager cdm = mc.player.getItemCooldownManager();

        Map<Identifier, ?> entriesMap = null;
        int currentTick = 0;
        try {
            ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) cdm;
            entriesMap = accessor.getEntries();
            currentTick = accessor.getTick();
        } catch (ClassCastException ignored) {}

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (seen.contains(item)) continue;

            float progress = cdm.getCooldownProgress(stack, 0.0f);
            if (progress <= 0f) continue;

            seen.add(item);
            int remainingTicks = entriesMap != null ? getRemainingTicks(stack, entriesMap, currentTick) : 0;
            result.add(new CooldownEntry(item.toString(), stack.getName().getString(), stack.copy(), progress, remainingTicks));
        }

        ItemStack offHand = mc.player.getOffHandStack();
        if (!offHand.isEmpty()) {
            Item item = offHand.getItem();
            if (!seen.contains(item)) {
                float progress = cdm.getCooldownProgress(offHand, 0.0f);
                if (progress > 0f) {
                    int remainingTicks = entriesMap != null ? getRemainingTicks(offHand, entriesMap, currentTick) : 0;
                    result.add(new CooldownEntry(item.toString(), offHand.getName().getString(), offHand.copy(), progress, remainingTicks));
                }
            }
        }
        return result;
    }

    private int getRemainingTicks(ItemStack stack, Map<Identifier, ?> entries, int currentTick) {
        try {
            Identifier itemId = stack.getItem().getRegistryEntry().getKey().map(k -> k.getValue()).orElse(null);
            if (itemId == null) return 0;
            Object entry = entries.get(itemId);
            if (entry instanceof ItemCooldownManagerEntryAccessor entryAcc) {
                return entryAcc.invokeEndTick() - currentTick;
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String formatCooldown(float progress, int remainingTicks) {
        if (remainingTicks > 0) {
            int totalSec = remainingTicks / 20;
            int min = totalSec / 60;
            int sec = totalSec % 60;
            if (min > 0) return min + "\u043C " + String.format("%02d", sec) + "\u0441";
            return sec + "\u0441";
        }
        return String.format("%.0f%%", progress * 100);
    }

    @Override
    public float getEffectiveWidth(Renderer2D r, FontObject font) {
        return (float) widthAnim.get();
    }

    @Override
    public float getWidth(Renderer2D r, FontObject font) {
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D r, FontObject font) {
        return boundsH;
    }
}
