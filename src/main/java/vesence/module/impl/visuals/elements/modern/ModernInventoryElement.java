package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
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
import vesence.utils.render.ttf.TtfFonts;

/**
 * Inventory Hud: порт {@code InventoryHudRenderer} из privatedlc (Excellent
 * 1.16.5). Карточка «Инвентарь» с сеткой 3x9 основного инвентаря, счётчиком
 * пустых слотов и превью-заполнением в редакторе. В modern-дизайне vesence:
 * чёрная карточка-заголовок + слоты, SF_SEMI, иконка-свечение цветом темы.
 */
@Environment(EnvType.CLIENT)
public class ModernInventoryElement extends HudElement {

    private static final float SLOT_SIZE       = 24f;
    private static final float SLOT_GAP        = 3f;
    private static final float ROWS            = 3f;
    private static final float COLS            = 9f;
    private static final float CARD_PAD        = 9f;
    private static final float CARD_RADIUS     = 10f;
    private static final float HEADER_BOX_HEIGHT = 33f;
    private static final float SLOT_RADIUS     = 5f;
    private static final float ITEM_SIZE       = 18f;
    private static final float COUNT_FONT      = 17f;

    private static final int WHITE_COLOR = 0xFFFFFFFF;

    private final Animation2 visibilityAnim = new Animation2();
    private final Animation2 widthAnim = new Animation2();

    private float boundsW;
    private float boundsH = HEADER_BOX_HEIGHT;

    public ModernInventoryElement() {
        super("Inventory Hud", 640f, 240f);
        visibilityAnim.set(0.0);
        widthAnim.set(gridWidth());
        this.enabled = false;
    }

    private static float gridWidth() {
        return COLS * SLOT_SIZE + (COLS - 1) * SLOT_GAP + CARD_PAD * 2f;
    }

    private static float gridHeight() {
        return ROWS * SLOT_SIZE + (ROWS - 1) * SLOT_GAP;
    }

    private static ItemStack previewStack(int slot) {
        // Превью в редакторе: диагональный паттерн предметов с реальными стека́ми.
        return switch (slot % 9) {
            case 0 -> new ItemStack(Items.DIAMOND, 32);
            case 2 -> new ItemStack(Items.GOLDEN_APPLE, 3);
            case 4 -> new ItemStack(Items.ENDER_PEARL, 16);
            case 6 -> new ItemStack(Items.OBSIDIAN, 64);
            default -> ItemStack.EMPTY;
        };
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

        boolean shouldShow = isChatOpen || (mc.player != null && !mc.player.getInventory().isEmpty());

        visibilityAnim.update();
        visibilityAnim.run(shouldShow ? 1.0 : 0.0, 0.15, Easings.CUBIC_OUT);
        float globalAlpha = (float) visibilityAnim.get();
        if (globalAlpha < 0.005f) return;

        int themeColor = Renderer2D.ColorUtil.getClientColor();

        float rectW = gridWidth();
        float headerH = HEADER_BOX_HEIGHT;
        // Полная высота сеточной карточки: паддинг сверху + сетка + паддинг снизу.
        float gridCardH = gridHeight() + CARD_PAD * 2f;
        float totalH = headerH + 6f + gridCardH;

        // ---- Карточка-заголовок «Инвентарь» ----
        drawHudPanel(renderer, x, y, rectW, headerH, globalAlpha);
        // Иконка-куб инвентаря (изометрический куб, divine_icons «l»).
        TtfFonts.DIVINE_ICONS.drawStringWithGlow(renderer, "l", x + 8f, y + headerH / 2f - 12f, 24,
                ColorUtil.replAlpha(themeColor, (int) (230 * globalAlpha)));
        renderer.rect(x + 38, y + 10.5f, 1, 14, ColorUtil.replAlpha(-1, (int) (20 * globalAlpha)));
        renderer.text(f, x + 48, y + 13f + 9f + 2.5f, 31, "Инвентарь",
                ColorUtil.replAlpha(-1, globalAlpha), -0.15f);

        // Счётчик пустых слотов справа в заголовке.
        int empty = 0;
        if (mc.player != null) {
            for (int i = 9; i < 36; i++) {
                if (mc.player.getInventory().getItem(i).isEmpty()) empty++;
            }
        }
        String emptyStr = String.valueOf(empty);
        float emptyW = renderer.measureText(f, emptyStr, COUNT_FONT, -0.1f).width;
        renderer.textRight(f, x + rectW - 10f, y + headerH / 2f + 7f, COUNT_FONT, emptyStr,
                ColorUtil.replAlpha(themeColor, (int) (255 * globalAlpha)), -0.1f);

        // ---- Карточка сетки ----
        float gridY = y + headerH + 6f;
        if (isBlurEnabled()) {
            renderer.blur(x, gridY, rectW, gridCardH, CARD_RADIUS, globalAlpha);
        }
        renderer.shadow(x, gridY, rectW, gridCardH, CARD_RADIUS, 5f, 1f,
                ColorUtil.replAlpha(ColorUtil.BLACK, (int) (45 * globalAlpha)));
        renderer.rect(x, gridY, rectW, gridCardH, CARD_RADIUS,
                ClickGui.getBackgroundColor(ClickGui.getHudAlpha() * globalAlpha));

        // ---- Слоты 3x9 (основной инвентарь: индексы 9..35) ----
        for (int row = 0; row < (int) ROWS; row++) {
            for (int col = 0; col < (int) COLS; col++) {
                int invIndex = 9 + row * (int) COLS + col;
                ItemStack stack = mc.player != null
                        ? mc.player.getInventory().getItem(invIndex)
                        : ItemStack.EMPTY;
                if (stack.isEmpty() && isChatOpen) {
                    stack = previewStack(invIndex);
                }

                float slotX = x + CARD_PAD + col * (SLOT_SIZE + SLOT_GAP);
                float slotY = gridY + CARD_PAD + row * (SLOT_SIZE + SLOT_GAP);

                renderer.rect(slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS,
                        ColorUtil.replAlpha(-1, (int) (16 * globalAlpha)));
                renderer.rectOutline(slotX, slotY, SLOT_SIZE, SLOT_SIZE, SLOT_RADIUS,
                        ColorUtil.replAlpha(-1, (int) (24 * globalAlpha)), 1f);

                if (!stack.isEmpty()) {
                    drawItemIcon(ctx, stack,
                            slotX + (SLOT_SIZE - ITEM_SIZE) / 2f,
                            slotY + (SLOT_SIZE - ITEM_SIZE) / 2f,
                            ITEM_SIZE, globalAlpha);

                    // Количество предметов в углу слота (только если предметов > 1;
                    // превью-стеки несут свои реальные количества).
                    if (stack.getCount() > 1) {
                        String countStr = String.valueOf(stack.getCount());
                        float cntW = renderer.measureText(f, countStr, COUNT_FONT, -0.1f).width;
                        // Учитываем внутреннее деление размера шрифта в Renderer2D
                        // (эффективный глиф = COUNT_FONT/2): центрируем по слоту.
                        float cntH = renderer.measureText(f, countStr, COUNT_FONT, -0.1f).height;
                        renderer.text(f, slotX + SLOT_SIZE - cntW - 3f,
                                slotY + SLOT_SIZE - 2f, COUNT_FONT, countStr,
                                ColorUtil.replAlpha(-1, (int) (255 * globalAlpha)), -0.1f);
                    }
                }
            }
        }

        boundsW = rectW;
        boundsH = totalH;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        return gridWidth();
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return HEADER_BOX_HEIGHT + 6f + gridHeight() + CARD_PAD * 2f;
    }
}
