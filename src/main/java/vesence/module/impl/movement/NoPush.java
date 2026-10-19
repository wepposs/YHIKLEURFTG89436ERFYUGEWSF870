package vesence.module.impl.movement;

import java.util.Optional;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.world.phys.Vec3;

import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.BooleanSetting;

@IModule(
        name = "NoPush",
        description = "Убирает отталкивание от тех или иных энтити",
        category = Category.MOVEMENT,
        bind = -1
)
@Environment(EnvType.CLIENT)
public class NoPush extends Module {

   public final MultiBooleanSetting modes = new MultiBooleanSetting(
           "Игнорировать",
           new BooleanSetting("Игроков", true),
           new BooleanSetting("Воду", false),
           new BooleanSetting("Блоки", true),
           new BooleanSetting("Удочку", true),
           new BooleanSetting("Взрывы", true)
   );

   private boolean wasNoClip = false;

   /*
    * Взрывной импульс, который сервер отправил игроку.
    * На следующем тике вычитаем именно его из текущей скорости,
    * не ломая обычное движение игрока.
    */
   private Vec3 pendingExplosionKnockback = null;

   public NoPush() {
      this.addSettings(new Setting[]{modes});
   }

   public static NoPush getInstance() {
      return (NoPush) vesence.Vesence.get.manager.getModule(NoPush.class);
   }

   @EventInit
   public void onPacket(EventPacket event) {
      if (mc.player == null) {
         return;
      }

      if (!shouldIgnoreExplosions()) {
         return;
      }

      if (event.isSend()) {
         return;
      }

      if (!(event.getPacket() instanceof ExplosionS2CPacket packet)) {
         return;
      }

      Optional<Vec3> knockback = packet.playerKnockback();

      if (knockback.isPresent()) {
         pendingExplosionKnockback = knockback.get();
      }
   }

   @EventInit
   public void onTick(EventUpdate e) {
      if (mc.player == null) {
         return;
      }

      if (!this.enable) {
         if (wasNoClip) {
            mc.player.noClip = false;
            wasNoClip = false;
         }

         pendingExplosionKnockback = null;
         return;
      }

      if (shouldIgnoreBlocks()) {
         mc.player.noClip = true;
         wasNoClip = true;
      } else if (wasNoClip) {
         mc.player.noClip = false;
         wasNoClip = false;
      }

      /*
       * Убираем только импульс от взрыва.
       * Обычная скорость игрока сохраняется.
       */
      if (shouldIgnoreExplosions() && pendingExplosionKnockback != null) {
         Vec3 current = mc.player.getDeltaMovement();
         Vec3 knockback = pendingExplosionKnockback;

         mc.player.setDeltaMovement(
                 current.x - knockback.x,
                 current.y - knockback.y,
                 current.z - knockback.z
         );

         pendingExplosionKnockback = null;
      }
   }

   public boolean shouldIgnorePlayers() {
      return modes.get("Игроков");
   }

   public boolean shouldIgnoreWater() {
      return modes.get("Воду");
   }

   public boolean shouldIgnoreBlocks() {
      return modes.get("Блоки");
   }

   public boolean shouldIgnoreFishing() {
      return modes.get("Удочку");
   }

   public boolean shouldIgnoreExplosions() {
      return modes.get("Взрывы");
   }

   @Override
   public void onDisable() {
      if (mc.player != null && wasNoClip) {
         mc.player.noClip = false;
         wasNoClip = false;
      }

      pendingExplosionKnockback = null;

      super.onDisable();
   }
}