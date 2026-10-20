package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import vesence.event.Event;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class EventRender3D extends Event {
   private final PoseStack matrixStack;
   private final float tickDelta;
   private final Matrix4f projectionMatrix;
   private final Quaternionf cameraRotation;
   private final Vec3 cameraPos;

   public EventRender3D(PoseStack matrixStack, float tickDelta) {
      this(matrixStack, tickDelta, new Matrix4f(), new Quaternionf(), Vec3.ZERO);
   }

   public EventRender3D(PoseStack matrixStack, float tickDelta, Matrix4f projectionMatrix, Camera camera) {
      this(matrixStack, tickDelta, projectionMatrix, camera.rotation(), camera.position());
   }

   private EventRender3D(PoseStack matrixStack, float tickDelta, Matrix4f projectionMatrix, Quaternionf cameraRotation, Vec3 cameraPos) {
      this.matrixStack = matrixStack;
      this.tickDelta = tickDelta;
      this.projectionMatrix = new Matrix4f(projectionMatrix);
      this.cameraRotation = new Quaternionf(cameraRotation);
      this.cameraPos = cameraPos;
   }

   public PoseStack getMatrixStack() {
      return this.matrixStack;
   }

   public float getTickDelta() {
      return this.tickDelta;
   }

   public Matrix4f getProjectionMatrix() {
      return new Matrix4f(this.projectionMatrix);
   }

   public Quaternionf getCameraRotation() {
      return new Quaternionf(this.cameraRotation);
   }

   public Vec3 getCameraPos() {
      return this.cameraPos;
   }
}
