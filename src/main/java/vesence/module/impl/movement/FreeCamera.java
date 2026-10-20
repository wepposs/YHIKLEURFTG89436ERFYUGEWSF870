package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.SliderSetting;

@IModule(
   name = "FreeCam",
   description = "Позволяет вам осмотреть закрытые помещения",
   category = Category.MOVEMENT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class FreeCamera extends Module {
   private final SliderSetting speed = new SliderSetting("Скорость", 1.0, 0.1, 5.0, 0.05);

   private final double ACCEL = 0.35;
   private final double BRAKE = 0.75;
   private double smoothForward;
   private double smoothSideways;
   private double smoothVertical;
   private Vec3 savedPos;
   private float savedYaw, savedPitch;
   private Vec3 cameraPos;
   private float cameraYaw, cameraPitch;
   private boolean prevNoClip;

   public FreeCamera() {
      this.addSettings(new Setting[]{speed});
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (mc.player == null) return;

      FreecamState.prevPos = cameraPos;
      FreecamState.prevYaw = cameraYaw;
      FreecamState.prevPitch = cameraPitch;

      float deltaYaw = Mth.wrapDegrees(mc.player.getYRot() - savedYaw);
      float deltaPitch = mc.player.getXRot() - savedPitch;
      cameraYaw += deltaYaw;
      cameraPitch = Mth.clamp(cameraPitch + deltaPitch, -90, 90);

      mc.player.setYRot(savedYaw);
      mc.player.setXRot(savedPitch);
      mc.player.setPos(savedPos.x, savedPos.y, savedPos.z);
      mc.player.setDeltaMovement(Vec3.ZERO);
      mc.player.noPhysics = true;
      mc.player.fallDistance = 0;

      double s = speed.get();
      float yawRad = (float) Math.toRadians(cameraYaw);
      int forward = 0, sideways = 0;
      if (mc.options.keyUp.isDown()) forward++;
      if (mc.options.keyDown.isDown()) forward--;
      if (mc.options.keyLeft.isDown()) sideways++;
      if (mc.options.keyRight.isDown()) sideways--;
      double targetVertical = 0;
      if (mc.options.keyJump.isDown()) targetVertical = 1;
      if (mc.options.keyShift.isDown()) targetVertical = -1;

      smoothForward = smoothAxis(smoothForward, forward);
      smoothSideways = smoothAxis(smoothSideways, sideways);
      smoothVertical = smoothAxis(smoothVertical, targetVertical);
      if (Math.abs(smoothForward) < 0.001) smoothForward = 0;
      if (Math.abs(smoothSideways) < 0.001) smoothSideways = 0;
      if (Math.abs(smoothVertical) < 0.001) smoothVertical = 0;

      double mx = (smoothSideways * Math.cos(yawRad) - smoothForward * Math.sin(yawRad)) * s;
      double mz = (smoothForward * Math.cos(yawRad) + smoothSideways * Math.sin(yawRad)) * s;
      double my = smoothVertical * s;

      cameraPos = cameraPos.add(mx, my, mz);

      FreecamState.pos = cameraPos;
      FreecamState.yaw = cameraYaw;
      FreecamState.pitch = cameraPitch;
   }

   @Override
   public void onEnable() {
      if (mc.player == null) return;
      savedPos = mc.player.position();
      savedYaw = mc.player.getYRot();
      savedPitch = mc.player.getXRot();
      cameraPos = savedPos;
      cameraYaw = savedYaw;
      cameraPitch = savedPitch;
      prevNoClip = mc.player.noPhysics;
      FreecamState.prevPos = cameraPos;
      FreecamState.pos = cameraPos;
      FreecamState.prevYaw = cameraYaw;
      FreecamState.yaw = cameraYaw;
      FreecamState.prevPitch = cameraPitch;
      FreecamState.pitch = cameraPitch;
      super.onEnable();
   }

   @Override
   public void onDisable() {
      if (mc.player != null) {
         mc.player.noPhysics = prevNoClip;
         mc.player.setPos(savedPos.x, savedPos.y, savedPos.z);
         mc.player.setYRot(savedYaw);
         mc.player.setXRot(savedPitch);
         mc.player.setDeltaMovement(Vec3.ZERO);
      }
      FreecamState.pos = null;
      super.onDisable();
   }

   private double smoothAxis(double current, double target) {
      if (target == 0) {
         return current + (target - current) * BRAKE;
      } else if (Math.abs(current) < 0.001) {
         return current + (target - current) * ACCEL;
      } else if (target * current > 0) {
         return current + (target - current) * ACCEL;
      } else {
         return current + (target - current) * BRAKE;
      }
   }

   public static FreeCamera getInstance() {
      return (FreeCamera) vesence.Vesence.get.manager.getModule(FreeCamera.class);
   }
}
