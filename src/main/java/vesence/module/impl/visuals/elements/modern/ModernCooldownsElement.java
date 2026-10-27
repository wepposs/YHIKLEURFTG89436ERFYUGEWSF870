package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;
import vesence.mixin.ItemCooldownManagerAccessor;
import vesence.mixin.ItemCooldownManagerEntryAccessor;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cooldowns: компоновка Default-версии — карточка-заголовок «Cooldowns» и
 * выезжающие строки (иконка предмета, название, разделитель, остаток справа).
 * Дизайн modern: чёрные карточки с тенью, светящаяся TTF-иконка, SF_SEMI,
 * значение цветом темы.
 */
@Environment(EnvType.CLIENT)
public class ModernCooldownsElement extends HudElement {

    private static final float PADDING_H        = 13f;
    private static final float PADDING_V        = 12f;
    private static final float FONT_SIZE        = 28f;
    private static final float HEADER_FONT_SIZE = 27f;
    private static final float LINE_HEIGHT      = 29f;
    private static final float HEADER_HEIGHT    = 17f;
    private static final float HEADER_BOX_HEIGHT = 33f;
    private static final float ROW_BOX_HEIGHT = 25f;
    private static final float ROW_RADIUS = 8f;
    private static final float ROW_TOP_OFFSET = 4f;
    private static final float ROW_ICON_SIZE = 18f;
    private static final float ICON_GAP         = 6f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private final Map<String, Animation2> itemAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim      = new Animation2();
    private float boundsW = 125.0f + PADDING_H * 2.0f;
    private float boundsH = HEADER_BOX_HEIGHT;

    public ModernCooldownsElement() {
        super("Cooldowns", 10f, 320f);
        visibilityAnim.set(0.0);
        widthAnim.set(125.0 + PADDING_H * 2.0);
    }

    private record CooldownEntry(String key, String displayName, ItemStack stack, float progress, int remainingTicks) {}

    private List<CooldownEntry> collectCooldowns(Minecraft mc) {
        if (mc.player == null) return List.of();
        List<CooldownEntry> result = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>();

        ItemCooldowns cdm = mc.player.getCooldowns();

        Map<Identifier, ?> entriesMap = null;
        int currentTick = 0;
        try {
            ItemCooldownManagerAccessor accessor = (ItemCooldownManagerAccessor) cdm;
            entriesMap = accessor.getEntries();
            currentTick = accessor.getTick();
        } catch (ClassCastException ignored) {}

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();
            if (seen.contains(item)) continue;

            float progress = cdm.getCooldownPercent(stack, 0.0f);
            if (progress <= 0f) continue;

            seen.add(item);

            int remainingTicks = 0;
            if (entriesMap != null) {
                remainingTicks = getRemainingTicks(stack, entriesMap, currentTick);
            }

            result.add(new CooldownEntry(item.toString(), stack.getHoverName().getString(), stack.copy(), progress, remainingTicks));
        }

        ItemStack offHand = mc.player.getOffhandItem();
        if (!offHand.isEmpty()) {
            Item item = offHand.getItem();
            if (!seen.contains(item)) {
                float progress = cdm.getCooldownPercent(offHand, 0.0f);
                if (progress > 0f) {
                    int remainingTicks = 0;
                    if (entriesMap != null) {
                        remainingTicks = getRemainingTicks(offHand, entriesMap, currentTick);
                    }
                    result.add(new CooldownEntry(item.toString(), offHand.getHoverName().getString(), offHand.copy(), progress, remainingTicks));
                }
            }
        }
        return result;
    }

    private int getRemainingTicks(ItemStack stack, Map<Identifier, ?> entries, int currentTick) {
        try {
            Identifier itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
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
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        List<CooldownEntry> entries = collectCooldowns(mc);

        for (CooldownEntry entry : entries) {
            Animation2 anim = itemAnims.computeIfAbsent(entry.key(), k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            anim.update();
            anim.run(1.0, 0.15, Easings.CUBIC_OUT);

            Animation2 slot = slotAnims.computeIfAbsent(entry.key(), k -> {
                Animation2 a = new Animation2();
                a.set(0.0);
                return a;
            });
            slot.update();
            slot.run(LINE_HEIGHT, 0.15, Easings.CUBIC_OUT);
        }

        List<String> dead = new ArrayList<>();
        for (Map.Entry<String, Animation2> e : itemAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = entries.stream().anyMatch(en -> en.key().equals(key));
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
            itemAnims.remove(key);
            slotAnims.remove(key);
        }

        boolean hasAny     = !entries.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;

        visibilityAnim.update();
        widthAnim.update();

        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float maxLineW = 0f;
        float maxValueW = 0f;
        boolean hasLongValue = false;
        for (CooldownEntry entry : entries) {
            String name = entry.displayName();
            String dur  = formatCooldown(entry.progress(), entry.remainingTicks());
            float nameW = renderer.measureText(f, name, FONT_SIZE, -0.1f).width;
            float durW  = renderer.measureText(f, dur, FONT_SIZE, -0.1f).width;
            float lineW = 30f + nameW + 18f + Math.max(durW + 2f, 26f) + 14f;
            if (lineW > maxLineW) maxLineW = lineW;
            if (durW > maxValueW) maxValueW = durW;
            if (dur.length() > 1) hasLongValue = true;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(f, "Cooldowns", HEADER_FONT_SIZE + 4, -0.15f).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(120, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        // ---- Карточка-заголовок ----
        drawHudPanel(renderer, x, y, rectW, HEADER_BOX_HEIGHT, globalAlpha);

        float curY = y + PADDING_V + 9;
        TtfFonts.ICONS.drawStringWithGlow(renderer, "T", x + 8f, y + HEADER_BOX_HEIGHT / 2f - 12f, 24,
                ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha)));
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (20 * globalAlpha)));
        renderer.text(f, x + 48, curY + 2.5f, 31, "Cooldowns", ColorUtil.replAlpha(-1, globalAlpha), -0.15f);

        curY += HEADER_HEIGHT;
        float visibleSlotHeight = 0f;
        for (CooldownEntry entry : entries) {
            Animation2 anim = itemAnims.get(entry.key());
            Animation2 slot = slotAnims.get(entry.key());
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String modName = entry.displayName();
            String valueStr = formatCooldown(entry.progress(), entry.remainingTicks());

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int valueColor = ColorUtil.replAlpha(themeColor, modTextAlpha);
            float rowX = x + modAlpha * 15 - 15;
            float textX = rowX + 30;

            renderer.blur(rowX, curY, rectW, ROW_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), ROW_RADIUS, modAlpha);
            renderer.rect(rowX, curY, rectW, ROW_BOX_HEIGHT, ROW_RADIUS,
                    ClickGui.getBackgroundColor((int) (185f * ClickGui.getHudAlpha() * modAlpha)));

            drawItemIcon(ctx, entry.stack(), rowX + 6f, curY + ROW_TOP_OFFSET, ROW_ICON_SIZE, modAlpha);
            renderer.text(f, textX, curY + 18.5f, 28, modName, nameColor, -0.1f);

            float valueAreaRight = rowX + rectW - 7;
            float valueAreaWidth = Math.max(maxValueW + 2f, 26f);
            float valueAreaLeft = valueAreaRight - valueAreaWidth;
            renderer.rect(valueAreaRight - maxValueW - 10, curY + 6, 1, 13, ColorUtil.replAlpha(-1, (int) (20 * modAlpha)));
            if (hasLongValue) {
                float valueCenterX = (valueAreaLeft + valueAreaRight) / 2f;
                renderer.textCenter(f, valueCenterX, curY + 18.5f, 28, valueStr, valueColor, -0.1f);
            } else {
                renderer.textRight(f, valueAreaRight - 1, curY + 18.5f, 28, valueStr, valueColor, -0.1f);
            }

            float slotHeight = (float) slot.get();
            curY += slotHeight;
            visibleSlotHeight += slotHeight;
        }

        for (Map.Entry<String, Animation2> e : slotAnims.entrySet()) {
            String key = e.getKey();
            boolean stillActive = entries.stream().anyMatch(en -> en.key().equals(key));
            if (!stillActive) {
                visibleSlotHeight += (float) e.getValue().get();
            }
        }

        boundsW = rectW;
        boundsH = visibleSlotHeight < 0.5f ? HEADER_BOX_HEIGHT : (y + PADDING_V + 9 + HEADER_HEIGHT + visibleSlotHeight + (ROW_BOX_HEIGHT - LINE_HEIGHT)) - y;
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
        return boundsW;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return boundsH;
    }
}
