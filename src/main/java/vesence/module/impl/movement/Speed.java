package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.player.FlyExempt;
import vesence.utils.player.MoveUtil;

@IModule(
   name = "Speed",
   description = "Speeds up player movement in different ways",
   category = Category.MOVEMENT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class Speed extends Module {
   public static ModeSetting mode = new ModeSetting("Mode", "Ares-Entity", "Ares-Entity", "Grim-Entity", "GrimGlide", "RW TEST");

   private final SliderSetting rwBoost = new SliderSetting("Boost (RW)", 0.0, 0.0, 0.12, 0.005);
   private final BooleanSetting autoJump = new BooleanSetting("Auto Jump (RW)", true);
   private final BooleanSetting rwDebug = new BooleanSetting("Debug (RW)", false);
   private final BooleanSetting flyExempt = new BooleanSetting("Fly Exempt (RW)", true);

   private int setbackCooldown;
   private int setbackCount;
   private int debugTickCounter;

   /*
    * Jump key state: we press it ourselves, restore on release.
    */
   private boolean holdingJump;
   private boolean playerJumpWasPressed;

   private Vec3 lastBoostVelocity;
   private double lastBoostAmount;

   public Speed() {
      this.addSettings(new Setting[]{mode, rwBoost, autoJump, rwDebug, flyExempt});
   }

   @Override
   public void onEnable() {
      super.onEnable();
      if (flyExempt.get()) {
         FlyExempt.sendNow();
      }
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (!this.enable) return;
      if (mc.player == null || mc.level == null) return;

      if (mode.is("Grim-Entity")) {
          double finalSpeed = 6.0E-4F;

          Entity nearest = null;
          double bestSq = Double.MAX_VALUE;
          double maxRangeSq = 0.2F;

          for (Entity ent : mc.level.entitiesForRendering()) {
             if (ent != mc.player) {

                double dx = ent.getX() - mc.player.getX();
                double dz = ent.getZ() - mc.player.getZ();
                double sq = dx * dx + dz * dz;
                if (sq <= maxRangeSq && sq < bestSq) {
                   bestSq = sq;
                   nearest = ent;
                }
             }
          }

           if (nearest != null) {
              double[] dir = this.getDirectionToPoint(new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ()), new Vec3(nearest.getX(), nearest.getY(), nearest.getZ()), finalSpeed);
              mc.player.push(dir[0], 0.0, dir[1]);
           }
        }

        if (mode.is("GrimGlide")) {
            if (mc.player.onGround() && mc.options.keyJump.isDown()) {
                double yaw = Math.toRadians(mc.player.getYRot());
                double xt = -Math.sin(yaw);
                double zt = Math.cos(yaw);
                Vec3 vel = mc.player.getDeltaMovement();
                mc.player.setDeltaMovement(vel.add(xt * 0.4, 0.42, zt * 0.4));
            } else if (!mc.player.onGround() && !mc.player.isFallFlying()) {
                Vec3 vel = mc.player.getDeltaMovement();
                double yaw = Math.toRadians(mc.player.getYRot());
                double xt = -Math.sin(yaw);
                double zt = Math.cos(yaw);
                Vec3 add = new Vec3(xt, 0, zt).scale(0.02);
                if (mc.player.input.hasForwardImpulse() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
                   mc.player.setDeltaMovement(vel.add(add.x, 0, add.z));
                }
                if (mc.player.getDeltaMovement().y > 0) {
                   mc.player.setDeltaMovement(mc.player.getDeltaMovement().add(0, 0.002, 0));
                }
            }
        }

      if (mode.is("RW TEST")) {
          this.tickRw();
          this.debugRw();
      } else {
          this.releaseJump();
      }
   }

   /*
    * RW TEST:
    *  - Auto Jump: we HOLD the jump key, exactly like a player holding
    *    space. Vanilla processes it in the correct tick order (sprint
    *    momentum, friction, jump impulse) — the server predicts the
    *    same numbers, nothing to flag.
    *  - Boost: optional velocity add, default 0 (measured: the
    *    anticheat flags even 0.005 without fly; with fly granted
    *    by the server (/tfly) any boost is accepted).
    */
   private void tickRw() {
      if (flyExempt.get()) {
          FlyExempt.tick();
      }

      if (setbackCooldown > 0) {
          setbackCooldown--;
          this.releaseJump();
          return;
      }

      if (!MoveUtil.isMoving() || mc.player.isShiftKeyDown()) {
          this.releaseJump();
          return;
      }

      if (autoJump.get()
              && !mc.player.isInWater()
              && !mc.player.isInLava()
              && !mc.player.onClimbable()) {
          this.holdJump();
      } else {
          this.releaseJump();
      }

      double boost = rwBoost.get();
      if (boost > 0.0D && !mc.player.onGround()) {
          double yaw = Math.toRadians(MoveUtil.getdir());
          double xt = -Math.sin(yaw);
          double zt = Math.cos(yaw);
          if (MoveUtil.getdir() == -1.0F) {
             xt = 0.0;
             zt = 0.0;
          }

          mc.player.addDeltaMovement(new Vec3(xt * boost, 0.0D, zt * boost));

          lastBoostVelocity = mc.player.getDeltaMovement();
          lastBoostAmount = boost;
      }
   }

   private void holdJump() {
      if (!holdingJump) {
         holdingJump = true;
         playerJumpWasPressed = mc.options.keyJump.isDown();
      }
      mc.options.keyJump.setDown(true);
   }

   private void releaseJump() {
      if (holdingJump) {
         holdingJump = false;
         mc.options.keyJump.setDown(playerJumpWasPressed);
      }
   }

   private void debugRw() {
      if (!rwDebug.get()) return;

      debugTickCounter++;
      if (debugTickCounter < 20) return;
      debugTickCounter = 0;

      Vec3 v = mc.player.getDeltaMovement();
      this.debugMessage(String.format(
              "RW | h=%.3f y=%.3f ground=%s cooldown=%d setbacks=%d boost=%.3f",
              Math.sqrt(v.x * v.x + v.z * v.z),
              v.y,
              mc.player.onGround(),
              setbackCooldown,
              setbackCount,
              rwBoost.get()
      ));
   }

   @EventInit
   public void onPacket(EventPacket e) {
      if (!this.enable || !mode.is("RW TEST")) return;
      if (mc.player == null) return;

      if (!e.isSend() && e.getPacket() instanceof ClientboundPlayerPositionPacket) {
          setbackCooldown = 10;
          setbackCount++;

          if (rwDebug.get()) {
             Vec3 lv = lastBoostVelocity;
             String dump = lv == null
                     ? "no boost"
                     : String.format("h=%.3f y=%.3f (boost=%.3f)",
                             Math.sqrt(lv.x * lv.x + lv.z * lv.z), lv.y, lastBoostAmount);
             this.debugMessage("SETBACK #" + setbackCount + " after: " + dump);
          }
      }
   }

   private void debugMessage(String message) {
      if (mc.player == null) return;
      try {
          mc.player.sendSystemMessage(
                  net.minecraft.network.chat.Component.literal("[Speed] " + message)
          );
      } catch (Throwable ignored) {
      }
   }

   @Override
   public void onDisable() {
      this.releaseJump();
      setbackCooldown = 0;
      super.onDisable();
   }

   private double[] getDirectionToPoint(Vec3 from, Vec3 to, double spd) {
      double dx = to.x - from.x;
      double dz = to.z - from.z;
      double len = Math.sqrt(dx * dx + dz * dz);
      return len == 0.0 ? new double[]{0.0, 0.0} : new double[]{dx / len * spd, dz / len * spd};
   }
}
