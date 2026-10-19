package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
   private Vec3d savedPos;
   private float savedYaw, savedPitch;
   private Vec3d cameraPos;
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

      float deltaYaw = MathHelper.wrapDegrees(mc.player.getYaw() - savedYaw);
      float deltaPitch = mc.player.getPitch() - savedPitch;
      cameraYaw += deltaYaw;
      cameraPitch = MathHelper.clamp(cameraPitch + deltaPitch, -90, 90);

      mc.player.setYaw(savedYaw);
      mc.player.setPitch(savedPitch);
      mc.player.setPosition(savedPos.x, savedPos.y, savedPos.z);
      mc.player.setVelocity(Vec3d.ZERO);
      mc.player.noClip = true;
      mc.player.fallDistance = 0;

      double s = speed.get();
      float yawRad = (float) Math.toRadians(cameraYaw);
      int forward = 0, sideways = 0;
      if (mc.options.forwardKey.isPressed()) forward++;
      if (mc.options.backKey.isPressed()) forward--;
      if (mc.options.leftKey.isPressed()) sideways++;
      if (mc.options.rightKey.isPressed()) sideways--;
      double targetVertical = 0;
      if (mc.options.jumpKey.isPressed()) targetVertical = 1;
      if (mc.options.sneakKey.isPressed()) targetVertical = -1;

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
      savedPos = mc.player.getEntityPos();
      savedYaw = mc.player.getYaw();
      savedPitch = mc.player.getPitch();
      cameraPos = savedPos;
      cameraYaw = savedYaw;
      cameraPitch = savedPitch;
      prevNoClip = mc.player.noClip;
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
         mc.player.noClip = prevNoClip;
         mc.player.setPosition(savedPos.x, savedPos.y, savedPos.z);
         mc.player.setYaw(savedYaw);
         mc.player.setPitch(savedPitch);
         mc.player.setVelocity(Vec3d.ZERO);
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
