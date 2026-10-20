package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventMotion;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.StringSetting;

@IModule(name = "AutoFix", description = "Автоматически чинит предметы", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class AutoFix extends Module {

    public ModeSetting mode = new ModeSetting("Режим", "Команда", "Команда", "Зелье");
    public StringSetting name = new StringSetting("Команда для починки", "/fix all");
    public BindSettings bind = new BindSettings("Кнопка", -1);
    public SliderSetting delay = new SliderSetting("Задержка", 50, 0, 500, 1);

    private long lastUseTime = 0;
    private long lastThrowTime = 0;

    public AutoFix() {
        this.addSettings(new Setting[]{mode, name, bind, delay});
    }

    @EventInit
    public void onMotion(EventMotion e) {
        if (!mode.is("Зелье")) return;
        if (mc.gui.screen() != null) return;
        if (!bind.isDown()) return;
        if (!isOnGroundSafe()) return;

        ItemStack fixItem = findFixItem();
        if (fixItem.isEmpty()) return;
        int xpSlot = findXpBottleHotbar();
        int xpSlotInv = findXpBottleInventory();
        if (xpSlot == -1 && xpSlotInv == -1) return;

        e.setPitch(90.0F);
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (mode.is("Команда")) {
            if (System.currentTimeMillis() - lastUseTime >= 1000 && needsFix(mc.player.getMainHandItem())) {
                mc.player.connection.sendChat(name.get());
                lastUseTime = System.currentTimeMillis();
            }
        }

        if (mode.is("Зелье")) {
            if (mc.gui.screen() != null) return;
            if (!bind.isDown()) return;

            ItemStack fixItem = findFixItem();
            if (!fixItem.isEmpty() && isOnGroundSafe()) {
                if (System.currentTimeMillis() - lastThrowTime >= delay.get().longValue()) {
                    int xpSlot = findXpBottleHotbar();
                    if (xpSlot != -1) {
                        mc.player.getInventory().setSelectedSlot(xpSlot);
                        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                        lastThrowTime = System.currentTimeMillis();
                    } else {
                        int xpSlotInv = findXpBottleInventory();
                        if (xpSlotInv != -1) {
                            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, xpSlotInv, 0, ContainerInput.PICKUP, mc.player);
                            lastThrowTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }
    }

    private boolean isOnGroundSafe() {
        return mc.player.onGround() || mc.player.onClimbable() || mc.player.isPassenger();
    }

    private int findXpBottleHotbar() {
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).is(Items.EXPERIENCE_BOTTLE)) return i;
        }
        return -1;
    }

    private int findXpBottleInventory() {
        for (int i = 9; i < 36; ++i) {
            if (mc.player.getInventory().getItem(i).is(Items.EXPERIENCE_BOTTLE)) return i;
        }
        return -1;
    }

    private boolean needsFix(ItemStack item) {
        return item.isDamageableItem() && (item.getMaxDamage() - item.getDamageValue()) <= 3;
    }

    private ItemStack findFixItem() {
        for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
            ItemStack stack = mc.player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            if (stack.isDamageableItem() && stack.getDamageValue() > 0 && hasMending(stack)) return stack;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.isDamageableItem() && stack.getDamageValue() > 0 && hasMending(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private boolean hasMending(ItemStack stack) {
        return stack.getEnchantments().keySet().stream()
                .anyMatch(e -> e.unwrapKey().map(k -> k.identifier().toString().contains("mending")).orElse(false));
    }

    private static AutoFix instance;

    public static AutoFix getInstance() {
        return instance;
    }
}
