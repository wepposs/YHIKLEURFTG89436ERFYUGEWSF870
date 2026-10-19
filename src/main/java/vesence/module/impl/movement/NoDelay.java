package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ExperienceBottleItem;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.mixin.LivingEntityAccessor;
import vesence.mixin.MinecraftClientAccessor;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;

@IModule(name = "No Delay", description = "Повзоляет делать разные действия без задержек", category = Category.MOVEMENT, bind = -1)
@Environment(EnvType.CLIENT)
public class NoDelay extends Module {
    public static BooleanSetting jump = new BooleanSetting("Прыжок", true);
    public static BooleanSetting bubbles = new BooleanSetting("Пузырьки", true);
    public static BooleanSetting blocks = new BooleanSetting("Блоки", true);

    public NoDelay() {
        this.addSettings(new Setting[]{jump, bubbles, blocks});
    }

    @EventInit
    public void onTick(EventUpdate e) {
        if (!this.enable) return;
        if (mc.player == null) return;

        MinecraftClientAccessor mcAccessor = (MinecraftClientAccessor)(Object) mc;

        if (jump.get()) {
            LivingEntityAccessor livingAccessor = (LivingEntityAccessor)(Object) mc.player;
            livingAccessor.setJumpingCooldown(0);
        }

        if (bubbles.get()) {
            net.minecraft.item.ItemStack main = mc.player.getMainHandStack();
            net.minecraft.item.ItemStack off = mc.player.getOffHandStack();
            if (main.getItem() instanceof ExperienceBottleItem || off.getItem() instanceof ExperienceBottleItem) {
                mcAccessor.setItemUseCooldown(0);
            }
        }

        if (blocks.get()) {
            net.minecraft.item.ItemStack main = mc.player.getMainHandStack();
            net.minecraft.item.ItemStack off = mc.player.getOffHandStack();
            if (main.getItem() instanceof BlockItem || off.getItem() instanceof BlockItem) {
                mcAccessor.setItemUseCooldown(0);
            }
        }
    }
}
