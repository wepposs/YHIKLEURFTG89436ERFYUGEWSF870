package vesence.module.impl.combat.auraComponent.rotationComponent.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import org.joml.Vector2f;
import vesence.renderengine.utils.MathHelper;
import vesence.utils.other.IMinecraft;

@Environment(EnvType.CLIENT)
public class Rotation implements IMinecraft {
   public float yaw;
   public float pitch;

   public Rotation(Entity entity) {
      this.yaw = entity.getYaw();
      this.pitch = entity.getPitch();
   }

   public Rotation(float yawN, float pitchN) {
      this.yaw = yawN;
      this.pitch = pitchN;
   }

   public float getDelta(Rotation target) {
      float yawDelta = MathHelper.wrapDegrees(target.yaw - this.yaw);
      float pitchDelta = target.pitch - this.pitch;
      return (float)Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));
   }

   public double getDeltaDouble(Rotation target) {
      double yawDelta = MathHelper.wrapDegrees(target.yaw - this.yaw);
      double pitchDelta = MathHelper.wrapDegrees(target.pitch - this.pitch);
      return Math.hypot(yawDelta, pitchDelta);
   }

   public static Vector2f camera() {
      return new Vector2f(cameraYaw(), cameraPitch());
   }

   public static float cameraYaw() {
      return MathHelper.wrapDegrees(mc.gameRenderer.getCamera().getYaw() + (mc.gameRenderer.getCamera().isThirdPerson() ? 180 : 0));
   }

   public static float cameraPitch() {
      return (mc.gameRenderer.getCamera().isThirdPerson() ? -1 : 1) * mc.gameRenderer.getCamera().getPitch();
   }
}
