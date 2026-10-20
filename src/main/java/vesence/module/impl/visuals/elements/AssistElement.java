package vesence.module.impl.visuals.elements;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import vesence.Vesence;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.impl.misc.Assist;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontObject;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public class AssistElement extends HudElement {

    private static final float PANEL_SIZE = 60;
    private static final float PANEL_GAP = 8;
    private static final float PANEL_PADDING = 4;
    private static final float FONT_SIZE = 18;
    private static final int WHITE_COLOR = 0xFFFFFFFF;

    public AssistElement() {
        super("Hud Binds Assist", 10f, 40f);
    }

    @Override
    public void render(Renderer2D renderer, FontObject font, int screenWidth, int screenHeight, GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Hud hud = Vesence.get.manager.get(Hud.class);
        if (hud != null && !hud.isElementEnabled(name)) return;

        Assist assist = Vesence.get.manager.get(Assist.class);
        boolean preview = mc.gui.screen() instanceof ChatScreen;
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
            drawHudPanel(renderer, curX, y, PANEL_SIZE, PANEL_SIZE, 1f);

            float guiScale = (float) vesence.utils.other.Mathf.getScaleFactor();

            float panelCenterX = curX + 30;
            float panelCenterY = y + 30;
            float scaledCenterX = this.x + (panelCenterX - this.x) * elemScale;
            float scaledCenterY = this.y + (panelCenterY - this.y) * elemScale;
            float centerX = scaledCenterX / guiScale;
            float centerY = scaledCenterY / guiScale;
            float itemScale = 0.85f * elemScale;

            ctx.pose().pushMatrix();
            ctx.pose().translate(centerX, centerY);
            ctx.pose().scale(itemScale, itemScale);
            ctx.item(item.stack, -8, -8);
            ctx.pose().popMatrix();

            String countStr = String.valueOf(item.totalCount);
            float countTextH = renderer.measureText(font, countStr, FONT_SIZE).height;
            float countX = curX + 6;
            float countY = y + 6 + countTextH * 0.7f;
            renderer.text(font, countX, countY, FONT_SIZE, countStr, WHITE_COLOR);

                float bindW = renderer.measureText(font, item.bindKey, FONT_SIZE).width;
                float bindTextH = renderer.measureText(font, item.bindKey, FONT_SIZE).height;
                float bindX = curX + PANEL_SIZE - bindW - 6;
                float bindY = y + PANEL_SIZE - 6 - bindTextH * 0.3f;
                renderer.text(font, bindX, bindY, FONT_SIZE, item.bindKey, WHITE_COLOR);

            curX += PANEL_SIZE + PANEL_GAP;
        }
    }

    @Override
    public float getWidth(Renderer2D renderer, FontObject font) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return PANEL_SIZE;

        Assist assist = Vesence.get.manager.get(Assist.class);
        if (assist == null || !assist.enable) return PANEL_SIZE;

        List<BoundItem> boundItems = getBoundItemsFromAssist(mc, assist);
        if (boundItems.isEmpty()) return PANEL_SIZE;

        return boundItems.size() * PANEL_SIZE + (boundItems.size() - 1) * PANEL_GAP;
    }

    @Override
    public float getHeight(Renderer2D renderer, FontObject font) {
        return PANEL_SIZE;
    }

    private List<BoundItem> getBoundItemsFromAssist(Minecraft mc, Assist assist) {
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

    private void addBoundItem(List<BoundItem> items, Minecraft mc, BindSettings bind, Item item, String nameSubstring) {
        if (bind.key == -1) return;

        if (mc.player.getCooldowns().isOnCooldown(new ItemStack(item))) return;

        int slot = findItemSlot(mc, nameSubstring, item);
        if (slot == -1) return;

        ItemStack stack = mc.player.getInventory().getItem(slot);
        if (stack.isEmpty()) return;

        int totalCount = countItemsInInventory(mc, nameSubstring, item);
        if (totalCount == 0) return;

        String bindKey = bind.label();

        items.add(new BoundItem(stack, bindKey, totalCount));
    }

    private int findItemSlot(Minecraft mc, String nameSubstring, Item fallbackItem) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getHoverName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    return i;
                }
            }

            if (fallbackItem != null && stack.is(fallbackItem)) {
                return i;
            }
        }
        return -1;
    }

    private int countItemsInInventory(Minecraft mc, String nameSubstring, Item fallbackItem) {
        int count = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            boolean matches = false;
            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getHoverName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    matches = true;
                }
            }

            if (!matches && fallbackItem != null && stack.is(fallbackItem)) {
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
        String name = InputConstants.Type.KEYBOARD.getOrCreate(keyCode).getDisplayName().getString();
        if (name != null) {
            return name.toUpperCase();
        }
        switch (keyCode) {
            case InputConstants.KEY_SPACE: return "SPC";
            case InputConstants.KEY_LSHIFT: return "SHIFT";
            case InputConstants.KEY_RSHIFT: return "SHIFT";
            case InputConstants.KEY_LCONTROL: return "CTRL";
            case InputConstants.KEY_RCONTROL: return "CTRL";
            case InputConstants.KEY_LALT: return "ALT";
            case InputConstants.KEY_RALT: return "ALT";
            case InputConstants.MOUSE_BUTTON_LEFT: return "LMB";
            case InputConstants.MOUSE_BUTTON_RIGHT: return "RMB";
            case InputConstants.MOUSE_BUTTON_MIDDLE: return "MMB";
            default: return "KEY" + keyCode;
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
