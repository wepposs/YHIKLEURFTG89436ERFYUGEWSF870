package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import vesence.Vesence;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.impl.misc.Assist;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.KeyUtil;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontObject;
import org.lwjgl.glfw.GLFW;
import vesence.utils.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class ModernAssistElement extends HudElement {

    private static final float PANEL_SIZE = 80;
    private static final float PANEL_GAP = 8;
    private static final float PANEL_PADDING = 4;
    private static final float FONT_SIZE = 30;
    private static final int WHITE_COLOR = 0xFFFFFFFF;

    public ModernAssistElement() {
        super("Hud Binds Assist", 10f, 40f);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        Hud hud = Vesence.get.manager.get(Hud.class);
        if (hud != null && !hud.isElementEnabled(name)) return;

        Assist assist = Vesence.get.manager.get(Assist.class);
        boolean preview = mc.currentScreen instanceof ChatScreen;
        if ((assist == null || !assist.enable) && !preview) return;

        List<BoundItem> boundItems = assist != null && assist.enable
                ? getBoundItemsFromAssist(mc, assist)
                : new ArrayList<>();
        if (boundItems.isEmpty()) {
            if (!preview) return;
            boundItems.add(new BoundItem(new ItemStack(Items.WIND_CHARGE), "KEY", 0));
        }

        float curX = x;
        float elemScale = getScale();

        for (BoundItem item : boundItems) {
            float pw = panelWidth(renderer, item) + 10;
            int panelBg = ColorUtil.replAlpha(ColorUtil.BLACK, 155);
            renderer.blur(curX, y, pw, 45, 15, 12, 1f);
            renderer.rect(curX, y, pw, 45, 12, panelBg);
            renderer.rect(curX + 43, y + 5, pw - 48, 35, 3,8,8, 3, ColorUtil.replAlpha(-1, (int) (12 * alphaAnim.get())));
            float guiScale = (float) vesence.utils.other.Mathf.getScaleFactor();

            float panelCenterX = curX + 20;
            float panelCenterY = y + 21;
            float scaledCenterX = this.x + (panelCenterX - this.x) * elemScale;
            float scaledCenterY = this.y + (panelCenterY - this.y) * elemScale;
            float centerX = scaledCenterX / guiScale + 1;
            float centerY = scaledCenterY / guiScale + 1;
            float itemScale = 0.8f * elemScale;

            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().translate(centerX, centerY);
            ctx.getMatrices().scale(itemScale, itemScale);
            ctx.drawItem(item.stack, -8, -8);
            ctx.getMatrices().popMatrix();

            float bindW = renderer.measureText(FontRegistry.SF_MEDIUM, item.bindKey, FONT_SIZE).width;
            String countStr = String.valueOf(item.totalCount);
            float countTextH = renderer.measureText(FontRegistry.SF_MEDIUM, countStr, FONT_SIZE).height;
            float countX = curX + bindW + 78;
            float countY = y + PANEL_SIZE / 2f - 10.5f;
            renderer.text(FontRegistry.SF_MEDIUM, countX, countY, FONT_SIZE + 5, ColorFormat.color(ColorUtil.theme(255)) + countStr + ColorFormat.reset() + "x", WHITE_COLOR);

            float bindTextH = renderer.measureText(FontRegistry.SF_MEDIUM, item.bindKey, FONT_SIZE).height;
            float bindX = curX + 54;
            float bindY = y + PANEL_SIZE / 2f - 10.5f;
            renderer.text(FontRegistry.SF_MEDIUM, bindX, bindY, FONT_SIZE, item.bindKey, WHITE_COLOR);
            renderer.rect(curX + bindW + 65, y + 14, 1, 18, ColorUtil.replAlpha(-1, 25));

            curX += pw + PANEL_GAP;
        }
    }

    private float panelWidth(Renderer2D renderer, BoundItem item) {
        float bindW = renderer.measureText(FontRegistry.SF_MEDIUM, item.bindKey, FONT_SIZE).width + 10;
        float countW = renderer.measureText(FontRegistry.SF_MEDIUM, String.valueOf(item.totalCount), FONT_SIZE).width + 37;
        float leftW = Math.max(34f, 6f);
        return leftW + 8f + bindW + 4 + countW;
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return PANEL_SIZE;

        Assist assist = Vesence.get.manager.get(Assist.class);
        if (assist == null || !assist.enable) return PANEL_SIZE;

        List<BoundItem> boundItems = getBoundItemsFromAssist(mc, assist);
        if (boundItems.isEmpty()) return PANEL_SIZE;

        float total = 0f;
        for (BoundItem item : boundItems) {
            total += panelWidth(renderer, item) + PANEL_GAP;
        }
        return Math.max(0f, total - PANEL_GAP);
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return 40;
    }

    private List<BoundItem> getBoundItemsFromAssist(MinecraftClient mc, Assist assist) {
        List<BoundItem> items = new ArrayList<>();

        if (assist.mode.is("FunTime")) {
            addBoundItem(items, mc, assist.windChargeBind, Items.WIND_CHARGE, null);
            addBoundItem(items, mc, assist.ftDisorientationBind, Items.ENDER_EYE, "дезориентация");
            addBoundItem(items, mc, assist.ftTrapBind, Items.NETHERITE_SCRAP, "трапка");
            addBoundItem(items, mc, assist.ftDustBind, Items.SUGAR, "явная");
            addBoundItem(items, mc, assist.ftCrossbowBind, Items.CROSSBOW, null);
            addBoundItem(items, mc, assist.ftPlastBind, Items.DRIED_KELP, "пласт");
            addBoundItem(items, mc, assist.ftAuraBind, Items.PHANTOM_MEMBRANE, "божья");
            addBoundItem(items, mc, assist.ftSnowballBind, Items.SNOWBALL, "снежок");
            addBoundItem(items, mc, assist.ftSmerchBind, Items.FIRE_CHARGE, "смерч");
        } else if (assist.mode.is("HolyWorld")) {
            addBoundItem(items, mc, assist.windChargeBind, Items.WIND_CHARGE, null);
            addBoundItem(items, mc, assist.hwExplosiveTrapBind, Items.PRISMARINE_SHARD, null);
            addBoundItem(items, mc, assist.hwStanBind, Items.NETHER_STAR, null);
            addBoundItem(items, mc, assist.hwExplosiveThingBind, Items.FIRE_CHARGE, null);
            addBoundItem(items, mc, assist.hwTrapBind, Items.POPPED_CHORUS_FRUIT, null);
            addBoundItem(items, mc, assist.hwSnowballBind, Items.SNOWBALL, null);
        }

        return items;
    }

    private void addBoundItem(List<BoundItem> items, MinecraftClient mc, BindSettings bind, Item item, String nameSubstring) {
        if (bind.key == -1) return;

        if (mc.player.getItemCooldownManager().isCoolingDown(new ItemStack(item))) return;

        int slot = findItemSlot(mc, nameSubstring, item);
        if (slot == -1) return;

        ItemStack stack = mc.player.getInventory().getStack(slot);
        if (stack.isEmpty()) return;

        int totalCount = countItemsInInventory(mc, nameSubstring, item);
        if (totalCount == 0) return;

        String bindKey = bind.label();

        items.add(new BoundItem(stack, bindKey, totalCount));
    }

    private int findItemSlot(MinecraftClient mc, String nameSubstring, Item fallbackItem) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    return i;
                }
            }

            if (fallbackItem != null && stack.isOf(fallbackItem)) {
                return i;
            }
        }
        return -1;
    }

    private int countItemsInInventory(MinecraftClient mc, String nameSubstring, Item fallbackItem) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;

            boolean matches = false;
            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    matches = true;
                }
            }

            if (!matches && fallbackItem != null && stack.isOf(fallbackItem)) {
                matches = true;
            }

            if (matches) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\u00a7.", "").toLowerCase(Locale.ROOT);
    }

    private String decodeMojibake(String value) {
        try {
            return new String(value.getBytes(java.nio.charset.Charset.forName("windows-1251")), java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    private String getKeyName(int keyCode) {
        if (keyCode == -1) return "";
        switch (keyCode) {
            case GLFW.GLFW_MOUSE_BUTTON_1: return "LMB";
            case GLFW.GLFW_MOUSE_BUTTON_2: return "RMB";
            case GLFW.GLFW_MOUSE_BUTTON_3: return "MMB";
            default: return KeyUtil.getKey(keyCode);
        }
    }

    private static class BoundItem {
        final ItemStack stack;
        final String bindKey;
        final int totalCount;

        BoundItem(ItemStack stack, String bindKey, int totalCount) {
            this.stack = stack;
            this.bindKey = bindKey;
            this.totalCount = totalCount;
        }
    }
}
