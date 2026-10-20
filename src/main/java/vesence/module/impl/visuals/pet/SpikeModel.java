package vesence.module.impl.visuals.pet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import vesence.module.impl.visuals.Immediate;

/**
 * Порт xd.harm.modules.models.spike.SpikeModel — JSON-меш Спайка с клип-
 * анимациями (idle/walk каналы: translation/rotation, slerp) + процедурной
 * анимацией конечностей. Рендер адаптирован под 1.21.11.
 */
public class SpikeModel {

   public static final RenderType LAYER = PetRender.meshLayer(
      "vesence_pet_spike", Identifier.fromNamespaceAndPath("vesence", "textures/pet/spike.png"));
   private static final Identifier MODEL_LOCATION = Identifier.fromNamespaceAndPath("vesence", "pet/spike_mesh.json");
   private static final float MODEL_SCALE = 1.18F;

   private final Minecraft mc = Minecraft.getInstance();
   private final List<ModelNode> nodes = new ArrayList<>();
   private final List<Integer> rootNodes = new ArrayList<>();
   private final List<ModelAnimation> animations = new ArrayList<>();
   private ModelAnimation idleAnimation;
   private ModelAnimation walkAnimation;
   private boolean loaded;
   private boolean failed;
   private float modelYawOffset = 180.0F;
   private float centerX;
   private float centerZ;
   private float minY;
   private float walkBlend;
   private float smoothedYaw;
   private float smoothedHeadYaw;
   private float smoothedHeadPitch;
   private float walkCycleProgress;
   private long lastAnimationTimeNanos;

   public void render(PoseStack matrixStack, Immediate vertexConsumers, float partialTicks, SpikeBrain brain) {
      ensureLoaded();
      if (failed || nodes.isEmpty()) {
         return;
      }

      VertexConsumer buffer = vertexConsumers.getBuffer(LAYER);
      float ageInTicks = mc.player == null ? 0.0F : mc.player.tickCount + partialTicks;
      float deltaSeconds = updateAnimationClock();
      float moveSpeed = brain.getMoveSpeed();
      float interpolatedWalkAmount = Mth.lerp(partialTicks, brain.prevLimbSwingAmount, brain.limbSwingAmount);
      float walkAmount = Math.min(Math.max(interpolatedWalkAmount, moveSpeed * 18.0F), 1.0F);
      float targetWalkBlend = walkAmount > 0.025F ? Math.min(1.0F, walkAmount * 1.35F) : 0.0F;
      walkBlend = smoothApproach(walkBlend, targetWalkBlend, deltaSeconds, 3.6F);
      float idleBlend = 1.0F - walkBlend;

      float bodyYaw = normalizeDegrees(modelYawOffset - brain.getBody());
      smoothedYaw = smoothYaw(smoothedYaw, bodyYaw, deltaSeconds, 10.0F);
      smoothedHeadYaw = smoothApproach(smoothedHeadYaw, brain.getHeadYaw(), deltaSeconds, 9.0F);
      smoothedHeadPitch = smoothApproach(smoothedHeadPitch, brain.getHeadPitch(), deltaSeconds, 9.0F);

      float cycleSpeed = 1.45F + moveSpeed * 58.0F;
      walkCycleProgress += deltaSeconds * cycleSpeed;
      float walkCycle = walkCycleProgress;
      float animationTime = ageInTicks / 20.0F;
      float walkAnimationTime = walkCycleProgress * 0.095F;
      float idleCycle = ageInTicks * 0.06F;
      float idleBob = (float) Math.sin(idleCycle) * 0.0007F + (float) Math.sin(idleCycle * 0.31F + 0.9F) * 0.00045F;
      float walkBob = Math.abs((float) Math.sin(walkCycle * 2.0F)) * 0.0012F;
      float bob = idleBob * idleBlend + walkBob * walkBlend;
      float bodyRoll = ((float) Math.sin(idleCycle * 0.45F) * 0.018F) * idleBlend + ((float) Math.sin(walkCycle * 2.0F) * 0.035F) * walkBlend;
      float bodyPitch = ((float) Math.cos(idleCycle * 0.42F) * 0.026F - 0.018F) * idleBlend
            + ((float) Math.cos(walkCycle * 2.0F) * 0.035F - 0.008F) * walkBlend;
      float sideShift = (float) Math.cos(walkCycle * 2.0F) * 0.00035F * walkBlend;
      float stride = 30.0F * walkBlend;
      float frontLeftPhase = walkCycle;
      float frontRightPhase = walkCycle + (float) Math.PI;
      float rearLeftPhase = walkCycle + (float) Math.PI;
      float rearRightPhase = walkCycle;
      float frontLeftStep = (float) Math.cos(frontLeftPhase);
      float frontRightStep = (float) Math.cos(frontRightPhase);
      float rearLeftStep = (float) Math.cos(rearLeftPhase);
      float rearRightStep = (float) Math.cos(rearRightPhase);
      float frontLeftLift = Math.max(0.0F, (float) Math.sin(frontLeftPhase));
      float frontRightLift = Math.max(0.0F, (float) Math.sin(frontRightPhase));
      float rearLeftLift = Math.max(0.0F, (float) Math.sin(rearLeftPhase));
      float rearRightLift = Math.max(0.0F, (float) Math.sin(rearRightPhase));
      float frontLeftSwing = frontLeftStep * stride;
      float frontRightSwing = frontRightStep * stride;
      float rearLeftSwing = rearLeftStep * stride * 0.86F;
      float rearRightSwing = rearRightStep * stride * 0.86F;
      float frontLeftKnee = frontLeftLift * stride * 0.72F;
      float frontRightKnee = frontRightLift * stride * 0.72F;
      float rearLeftKnee = rearLeftLift * stride * 0.78F;
      float rearRightKnee = rearRightLift * stride * 0.78F;
      float frontLeftFoot = (frontLeftLift * -0.38F + frontLeftStep * 0.12F) * stride;
      float frontRightFoot = (frontRightLift * -0.38F + frontRightStep * 0.12F) * stride;
      float rearLeftFoot = (rearLeftLift * -0.44F + rearLeftStep * 0.1F) * stride;
      float rearRightFoot = (rearRightLift * -0.44F + rearRightStep * 0.1F) * stride;
      float shoulderSway = (float) Math.cos(walkCycle) * 0.08F * walkBlend;
      float hipSway = (float) Math.cos(walkCycle + 0.45F) * 0.06F * walkBlend;
      float spineYaw = (float) Math.sin(walkCycle) * 0.035F * walkBlend;
      float earFlop = ((float) Math.sin(idleCycle * 0.48F) * 2.4F) * idleBlend + ((float) Math.sin(walkCycle + 0.9F) * 4.8F) * walkBlend;
      float tailBaseYaw = ((float) Math.sin(idleCycle * 0.38F) * 2.5F) * idleBlend + ((float) Math.sin(walkCycle * 0.9F) * 7.0F) * walkBlend;
      float tailBaseRoll = 4.0F + ((float) Math.cos(idleCycle * 0.4F) * 0.45F) * idleBlend + ((float) Math.cos(walkCycle * 0.9F) * 1.5F) * walkBlend;
      float tailTipYaw = tailBaseYaw * 1.28F + ((float) Math.sin(idleCycle * 0.62F + 0.45F) * 0.9F) * idleBlend;

      matrixStack.pushPose();
      matrixStack.translate(sideShift, bob - 0.01 * 10.0F, 0.0D);
      matrixStack.rotate(Axis.YP.rotationDegrees(smoothedYaw));
      matrixStack.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);
      matrixStack.translate(-centerX, -minY, -centerZ);
      matrixStack.translate(centerX, 0.0F, centerZ);
      matrixStack.rotate(Axis.ZP.rotationDegrees(bodyRoll));
      matrixStack.rotate(Axis.XP.rotationDegrees(bodyPitch));
      matrixStack.translate(-centerX, 0.0F, -centerZ);

      AnimationPose pose = new AnimationPose(idleCycle, animationTime, walkAnimationTime, walkBlend, idleBlend, smoothedHeadYaw, smoothedHeadPitch,
            frontLeftSwing, frontRightSwing, rearLeftSwing, rearRightSwing,
            frontLeftKnee, frontRightKnee, rearLeftKnee, rearRightKnee,
            frontLeftFoot, frontRightFoot, rearLeftFoot, rearRightFoot,
            shoulderSway, hipSway, spineYaw, earFlop,
            tailBaseYaw, tailBaseRoll, tailTipYaw);
      for (int rootNode : rootNodes) {
         renderNode(matrixStack, buffer, rootNode, pose);
      }
      matrixStack.popPose();
   }

   private void renderNode(PoseStack matrixStack, VertexConsumer buffer, int nodeIndex, AnimationPose pose) {
      if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
         return;
      }

      ModelNode node = nodes.get(nodeIndex);
      matrixStack.pushPose();
      matrixStack.translate(node.translation[0], node.translation[1], node.translation[2]);
      matrixStack.rotate(new Quaternionf(node.rotation[0], node.rotation[1], node.rotation[2], node.rotation[3]));
      if (!isUnitScale(node.scale)) {
         matrixStack.scale(node.scale[0], node.scale[1], node.scale[2]);
      }

      applyNodeAnimation(matrixStack, node.name, pose);

      if (!node.triangles.isEmpty()) {
         renderTriangles(matrixStack, buffer, node.triangles);
      }

      for (int childIndex : node.children) {
         renderNode(matrixStack, buffer, childIndex, pose);
      }
      matrixStack.popPose();
   }

   private void applyNodeAnimation(PoseStack matrixStack, String nodeName, AnimationPose pose) {
      if (applyClipAnimation(matrixStack, nodeName, pose)) {
         return;
      }

      if ("chest".equals(nodeName)) {
         float breathe = (float) Math.sin(pose.idleCycle * 0.22F) * 0.018F * pose.idleBlend;
         matrixStack.translate(0.0D, breathe * 0.01F, 0.0D);
         matrixStack.rotate(Axis.ZP.rotationDegrees(pose.shoulderSway * 0.06F));
         matrixStack.rotate(Axis.XP.rotationDegrees(breathe + pose.walkBlend * 0.04F));
         return;
      }
      if ("left_leg".equals(nodeName)) {
         matrixStack.rotate(Axis.XP.rotationDegrees(pose.rearLeftSwing * 0.82F));
         matrixStack.rotate(Axis.ZP.rotationDegrees(-pose.hipSway * 0.25F));
         return;
      }
      if ("right_leg".equals(nodeName)) {
         matrixStack.rotate(Axis.XP.rotationDegrees(pose.rearRightSwing * 0.82F));
         matrixStack.rotate(Axis.ZP.rotationDegrees(pose.hipSway * 0.25F));
         return;
      }
      if ("left_arm".equals(nodeName)) {
         matrixStack.rotate(Axis.ZP.rotationDegrees(-78.0F + pose.earFlop + pose.shoulderSway * 0.25F));
         matrixStack.rotate(Axis.XP.rotationDegrees(pose.frontLeftSwing * 0.42F));
         return;
      }
      if ("right_arm".equals(nodeName)) {
         matrixStack.rotate(Axis.ZP.rotationDegrees(78.0F - pose.earFlop - pose.shoulderSway * 0.25F));
         matrixStack.rotate(Axis.XP.rotationDegrees(pose.frontRightSwing * 0.42F));
         return;
      }
      if ("left_polearm".equals(nodeName)) {
         matrixStack.rotate(Axis.ZP.rotationDegrees(-7.0F + pose.frontLeftFoot * 0.08F));
         matrixStack.rotate(Axis.XP.rotationDegrees(pose.frontLeftFoot * 0.18F));
         return;
      }
      if ("right_polearm".equals(nodeName)) {
         matrixStack.rotate(Axis.ZP.rotationDegrees(7.0F - pose.frontRightFoot * 0.08F));
         matrixStack.rotate(Axis.XP.rotationDegrees(pose.frontRightFoot * 0.18F));
      }
   }

   private boolean applyClipAnimation(PoseStack matrixStack, String nodeName, AnimationPose pose) {
      AnimationTransform idle = idleAnimation == null ? null : idleAnimation.sample(nodeName, pose.animationTime);
      AnimationTransform walk = walkAnimation == null ? null : walkAnimation.sample(nodeName, pose.walkAnimationTime);
      if (idle == null && walk == null) {
         return false;
      }

      float[] translation = blendVector(
            idle == null ? null : idle.translation,
            walk == null ? null : walk.translation,
            pose.walkBlend);
      if (translation != null) {
         matrixStack.translate(translation[0], translation[1], translation[2]);
      }

      float[] rotation = blendRotation(
            idle == null ? null : idle.rotation,
            walk == null ? null : walk.rotation,
            pose.walkBlend);
      if (rotation != null) {
         matrixStack.rotate(new Quaternionf(rotation[0], rotation[1], rotation[2], rotation[3]));
      }
      return true;
   }

   private float[] blendVector(float[] idle, float[] walk, float blend) {
      if (idle == null && walk == null) {
         return null;
      }
      float[] a = idle == null ? new float[]{0.0F, 0.0F, 0.0F} : idle;
      float[] b = walk == null ? idle : walk;
      if (b == null) {
         b = new float[]{0.0F, 0.0F, 0.0F};
      }
      return new float[]{
            Mth.lerp(blend, a[0], b[0]),
            Mth.lerp(blend, a[1], b[1]),
            Mth.lerp(blend, a[2], b[2])
      };
   }

   private float[] blendRotation(float[] idle, float[] walk, float blend) {
      if (idle == null && walk == null) {
         return null;
      }
      float[] identity = new float[]{0.0F, 0.0F, 0.0F, 1.0F};
      float[] a = idle == null ? identity : idle;
      float[] b = walk == null ? idle : walk;
      if (b == null) {
         b = identity;
      }
      return slerp(a, b, blend);
   }

   private float[] slerp(float[] a, float[] b, float blend) {
      float ax = a[0];
      float ay = a[1];
      float az = a[2];
      float aw = a[3];
      float bx = b[0];
      float by = b[1];
      float bz = b[2];
      float bw = b[3];
      float dot = ax * bx + ay * by + az * bz + aw * bw;
      if (dot < 0.0F) {
         dot = -dot;
         bx = -bx;
         by = -by;
         bz = -bz;
         bw = -bw;
      }

      float scaleA;
      float scaleB;
      if (dot > 0.9995F) {
         scaleA = 1.0F - blend;
         scaleB = blend;
      } else {
         float theta = (float) Math.acos(dot);
         float sinTheta = (float) Math.sin(theta);
         scaleA = (float) Math.sin((1.0F - blend) * theta) / sinTheta;
         scaleB = (float) Math.sin(blend * theta) / sinTheta;
      }

      float x = scaleA * ax + scaleB * bx;
      float y = scaleA * ay + scaleB * by;
      float z = scaleA * az + scaleB * bz;
      float w = scaleA * aw + scaleB * bw;
      float length = Mth.sqrt(x * x + y * y + z * z + w * w);
      if (length < 1.0E-5F) {
         return new float[]{0.0F, 0.0F, 0.0F, 1.0F};
      }
      return new float[]{x / length, y / length, z / length, w / length};
   }

   private boolean isUnitScale(float[] scale) {
      return Math.abs(scale[0] - 1.0F) < 1.0E-4F
            && Math.abs(scale[1] - 1.0F) < 1.0E-4F
            && Math.abs(scale[2] - 1.0F) < 1.0E-4F;
   }

   private float updateAnimationClock() {
      long now = System.nanoTime();
      if (lastAnimationTimeNanos == 0L) {
         lastAnimationTimeNanos = now;
         return 1.0F / 60.0F;
      }
      float deltaSeconds = (now - lastAnimationTimeNanos) / 1_000_000_000.0F;
      lastAnimationTimeNanos = now;
      return Math.min(deltaSeconds, 0.05F);
   }

   private float smoothApproach(float current, float target, float deltaSeconds, float speed) {
      float alpha = 1.0F - (float) Math.exp(-deltaSeconds * speed);
      return current + (target - current) * alpha;
   }

   private float smoothYaw(float current, float target, float deltaSeconds, float speed) {
      float delta = wrapDegrees(target - current);
      float alpha = 1.0F - (float) Math.exp(-deltaSeconds * speed);
      return normalizeDegrees(current + delta * alpha);
   }

   private float wrapDegrees(float angle) {
      angle %= 360.0F;
      if (angle >= 180.0F) {
         angle -= 360.0F;
      }
      if (angle < -180.0F) {
         angle += 360.0F;
      }
      return angle;
   }

   private float normalizeDegrees(float angle) {
      angle %= 360.0F;
      if (angle < 0.0F) {
         angle += 360.0F;
      }
      return angle;
   }

   private void ensureLoaded() {
      if (loaded || failed) {
         return;
      }
      try {
         Optional<Resource> resource = mc.getResourceManager().getResource(MODEL_LOCATION);
         if (resource.isEmpty()) {
            failed = true;
            return;
         }
         try (InputStream stream = resource.get().open();
              InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            parse(JsonParser.parseReader(reader).getAsJsonObject());
            loaded = true;
         }
      } catch (Exception e) {
         failed = true;
         System.err.println("Failed to load spike pet model from " + MODEL_LOCATION);
         e.printStackTrace();
      }
   }

   private void parse(JsonObject root) {
      nodes.clear();
      rootNodes.clear();
      animations.clear();
      idleAnimation = null;
      walkAnimation = null;

      JsonObject bounds = root.getAsJsonObject("bounds");
      JsonArray min = bounds.getAsJsonArray("min");
      JsonArray max = bounds.getAsJsonArray("max");
      minY = min.get(1).getAsFloat();
      centerX = (min.get(0).getAsFloat() + max.get(0).getAsFloat()) * 0.5F;
      centerZ = (min.get(2).getAsFloat() + max.get(2).getAsFloat()) * 0.5F;

      JsonArray roots = root.getAsJsonArray("rootNodes");
      for (int i = 0; i < roots.size(); i++) {
         rootNodes.add(roots.get(i).getAsInt());
      }

      JsonArray nodeArray = root.getAsJsonArray("nodes");
      for (int i = 0; i < nodeArray.size(); i++) {
         JsonObject nodeObject = nodeArray.get(i).getAsJsonObject();
         String name = nodeObject.has("name") ? nodeObject.get("name").getAsString() : "";
         float[] translation = readFloatArray(nodeObject.getAsJsonArray("translation"), 3, new float[]{0.0F, 0.0F, 0.0F});
         float[] rotation = readFloatArray(nodeObject.getAsJsonArray("rotation"), 4, new float[]{0.0F, 0.0F, 0.0F, 1.0F});
         float[] scale = readFloatArray(nodeObject.getAsJsonArray("scale"), 3, new float[]{1.0F, 1.0F, 1.0F});
         int[] children = readIntArray(nodeObject.getAsJsonArray("children"));
         List<MeshTriangle> triangles = new ArrayList<>();
         JsonArray triangleArray = nodeObject.getAsJsonArray("triangles");
         if (triangleArray != null) {
            for (int t = 0; t < triangleArray.size(); t++) {
               JsonArray tri = triangleArray.get(t).getAsJsonArray();
               triangles.add(new MeshTriangle(
                     readVertex(tri.get(0).getAsJsonArray()),
                     readVertex(tri.get(1).getAsJsonArray()),
                     readVertex(tri.get(2).getAsJsonArray())));
            }
         }
         nodes.add(new ModelNode(name, translation, rotation, scale, children, triangles));
      }

      modelYawOffset = resolveModelYawOffset();

      JsonArray animationArray = root.getAsJsonArray("animations");
      if (animationArray != null) {
         for (int i = 0; i < animationArray.size(); i++) {
            ModelAnimation animation = readAnimation(animationArray.get(i).getAsJsonObject());
            animations.add(animation);
            if ("idle".equalsIgnoreCase(animation.name)) {
               idleAnimation = animation;
            } else if ("walk".equalsIgnoreCase(animation.name)) {
               walkAnimation = animation;
            }
         }
      }
   }

   private ModelAnimation readAnimation(JsonObject object) {
      String name = object.has("name") ? object.get("name").getAsString() : "";
      float duration = object.has("duration") ? object.get("duration").getAsFloat() : 0.0F;
      List<AnimationChannel> channels = new ArrayList<>();
      JsonArray channelArray = object.getAsJsonArray("channels");
      if (channelArray != null) {
         for (int i = 0; i < channelArray.size(); i++) {
            JsonObject channelObject = channelArray.get(i).getAsJsonObject();
            String node = channelObject.has("node") ? channelObject.get("node").getAsString() : "";
            String path = channelObject.has("path") ? channelObject.get("path").getAsString() : "";
            float[] times = readFloatArray(channelObject.getAsJsonArray("times"));
            float[][] values = readFloatMatrix(channelObject.getAsJsonArray("values"));
            channels.add(new AnimationChannel(node, path, times, values));
         }
      }
      return new ModelAnimation(name, duration, channels);
   }

   private float resolveModelYawOffset() {
      int chestIndex = findNodeIndex("chest");
      int rootIndex = findNodeIndex("bone");
      if (chestIndex == -1 || rootIndex == -1) {
         return 180.0F;
      }

      float[] chest = getNodeWorldPosition(chestIndex);
      float[] root = getNodeWorldPosition(rootIndex);
      float dirX = chest[0] - root[0];
      float dirZ = chest[2] - root[2];
      if (Math.abs(dirX) < 1.0E-4F && Math.abs(dirZ) < 1.0E-4F) {
         return 180.0F;
      }

      return normalizeDegrees((float) Math.toDegrees(Math.atan2(dirZ, dirX)) - 90.0F);
   }

   private int findNodeIndex(String name) {
      for (int i = 0; i < nodes.size(); i++) {
         if (name.equals(nodes.get(i).name)) {
            return i;
         }
      }
      return -1;
   }

   private float[] getNodeWorldPosition(int targetIndex) {
      for (int rootIndex : rootNodes) {
         float[] position = accumulateNodePosition(rootIndex, targetIndex, identityMatrix());
         if (position != null) {
            return position;
         }
      }
      return new float[]{0.0F, 0.0F, 0.0F};
   }

   private float[] accumulateNodePosition(int currentIndex, int targetIndex, float[] parentMatrix) {
      if (currentIndex < 0 || currentIndex >= nodes.size()) {
         return null;
      }

      ModelNode node = nodes.get(currentIndex);
      float[] currentMatrix = multiplyMatrices(parentMatrix, composeNodeMatrix(node));
      if (currentIndex == targetIndex) {
         return new float[]{currentMatrix[12], currentMatrix[13], currentMatrix[14]};
      }

      for (int childIndex : node.children) {
         float[] result = accumulateNodePosition(childIndex, targetIndex, currentMatrix);
         if (result != null) {
            return result;
         }
      }
      return null;
   }

   private float[] identityMatrix() {
      return new float[]{
            1.0F, 0.0F, 0.0F, 0.0F,
            0.0F, 1.0F, 0.0F, 0.0F,
            0.0F, 0.0F, 1.0F, 0.0F,
            0.0F, 0.0F, 0.0F, 1.0F
      };
   }

   private float[] composeNodeMatrix(ModelNode node) {
      float x = node.rotation[0];
      float y = node.rotation[1];
      float z = node.rotation[2];
      float w = node.rotation[3];
      float x2 = x + x;
      float y2 = y + y;
      float z2 = z + z;
      float xx = x * x2;
      float xy = x * y2;
      float xz = x * z2;
      float yy = y * y2;
      float yz = y * z2;
      float zz = z * z2;
      float wx = w * x2;
      float wy = w * y2;
      float wz = w * z2;

      return new float[]{
            (1.0F - (yy + zz)) * node.scale[0], (xy + wz) * node.scale[0], (xz - wy) * node.scale[0], 0.0F,
            (xy - wz) * node.scale[1], (1.0F - (xx + zz)) * node.scale[1], (yz + wx) * node.scale[1], 0.0F,
            (xz + wy) * node.scale[2], (yz - wx) * node.scale[2], (1.0F - (xx + yy)) * node.scale[2], 0.0F,
            node.translation[0], node.translation[1], node.translation[2], 1.0F
      };
   }

   private float[] multiplyMatrices(float[] a, float[] b) {
      float[] out = new float[16];
      for (int column = 0; column < 4; column++) {
         for (int row = 0; row < 4; row++) {
            out[column * 4 + row] =
                  a[row] * b[column * 4]
                        + a[4 + row] * b[column * 4 + 1]
                        + a[8 + row] * b[column * 4 + 2]
                        + a[12 + row] * b[column * 4 + 3];
         }
      }
      return out;
   }

   private float[] readFloatArray(JsonArray array, int expectedSize, float[] fallback) {
      if (array == null || array.size() < expectedSize) {
         return fallback;
      }
      float[] values = new float[expectedSize];
      for (int i = 0; i < expectedSize; i++) {
         values[i] = array.get(i).getAsFloat();
      }
      return values;
   }

   private float[] readFloatArray(JsonArray array) {
      if (array == null || array.size() == 0) {
         return new float[0];
      }
      float[] values = new float[array.size()];
      for (int i = 0; i < array.size(); i++) {
         values[i] = array.get(i).getAsFloat();
      }
      return values;
   }

   private float[][] readFloatMatrix(JsonArray array) {
      if (array == null || array.size() == 0) {
         return new float[0][0];
      }
      float[][] values = new float[array.size()][];
      for (int i = 0; i < array.size(); i++) {
         values[i] = readFloatArray(array.get(i).getAsJsonArray());
      }
      return values;
   }

   private int[] readIntArray(JsonArray array) {
      if (array == null || array.size() == 0) {
         return new int[0];
      }
      int[] values = new int[array.size()];
      for (int i = 0; i < array.size(); i++) {
         values[i] = array.get(i).getAsInt();
      }
      return values;
   }

   private MeshVertex readVertex(JsonArray array) {
      return new MeshVertex(
            array.get(0).getAsFloat(),
            array.get(1).getAsFloat(),
            array.get(2).getAsFloat(),
            array.get(3).getAsFloat(),
            array.get(4).getAsFloat());
   }

   private void renderTriangles(PoseStack matrixStack, VertexConsumer buffer, List<MeshTriangle> triangles) {
      Matrix4f matrix = matrixStack.last().pose();
      for (MeshTriangle triangle : triangles) {
         putVertex(buffer, matrix, triangle.a);
         putVertex(buffer, matrix, triangle.b);
         putVertex(buffer, matrix, triangle.c);
      }
   }

   private void putVertex(VertexConsumer buffer, Matrix4f matrix, MeshVertex vertex) {
      buffer.addVertex(matrix, vertex.x, vertex.y, vertex.z).setUv(vertex.u, vertex.v).setColor(255, 255, 255, 255);
   }

   private static final class ModelAnimation {
      private final String name;
      private final float duration;
      private final List<AnimationChannel> channels;

      private ModelAnimation(String name, float duration, List<AnimationChannel> channels) {
         this.name = name;
         this.duration = duration;
         this.channels = channels;
      }

      private AnimationTransform sample(String nodeName, float time) {
         AnimationTransform transform = null;
         for (AnimationChannel channel : channels) {
            if (!channel.node.equals(nodeName)) {
               continue;
            }
            if (transform == null) {
               transform = new AnimationTransform();
            }
            float[] value = channel.sample(duration, time);
            if ("translation".equals(channel.path)) {
               transform.translation = value;
            } else if ("rotation".equals(channel.path)) {
               transform.rotation = value;
            }
         }
         return transform;
      }
   }

   private static final class AnimationChannel {
      private final String node;
      private final String path;
      private final float[] times;
      private final float[][] values;

      private AnimationChannel(String node, String path, float[] times, float[][] values) {
         this.node = node;
         this.path = path;
         this.times = times;
         this.values = values;
      }

      private float[] sample(float duration, float time) {
         if (times.length == 0 || values.length == 0) {
            return new float[0];
         }
         float localTime = duration > 0.0F ? time % duration : time;
         if (localTime < 0.0F) {
            localTime += duration;
         }
         if (times.length == 1 || localTime <= times[0]) {
            return values[0];
         }
         int last = Math.min(times.length, values.length) - 1;
         if (localTime >= times[last]) {
            return values[last];
         }
         int frame = 0;
         while (frame + 1 < last && localTime > times[frame + 1]) {
            frame++;
         }
         float start = times[frame];
         float end = times[frame + 1];
         float blend = end <= start ? 0.0F : (localTime - start) / (end - start);
         if ("rotation".equals(path)) {
            return slerpValues(values[frame], values[frame + 1], blend);
         }
         return lerpValues(values[frame], values[frame + 1], blend);
      }

      private static float[] lerpValues(float[] a, float[] b, float blend) {
         int size = Math.min(a.length, b.length);
         float[] result = new float[size];
         for (int i = 0; i < size; i++) {
            result[i] = Mth.lerp(blend, a[i], b[i]);
         }
         return result;
      }

      private static float[] slerpValues(float[] a, float[] b, float blend) {
         float ax = a[0];
         float ay = a[1];
         float az = a[2];
         float aw = a[3];
         float bx = b[0];
         float by = b[1];
         float bz = b[2];
         float bw = b[3];
         float dot = ax * bx + ay * by + az * bz + aw * bw;
         if (dot < 0.0F) {
            dot = -dot;
            bx = -bx;
            by = -by;
            bz = -bz;
            bw = -bw;
         }
         float scaleA;
         float scaleB;
         if (dot > 0.9995F) {
            scaleA = 1.0F - blend;
            scaleB = blend;
         } else {
            float theta = (float) Math.acos(dot);
            float sinTheta = (float) Math.sin(theta);
            scaleA = (float) Math.sin((1.0F - blend) * theta) / sinTheta;
            scaleB = (float) Math.sin(blend * theta) / sinTheta;
         }
         float x = scaleA * ax + scaleB * bx;
         float y = scaleA * ay + scaleB * by;
         float z = scaleA * az + scaleB * bz;
         float w = scaleA * aw + scaleB * bw;
         float length = Mth.sqrt(x * x + y * y + z * z + w * w);
         if (length < 1.0E-5F) {
            return new float[]{0.0F, 0.0F, 0.0F, 1.0F};
         }
         return new float[]{x / length, y / length, z / length, w / length};
      }
   }

   private static final class AnimationTransform {
      private float[] translation;
      private float[] rotation;
   }

   private static final class AnimationPose {
      private final float idleCycle;
      private final float animationTime;
      private final float walkAnimationTime;
      private final float walkBlend;
      private final float idleBlend;
      private final float headYaw;
      private final float headPitch;
      private final float frontLeftSwing;
      private final float frontRightSwing;
      private final float rearLeftSwing;
      private final float rearRightSwing;
      private final float frontLeftKnee;
      private final float frontRightKnee;
      private final float rearLeftKnee;
      private final float rearRightKnee;
      private final float frontLeftFoot;
      private final float frontRightFoot;
      private final float rearLeftFoot;
      private final float rearRightFoot;
      private final float shoulderSway;
      private final float hipSway;
      private final float spineYaw;
      private final float earFlop;
      private final float tailBaseYaw;
      private final float tailBaseRoll;
      private final float tailTipYaw;

      private AnimationPose(float idleCycle, float animationTime, float walkAnimationTime, float walkBlend, float idleBlend, float headYaw, float headPitch,
                            float frontLeftSwing, float frontRightSwing, float rearLeftSwing, float rearRightSwing,
                            float frontLeftKnee, float frontRightKnee, float rearLeftKnee, float rearRightKnee,
                            float frontLeftFoot, float frontRightFoot, float rearLeftFoot, float rearRightFoot,
                            float shoulderSway, float hipSway,
                            float spineYaw, float earFlop, float tailBaseYaw, float tailBaseRoll, float tailTipYaw) {
         this.idleCycle = idleCycle;
         this.animationTime = animationTime;
         this.walkAnimationTime = walkAnimationTime;
         this.walkBlend = walkBlend;
         this.idleBlend = idleBlend;
         this.headYaw = headYaw;
         this.headPitch = headPitch;
         this.frontLeftSwing = frontLeftSwing;
         this.frontRightSwing = frontRightSwing;
         this.rearLeftSwing = rearLeftSwing;
         this.rearRightSwing = rearRightSwing;
         this.frontLeftKnee = frontLeftKnee;
         this.frontRightKnee = frontRightKnee;
         this.rearLeftKnee = rearLeftKnee;
         this.rearRightKnee = rearRightKnee;
         this.frontLeftFoot = frontLeftFoot;
         this.frontRightFoot = frontRightFoot;
         this.rearLeftFoot = rearLeftFoot;
         this.rearRightFoot = rearRightFoot;
         this.shoulderSway = shoulderSway;
         this.hipSway = hipSway;
         this.spineYaw = spineYaw;
         this.earFlop = earFlop;
         this.tailBaseYaw = tailBaseYaw;
         this.tailBaseRoll = tailBaseRoll;
         this.tailTipYaw = tailTipYaw;
      }
   }

   private static final class ModelNode {
      private final String name;
      private final float[] translation;
      private final float[] rotation;
      private final float[] scale;
      private final int[] children;
      private final List<MeshTriangle> triangles;

      private ModelNode(String name, float[] translation, float[] rotation, float[] scale, int[] children, List<MeshTriangle> triangles) {
         this.name = name;
         this.translation = translation;
         this.rotation = rotation;
         this.scale = scale;
         this.children = children;
         this.triangles = triangles;
      }
   }

   private static final class MeshTriangle {
      private final MeshVertex a;
      private final MeshVertex b;
      private final MeshVertex c;

      private MeshTriangle(MeshVertex a, MeshVertex b, MeshVertex c) {
         this.a = a;
         this.b = b;
         this.c = c;
      }
   }

   private static final class MeshVertex {
      private final float x;
      private final float y;
      private final float z;
      private final float u;
      private final float v;

      private MeshVertex(float x, float y, float z, float u, float v) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.u = u;
         this.v = v;
      }
   }
}
