package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.player.EventMotion;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.other.TimerUtil;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.world.WorldRenderUtil;

@IModule(name = "World Strips", description = "Smooth glowing strips drifting through the world", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class WorldStrips extends Module {
   private static final Identifier GLOW_TEXTURE = Identifier.of("vesence", "textures/world/dashbloom.png");
   private static final Identifier SOFT_GLOW_TEXTURE = Identifier.of("vesence", "textures/world/dashbloomsample.png");
   private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "world_strips_color"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .build()
   );
   private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "world_strips_glow"))
         .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.ADDITIVE)
         .build()
   );
   private static final RenderLayer COLOR_LAYER = RenderLayer.of("world_strips_color", RenderSetup.builder(COLOR_PIPELINE).expectedBufferSize(2048).translucent().build());
   private static final RenderLayer GLOW_LAYER = RenderLayer.of("world_strips_glow", RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(2048).translucent().texture("Sampler0", GLOW_TEXTURE).build());
   private static final RenderLayer SOFT_GLOW_LAYER = RenderLayer.of("world_strips_soft_glow", RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(2048).translucent().texture("Sampler0", SOFT_GLOW_TEXTURE).build());

   public static final SliderSetting count = new SliderSetting("Count", 26.0, 6.0, 80.0, 1.0, false);
   public static final SliderSetting radius = new SliderSetting("Radius", 10.0, 4.0, 24.0, 0.5, false);
   public static final SliderSetting length = new SliderSetting("Length", 1.7, 0.6, 4.0, 0.1, false);
   public static final SliderSetting width = new SliderSetting("Width", 0.18, 0.05, 0.6, 0.01, false);
   public static final SliderSetting speed = new SliderSetting("Speed", 0.9, 0.2, 2.2, 0.05, false);
   public static final BooleanSetting bloom = new BooleanSetting("Bloom", true);

   private final List<StripParticle> strips = new ArrayList<>();
   private final TimerUtil.satosTime spawnTimer = new TimerUtil.satosTime();

   public WorldStrips() {
      this.addSettings(new Setting[]{count, radius, length, width, speed, bloom});
   }

   @Override
   public void onDisable() {
      this.strips.clear();
      super.onDisable();
   }

   @EventInit
   public void onMotion(EventMotion event) {
      if (mc.player == null || mc.world == null) {
         this.strips.clear();
         return;
      }

      int desiredCount = count.get().intValue();
      double worldRadius = radius.get();
      double speedScale = speed.get();
      Vec3d playerCenter = mc.player.getEntityPos().add(0.0, mc.player.getHeight() * 0.6, 0.0);

      while (this.strips.size() > desiredCount) {
         this.strips.remove(0);
      }

      if (this.strips.size() < desiredCount && (this.strips.isEmpty() || this.spawnTimer.hasReached(90L))) {
         this.strips.add(new StripParticle(playerCenter, worldRadius, speedScale));
         this.spawnTimer.reset();
      }

      Iterator<StripParticle> iterator = this.strips.iterator();

      while (iterator.hasNext()) {
         StripParticle strip = iterator.next();
         strip.update(playerCenter, worldRadius, speedScale);
         if (strip.shouldRemove(playerCenter, worldRadius)) {
            iterator.remove();
         }
      }
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (this.strips.isEmpty() || mc.player == null) {
         return;
      }

      Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
      MatrixStack matrices = event.getMatrixStack();
      Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      float stripLength = length.get().floatValue();
      float stripWidth = width.get().floatValue();
      boolean glowEnabled = bloom.get();

      for (StripParticle strip : this.strips) {
         strip.render(matrices, immediate, cameraPos, stripLength, stripWidth, glowEnabled);
      }

      immediate.draw();
   }

   private static void drawSolidHalf(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, Vec3d side, int startColor, int endColor) {
      Vec3d a = start.add(side);
      Vec3d b = start.subtract(side);
      Vec3d c = end.subtract(side);
      Vec3d d = end.add(side);
      putColorVertex(buffer, matrix, a, startColor);
      putColorVertex(buffer, matrix, b, startColor);
      putColorVertex(buffer, matrix, c, endColor);
      putColorVertex(buffer, matrix, d, endColor);
   }

   private static void drawGlowHalf(VertexConsumer buffer, Matrix4f matrix, Vec3d start, Vec3d end, Vec3d side, int startColor, int endColor) {
      Vec3d a = start.add(side);
      Vec3d b = start.subtract(side);
      Vec3d c = end.subtract(side);
      Vec3d d = end.add(side);
      putTexturedVertex(buffer, matrix, a, startColor, 0.0F, 1.0F);
      putTexturedVertex(buffer, matrix, b, startColor, 1.0F, 1.0F);
      putTexturedVertex(buffer, matrix, c, endColor, 1.0F, 0.0F);
      putTexturedVertex(buffer, matrix, d, endColor, 0.0F, 0.0F);
   }

   private static void putColorVertex(VertexConsumer buffer, Matrix4f matrix, Vec3d pos, int color) {
      buffer.vertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
         .color(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color));
   }

   private static void putTexturedVertex(VertexConsumer buffer, Matrix4f matrix, Vec3d pos, int color, float u, float v) {
      buffer.vertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
         .color(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
         .texture(u, v)
         .overlay(OverlayTexture.DEFAULT_UV)
         .light(15728880)
         .normal(0.0F, 0.0F, 1.0F);
   }

   private static Vec3d lerp(Vec3d from, Vec3d to, double delta) {
      return from.add(to.subtract(from).multiply(delta));
   }

   private static double random(double min, double max) {
      return ThreadLocalRandom.current().nextDouble(min, max);
   }

   @Environment(EnvType.CLIENT)
   private static final class StripParticle {
      private Vec3d position;
      private Vec3d renderPosition;
      private Vec3d motion;
      private Vec3d direction;
      private final double phase;
      private final double turnSpeed;
      private final double swaySpeed;
      private final double swayStrength;
      private final int colorOffset;
      private final int maxAge;
      private int age;

      private StripParticle(Vec3d center, double radius, double speedScale) {
         double angle = random(0.0, Math.PI * 2.0);
         double spawnRadius = random(radius * 0.35, radius);
         double height = random(-1.2, 3.6);
         this.position = center.add(Math.cos(angle) * spawnRadius, height, Math.sin(angle) * spawnRadius);
         this.renderPosition = this.position;
         this.direction = new Vec3d(-Math.sin(angle), random(-0.18, 0.18), Math.cos(angle)).normalize();
         this.motion = this.direction.multiply(0.028 + speedScale * 0.012);
         this.phase = random(0.0, Math.PI * 2.0);
         this.turnSpeed = random(0.018, 0.036);
         this.swaySpeed = random(0.032, 0.054);
         this.swayStrength = random(0.003, 0.009);
         this.colorOffset = ThreadLocalRandom.current().nextInt(0, 3600);
         this.maxAge = ThreadLocalRandom.current().nextInt(130, 230);
      }

      private void update(Vec3d center, double radius, double speedScale) {
         this.age++;
         double ageTime = this.age;
         Vec3d orbital = new Vec3d(
            Math.cos(this.phase + ageTime * this.turnSpeed),
            Math.sin(this.phase * 0.7 + ageTime * this.swaySpeed) * 0.28,
            Math.sin(this.phase + ageTime * this.turnSpeed)
         );
         Vec3d desiredDirection = this.direction.multiply(0.88).add(orbital.multiply(0.12));
         if (desiredDirection.lengthSquared() > 1.0E-4) {
            desiredDirection = desiredDirection.normalize();
         } else {
            desiredDirection = new Vec3d(1.0, 0.0, 0.0);
         }

         Vec3d pull = center.subtract(this.position);
         if (pull.lengthSquared() > radius * radius * 1.4) {
            desiredDirection = desiredDirection.add(pull.normalize().multiply(0.28)).normalize();
         }

         this.direction = WorldStrips.lerp(this.direction, desiredDirection, 0.18).normalize();
         Vec3d sway = new Vec3d(
            Math.cos(this.phase * 1.31 + ageTime * this.swaySpeed) * this.swayStrength,
            Math.sin(this.phase * 0.83 + ageTime * this.swaySpeed) * this.swayStrength * 0.7,
            Math.sin(this.phase * 1.17 + ageTime * this.swaySpeed) * this.swayStrength
         );
         double velocity = 0.016 + speedScale * 0.022;
         this.motion = this.motion.multiply(0.84).add(this.direction.multiply(velocity)).add(sway);
         this.position = this.position.add(this.motion);
         this.renderPosition = WorldStrips.lerp(this.renderPosition, this.position, 0.24);
      }

      private boolean shouldRemove(Vec3d center, double radius) {
         return this.age > this.maxAge || this.position.distanceTo(center) > radius * 2.4;
      }

      private float alpha() {
         float fadeIn = Math.min(1.0F, this.age / 14.0F);
         float fadeOut = Math.min(1.0F, (this.maxAge - this.age) / 24.0F);
         float pulse = 0.82F + 0.18F * (float)Math.sin(this.phase + this.age * 0.12);
         return Math.max(0.0F, fadeIn * fadeOut * pulse);
      }

      private void render(MatrixStack matrices, Immediate immediate, Vec3d cameraPos, float stripLength, float stripWidth, boolean glowEnabled) {
         float alpha = this.alpha();
         if (alpha <= 0.01F) {
            return;
         }

         Vec3d center = this.renderPosition.subtract(cameraPos);
         Vec3d dir = this.direction.lengthSquared() > 1.0E-4 ? this.direction.normalize() : new Vec3d(1.0, 0.0, 0.0);
         Vec3d toCamera = cameraPos.subtract(this.renderPosition);
         if (toCamera.lengthSquared() < 1.0E-4) {
            toCamera = new Vec3d(0.0, 1.0, 0.0);
         } else {
            toCamera = toCamera.normalize();
         }

         Vec3d side = dir.crossProduct(toCamera);
         if (side.lengthSquared() < 1.0E-4) {
            side = dir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
         }
         if (side.lengthSquared() < 1.0E-4) {
            side = new Vec3d(1.0, 0.0, 0.0);
         }

         side = side.normalize().multiply(stripWidth * (0.95F + 0.18F * (float)Math.sin(this.phase + this.age * 0.11)));
         Vec3d half = dir.multiply(stripLength * 0.5F);
         Vec3d start = center.subtract(half);
         Vec3d end = center.add(half);
         Matrix4f matrix = matrices.peek().getPositionMatrix();
         int baseColor = ColorUtil.fade(this.colorOffset);
         int tipColor = ColorUtil.multAlpha(baseColor, alpha * 0.08F);
         int coreColor = ColorUtil.multAlpha(baseColor, alpha * 0.42F);
         VertexConsumer colorBuffer = immediate.getBuffer(COLOR_LAYER);

         drawSolidHalf(colorBuffer, matrix, start, center, side, tipColor, coreColor);
         drawSolidHalf(colorBuffer, matrix, center, end, side, coreColor, tipColor);

         if (glowEnabled) {
            Vec3d wideSide = side.multiply(2.6);
            int glowTip = ColorUtil.multAlpha(baseColor, alpha * 0.18F);
            int glowCore = ColorUtil.multAlpha(baseColor, alpha * 0.55F);
            VertexConsumer glowBuffer = immediate.getBuffer(GLOW_LAYER);
            VertexConsumer softGlowBuffer = immediate.getBuffer(SOFT_GLOW_LAYER);
            drawGlowHalf(glowBuffer, matrix, start, center, wideSide, glowTip, glowCore);
            drawGlowHalf(glowBuffer, matrix, center, end, wideSide, glowCore, glowTip);
            drawGlowHalf(softGlowBuffer, matrix, start, center, wideSide.multiply(1.45), glowTip, glowCore);
            drawGlowHalf(softGlowBuffer, matrix, center, end, wideSide.multiply(1.45), glowCore, glowTip);

            matrices.push();
            matrices.translate(center.x, center.y, center.z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
            Matrix4f glowMatrix = matrices.peek().getPositionMatrix();
            WorldRenderUtil.drawGlow(immediate.getBuffer(SOFT_GLOW_LAYER), glowMatrix, baseColor, (int)(78.0F * alpha), stripWidth * 7.5F);
            matrices.pop();
         }
      }
   }
}
