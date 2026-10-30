package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.AnimatedText;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.ttf.TtfFonts;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Item Count: порт {@code ItemCountRenderer} из privatedlc (Excellent 1.16.5).
 * Счётчики выбранных предметов (тотем, жемчуг края, золотые яблоки, обсидиан):
 * иконка + количество, строки выезжают по мере появления предметов в инвентаре.
 * В редакторе (чат открыт) — превью-набор. В modern-дизайне vesence: чёрная
 * карточка-заголовок + строки-карточки, SF_SEMI, количество цветом темы.
 */
@Environment(EnvType.CLIENT)
public class ModernItemCountElement extends HudElement {

    private static final float PADDING_H        = 13f;
    private static final float PADDING_V        = 12f;
    private static final float FONT_SIZE        = 28f;
    private static final float HEADER_FONT_SIZE = 27f;
    private static final float LINE_HEIGHT      = 29f;
    private static final float HEADER_HEIGHT    = 17f;
    private static final float HEADER_BOX_HEIGHT = 33f;
    private static final float ROW_BOX_HEIGHT   = 25f;
    private static final float ROW_RADIUS       = 8f;
    private static final float ROW_TOP_OFFSET   = 4f;
    private static final float ROW_ICON_SIZE    = 18f;
    private static final float ICON_GAP         = 6f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    /** Отслеживаемые предметы (аналог MultiItemSetting из Theme источника). */
    private static final Item[] TRACKED = {
            Items.TOTEM_OF_UNDYING, Items.ENDER_PEARL, Items.GOLDEN_APPLE, Items.OBSIDIAN
    };

    private record TrackedItem(Item item, String key) {}

    private final Map<String, Animation2> itemAnims = new HashMap<>();
    private final Map<String, Animation2> slotAnims = new HashMap<>();
    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();
    private float boundsW = 125f + PADDING_H * 2f;
    private float boundsH = HEADER_BOX_HEIGHT;

    public ModernItemCountElement() {
        super("Item Count", 320f, 560f);
        visibilityAnim.set(0.0);
        widthAnim.set(125f + PADDING_H * 2f);
        this.enabled = false;
    }

    private record CountEntry(String key, ItemStack stack, int count) {}

    private List<CountEntry> collectCounts(Minecraft mc, boolean preview) {
        Map<Item, Integer> counts = new LinkedHashMap<>();
        if (mc.player != null) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                counts.merge(item, stack.getCount(), Integer::sum);
            }
            ItemStack offhand = mc.player.getOffhandItem();
            if (!offhand.isEmpty()) {
                counts.merge(offhand.getItem(), offhand.getCount(), Integer::sum);
            }
        }

        List<CountEntry> result = new ArrayList<>();
        int previewIdx = 0;
        for (Item item : TRACKED) {
            Integer count = counts.get(item);
            if (count != null && count > 0) {
                String key = itemKey(item);
                result.add(new CountEntry(key, new ItemStack(item), count));
            } else if (preview) {
                // Превью-набор в редакторе.
                int previewCount = switch (previewIdx) {
                    case 0 -> 1;
                    case 1 -> 16;
                    case 2 -> 8;
                    default -> 64;
                };
                result.add(new CountEntry(itemKey(item), new ItemStack(item), previewCount));
            }
            previewIdx++;
        }
        return result;
    }

    private static String itemKey(Item item) {
        try {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            return id != null ? id.toString() : item.toString();
        } catch (Exception e) {
            return item.toString();
        }
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
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        if (FontRegistry.SF_SEMI == null) return;
        FontObject f = FontRegistry.SF_SEMI;

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        List<CountEntry> entries = collectCounts(mc, isChatOpen);

        for (CountEntry entry : entries) {
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

        boolean hasAny = !entries.isEmpty();
        boolean shouldShow = isChatOpen || hasAny;

        visibilityAnim.update();
        widthAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);

        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float maxLineW = 0f;
        for (CountEntry entry : entries) {
            String name = entry.stack().getHoverName().getString();
            String cnt = String.valueOf(entry.count());
            float nameW = renderer.measureText(f, name, FONT_SIZE, -0.1f).width;
            float cntW = renderer.measureText(f, cnt, FONT_SIZE, -0.1f).width;
            float lineW = 30f + nameW + 18f + Math.max(cntW + 2f, 26f) + 14f;
            if (lineW > maxLineW) maxLineW = lineW;
        }

        float headerIconW = 20f;
        float headerTextW = renderer.measureText(f, "Item Count", HEADER_FONT_SIZE + 4, -0.15f).width;
        float headerW = headerIconW + ICON_GAP + headerTextW;

        float contentW = Math.max(120, Math.max(maxLineW, headerW));
        float autoRectW = contentW + PADDING_H * 2f + 15;

        widthAnim.run(autoRectW, 0.2, Easings.CUBIC_OUT);
        float rectW = (float) widthAnim.get();

        // ---- Карточка-заголовок ----
        drawHudPanel(renderer, x, y, rectW, HEADER_BOX_HEIGHT, globalAlpha);

        float curY = y + PADDING_V + 9;
        TtfFonts.ESSENCE.drawStringWithGlow(renderer, "y", x + 8f, y + HEADER_BOX_HEIGHT / 2f - 12f, 24,
                ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha)));
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (20 * globalAlpha)));
        renderer.text(f, x + 48, curY + 2.5f, 31, "Item Count", ColorUtil.replAlpha(-1, globalAlpha), -0.15f);

        curY += HEADER_HEIGHT;
        float visibleSlotHeight = 0f;
        for (CountEntry entry : entries) {
            Animation2 anim = itemAnims.get(entry.key());
            Animation2 slot = slotAnims.get(entry.key());
            if (anim == null || slot == null || (anim.get() < 0.01 && slot.get() < 0.5)) continue;

            float modAlpha = (float) (anim.get() * globalAlpha);
            int modTextAlpha = (int) (255 * modAlpha);

            String name = entry.stack().getHoverName().getString();
            String cntStr = String.valueOf(entry.count());

            int nameColor = (modTextAlpha << 24) | (WHITE_COLOR & 0x00FFFFFF);
            int cntColor = ColorUtil.replAlpha(themeColor, modTextAlpha);
            float rowX = x + modAlpha * 15 - 15;
            float textX = rowX + 30;

            renderer.blur(rowX, curY, rectW, ROW_BOX_HEIGHT, ClickGui.blurStrengthHud.get().floatValue(), ROW_RADIUS, modAlpha);
            renderer.rect(rowX, curY, rectW, ROW_BOX_HEIGHT, ROW_RADIUS,
                    ClickGui.getBackgroundColor((int) (185f * ClickGui.getHudAlpha() * modAlpha)));

            drawItemIcon(ctx, entry.stack(), rowX + 6f, curY + ROW_TOP_OFFSET, ROW_ICON_SIZE, modAlpha);
            renderer.text(f, textX, curY + 18.5f, 28, name, nameColor, -0.1f);

            AnimatedText.draw(renderer, f, "ic_" + entry.key(), cntStr,
                    rowX + rectW - 12f, curY + 18.5f, 28, cntColor, AnimatedText.ALIGN_RIGHT);

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
