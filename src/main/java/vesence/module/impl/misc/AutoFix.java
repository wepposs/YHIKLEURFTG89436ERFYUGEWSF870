package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
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
        if (mc.currentScreen != null) return;
        if (!bind.isPressed()) return;
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
            if (System.currentTimeMillis() - lastUseTime >= 1000 && needsFix(mc.player.getMainHandStack())) {
                mc.player.networkHandler.sendChatMessage(name.get());
                lastUseTime = System.currentTimeMillis();
            }
        }

        if (mode.is("Зелье")) {
            if (mc.currentScreen != null) return;
            if (!bind.isPressed()) return;

            ItemStack fixItem = findFixItem();
            if (!fixItem.isEmpty() && isOnGroundSafe()) {
                if (System.currentTimeMillis() - lastThrowTime >= delay.get().longValue()) {
                    int xpSlot = findXpBottleHotbar();
                    if (xpSlot != -1) {
                        mc.player.getInventory().setSelectedSlot(xpSlot);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        lastThrowTime = System.currentTimeMillis();
                    } else {
                        int xpSlotInv = findXpBottleInventory();
                        if (xpSlotInv != -1) {
                            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, xpSlotInv, 0, SlotActionType.PICKUP, mc.player);
                            lastThrowTime = System.currentTimeMillis();
                        }
                    }
                }
            }
        }
    }

    private boolean isOnGroundSafe() {
        return mc.player.isOnGround() || mc.player.isClimbing() || mc.player.hasVehicle();
    }

    private int findXpBottleHotbar() {
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) return i;
        }
        return -1;
    }

    private int findXpBottleInventory() {
        for (int i = 9; i < 36; ++i) {
            if (mc.player.getInventory().getStack(i).isOf(Items.EXPERIENCE_BOTTLE)) return i;
        }
        return -1;
    }

    private boolean needsFix(ItemStack item) {
        return item.isDamageable() && (item.getMaxDamage() - item.getDamage()) <= 3;
    }

    private ItemStack findFixItem() {
        for (net.minecraft.entity.EquipmentSlot slot : net.minecraft.entity.EquipmentSlot.values()) {
            ItemStack stack = mc.player.getEquippedStack(slot);
            if (stack.isEmpty()) continue;
            if (stack.isDamageable() && stack.getDamage() > 0 && hasMending(stack)) return stack;
        }
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.isDamageable() && stack.getDamage() > 0 && hasMending(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private boolean hasMending(ItemStack stack) {
        return stack.getEnchantments().getEnchantments().stream()
                .anyMatch(e -> e.getIdAsString().contains("mending"));
    }

    private static AutoFix instance;

    public static AutoFix getInstance() {
        return instance;
    }
}
