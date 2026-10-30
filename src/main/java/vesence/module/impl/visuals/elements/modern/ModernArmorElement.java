package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix3x2fStack;
import vesence.module.impl.misc.ClickGui;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Armor Hud: порт {@code ArmorSmartRenderer} из privatedlc (Excellent 1.16.5),
 * горизонтальная компоновка. Один ряд слотов: хотбар-слот (выбранный предмет
 * в руке) — шлем — нагрудник — штаны — ботинки. Под каждым слотом полоса
 * прочности (для брони) с цветовой шкалой износа; анимация заполнения и
 * превью-сет незерита в редакторе (чат открыт). В modern-дизайне vesence:
 * чёрная карточка с тенью, SF_SEMI, градиент полосы прочности.
 */
@Environment(EnvType.CLIENT)
public class ModernArmorElement extends HudElement {

    private static final float SLOT_SIZE   = 34f;
    private static final float SLOT_GAP    = 6f;
    private static final float BAR_H       = 5f;
    private static final float BAR_GAP     = 3f;
    private static final float CARD_PAD    = 9f;
    private static final float CARD_RADIUS = 10f;
    private static final float ITEM_SIZE   = 22f;

    private static final int SLOTS = 5; // хотбар + 4 брони

    private static final int[] DAMAGE_COLORS = {
            Renderer2D.ColorUtil.getColor(80, 255, 80),
            Renderer2D.ColorUtil.getColor(160, 255, 80),
            Renderer2D.ColorUtil.getColor(255, 230, 60),
            Renderer2D.ColorUtil.getColor(255, 160, 50),
            Renderer2D.ColorUtil.getColor(255, 80, 60),
    };

    private final Animation2[] durabilityAnims = new Animation2[SLOTS];
    private final Animation2 visibilityAnim = new Animation2();

    public ModernArmorElement() {
        super("Armor Hud", 320f, 560f);
        for (int i = 0; i < SLOTS; i++) {
            durabilityAnims[i] = new Animation2();
            durabilityAnims[i].set(1.0);
        }
        visibilityAnim.set(0.0);
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Слоты по порядку: выбранный предмет хотбара, затем броня (шлем→ботинки). */
    private List<ItemStack> collectSlots(Minecraft mc, boolean preview) {
        List<ItemStack> stacks = new ArrayList<>();
        if (mc.player == null) return stacks;

        // Хотбар-слот: предмет в основной руке.
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() && preview) {
            held = new ItemStack(Items.NETHERITE_SWORD);
        }
        stacks.add(held);

        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty() && preview) {
                stack = previewStackFor(slot);
            }
            stacks.add(stack);
        }
        return stacks;
    }

    private static ItemStack previewStackFor(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> new ItemStack(Items.NETHERITE_HELMET);
            case CHEST -> new ItemStack(Items.NETHERITE_CHESTPLATE);
            case LEGS -> new ItemStack(Items.NETHERITE_LEGGINGS);
            case FEET -> new ItemStack(Items.NETHERITE_BOOTS);
            default -> ItemStack.EMPTY;
        };
    }

    private static float durabilityPct(ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxDamage() <= 0) return 1f;
        return Math.max(0f, (stack.getMaxDamage() - stack.getDamageValue()) / (float) stack.getMaxDamage());
    }

    private static int durabilityColor(float pct) {
        int idx = (int) ((1f - pct) * (DAMAGE_COLORS.length - 1));
        idx = Math.max(0, Math.min(DAMAGE_COLORS.length - 1, idx));
        return DAMAGE_COLORS[idx];
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

        Minecraft mc = Minecraft.getInstance();
        boolean isChatOpen = mc.gui.screen() instanceof ChatScreen;

        List<ItemStack> slots = collectSlots(mc, isChatOpen);
        boolean hasAny = slots.stream().anyMatch(s -> !s.isEmpty());
        boolean shouldShow = isChatOpen || hasAny;

        visibilityAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        float cardW = SLOTS * SLOT_SIZE + (SLOTS - 1) * SLOT_GAP + CARD_PAD * 2f;
        float cardH = SLOT_SIZE + BAR_H + BAR_GAP + CARD_PAD * 2f;

        // ---- Карточка ----
        if (isBlurEnabled()) {
            renderer.blur(x, y, cardW, cardH, ClickGui.blurStrengthHud.get().floatValue(), CARD_RADIUS, globalAlpha);
        }
        renderer.shadow(x, y, cardW, cardH, CARD_RADIUS, 5f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, (int) (45 * globalAlpha)));
        renderer.rect(x, y, cardW, cardH, CARD_RADIUS,
                ClickGui.getBackgroundColor(ClickGui.getHudAlpha() * globalAlpha));

        float rowY = y + CARD_PAD;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            float slotX = x + CARD_PAD + i * (SLOT_SIZE + SLOT_GAP);

            // Слот-подложка.
            renderer.rect(slotX, rowY, SLOT_SIZE, SLOT_SIZE, 6f,
                    ColorUtil.replAlpha(-1, (int) (18 * globalAlpha)));

            // Полоса прочности под слотом (только для брони, слот 0 — хотбар).
            float pct = i == 0 ? 1f : durabilityPct(stack);
            durabilityAnims[i].update();
            durabilityAnims[i].run(stack.isEmpty() || i == 0 ? 0.0 : pct, 0.25, Easings.QUAD_OUT);
            float animPct = (float) durabilityAnims[i].get();

            float barX = slotX;
            float barY = rowY + SLOT_SIZE + BAR_GAP;
            renderer.rect(barX, barY, SLOT_SIZE, BAR_H, 2.5f, ColorUtil.replAlpha(-1, (int) (30 * globalAlpha)));
            if (animPct > 0.005f) {
                int durColor = durabilityColor(animPct);
                int durDark = ColorUtil.multDark(durColor, 0.55f);
                renderer.gradient(barX, barY, SLOT_SIZE * animPct, BAR_H, 2.5f, durDark, durColor, durColor, durDark);
            }

            // Предмет по центру слота.
            drawItemIcon(ctx, stack,
                    slotX + (SLOT_SIZE - ITEM_SIZE) / 2f,
                    rowY + (SLOT_SIZE - ITEM_SIZE) / 2f,
                    ITEM_SIZE, globalAlpha);
        }
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return SLOTS * SLOT_SIZE + (SLOTS - 1) * SLOT_GAP + CARD_PAD * 2f;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return SLOT_SIZE + BAR_H + BAR_GAP + CARD_PAD * 2f;
    }
}
