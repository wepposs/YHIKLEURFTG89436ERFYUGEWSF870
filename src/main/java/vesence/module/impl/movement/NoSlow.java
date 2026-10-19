package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.SlowWalkingEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.utils.other.TimerUtil;

@IModule(
   name = "NoSlow",
   description = "Убирает замедление при использовании предметов",
   category = Category.MOVEMENT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoSlow extends Module {

   public static ModeSetting mode = new ModeSetting("Режим", "Grim", "Grim", "Grim Tick", "Grim Swap", "SpookyTime");

   private float ticks = 0.0F;
   private final TimerUtil timerUtil = new TimerUtil();

   public NoSlow() {
      this.addSettings(new Setting[]{mode});
   }

   @EventInit
   public void onTick(ClientTickEvent event) {
      if (mc.player != null) {
         if (mc.player.isUsingItem()) { this.ticks++; } else { this.ticks = 0.0F; }
      }
   }

   @EventInit
   public void onSlowWalking(SlowWalkingEvent event) {
      if (mc.player == null) return;

      switch (mode.get()) {
         case "Grim" -> {
            if (mc.player.getActiveHand() == Hand.MAIN_HAND) {
               mc.interactionManager.interactItem(mc.player, Hand.OFF_HAND);
            } else {
               mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            }
            event.cancel();
         }
         case "Grim Tick" -> {
            if (mc.player.isUsingItem() && !mc.player.hasVehicle() && this.ticks >= 1.2F) {
               event.cancel();
               this.ticks = 0.0F;
            }
         }
         case "Grim Swap" -> {
            if (mc.player.isUsingItem() && !mc.player.hasVehicle()) {
               mc.getNetworkHandler().sendPacket(new net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket(
                       mc.player.getInventory().getSelectedSlot()
               ));
               event.cancel();
            }
         }
         case "SpookyTime" -> {
            if (mc.player.isUsingItem() && !mc.player.hasVehicle() && this.ticks >= 1.3F) {
               event.cancel();
               this.ticks = 0.26F;
            }
         }
      }
   }

   public static NoSlow getInstance() {
      return (NoSlow) vesence.Vesence.get.manager.getModule(NoSlow.class);
   }
}
