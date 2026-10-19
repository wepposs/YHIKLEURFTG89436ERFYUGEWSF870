package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import vesence.event.Event;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@Environment(EnvType.CLIENT)
public class EventRender3D extends Event {
   private final MatrixStack matrixStack;
   private final float tickDelta;
   private final Matrix4f projectionMatrix;
   private final Quaternionf cameraRotation;
   private final Vec3d cameraPos;

   public EventRender3D(MatrixStack matrixStack, float tickDelta) {
      this(matrixStack, tickDelta, new Matrix4f(), new Quaternionf(), Vec3d.ZERO);
   }

   public EventRender3D(MatrixStack matrixStack, float tickDelta, Matrix4f projectionMatrix, Camera camera) {
      this(matrixStack, tickDelta, projectionMatrix, camera.getRotation(), camera.getCameraPos());
   }

   private EventRender3D(MatrixStack matrixStack, float tickDelta, Matrix4f projectionMatrix, Quaternionf cameraRotation, Vec3d cameraPos) {
      this.matrixStack = matrixStack;
      this.tickDelta = tickDelta;
      this.projectionMatrix = new Matrix4f(projectionMatrix);
      this.cameraRotation = new Quaternionf(cameraRotation);
      this.cameraPos = cameraPos;
   }

   public MatrixStack getMatrixStack() {
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

   public Vec3d getCameraPos() {
      return this.cameraPos;
   }
}
