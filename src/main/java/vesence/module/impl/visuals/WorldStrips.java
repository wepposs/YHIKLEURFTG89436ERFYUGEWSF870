package vesence.module.impl.visuals;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
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
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import net.minecraft.client.renderer.BindGroupLayouts;

@IModule(name = "World Strips", description = "Smooth glowing strips drifting through the world", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class WorldStrips extends Module {
   private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloom.png");
   private static final Identifier SOFT_GLOW_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloomsample.png");
   private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "world_strips_color"))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
         .build()
   );
   private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "world_strips_glow"))
         .withVertexShader("core/position_tex_color")
         .withFragmentShader("core/position_tex_color")
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
         .build()
   );
   private static final RenderType COLOR_LAYER = RenderType.create("world_strips_color", RenderSetup.builder(COLOR_PIPELINE).createRenderSetup());
   private static final RenderType GLOW_LAYER = RenderType.create("world_strips_glow", RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", GLOW_TEXTURE).createRenderSetup());
   private static final RenderType SOFT_GLOW_LAYER = RenderType.create("world_strips_soft_glow", RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", SOFT_GLOW_TEXTURE).createRenderSetup());

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
      if (mc.player == null || mc.level == null) {
         this.strips.clear();
         return;
      }

      int desiredCount = count.get().intValue();
      double worldRadius = radius.get();
      double speedScale = speed.get();
      Vec3 playerCenter = mc.player.position().add(0.0, mc.player.getBbHeight() * 0.6, 0.0);

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

      Immediate immediate = new Immediate();
      PoseStack matrices = event.getMatrixStack();
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      float stripLength = length.get().floatValue();
      float stripWidth = width.get().floatValue();
      boolean glowEnabled = bloom.get();

      for (StripParticle strip : this.strips) {
         strip.render(matrices, immediate, cameraPos, stripLength, stripWidth, glowEnabled);
      }

      immediate.draw();
   }

   private static void drawSolidHalf(VertexConsumer buffer, Matrix4f matrix, Vec3 start, Vec3 end, Vec3 side, int startColor, int endColor) {
      Vec3 a = start.add(side);
      Vec3 b = start.subtract(side);
      Vec3 c = end.subtract(side);
      Vec3 d = end.add(side);
      putColorVertex(buffer, matrix, a, startColor);
      putColorVertex(buffer, matrix, b, startColor);
      putColorVertex(buffer, matrix, c, endColor);
      putColorVertex(buffer, matrix, d, endColor);
   }

   private static void drawGlowHalf(VertexConsumer buffer, Matrix4f matrix, Vec3 start, Vec3 end, Vec3 side, int startColor, int endColor) {
      Vec3 a = start.add(side);
      Vec3 b = start.subtract(side);
      Vec3 c = end.subtract(side);
      Vec3 d = end.add(side);
      putTexturedVertex(buffer, matrix, a, startColor, 0.0F, 1.0F);
      putTexturedVertex(buffer, matrix, b, startColor, 1.0F, 1.0F);
      putTexturedVertex(buffer, matrix, c, endColor, 1.0F, 0.0F);
      putTexturedVertex(buffer, matrix, d, endColor, 0.0F, 0.0F);
   }

   private static void putColorVertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int color) {
      buffer.addVertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
         .setColor(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color));
   }

   private static void putTexturedVertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int color, float u, float v) {
      buffer.addVertex(matrix, (float)pos.x, (float)pos.y, (float)pos.z)
         .setColor(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
         .setUv(u, v)
         .setOverlay(OverlayTexture.NO_OVERLAY)
         .setLight(15728880)
         .setNormal(0.0F, 0.0F, 1.0F);
   }

   private static Vec3 lerp(Vec3 from, Vec3 to, double delta) {
      return from.add(to.subtract(from).scale(delta));
   }

   private static double random(double min, double max) {
      return ThreadLocalRandom.current().nextDouble(min, max);
   }

   @Environment(EnvType.CLIENT)
   private static final class StripParticle {
      private Vec3 position;
      private Vec3 renderPosition;
      private Vec3 motion;
      private Vec3 direction;
      private final double phase;
      private final double turnSpeed;
      private final double swaySpeed;
      private final double swayStrength;
      private final int colorOffset;
      private final int maxAge;
      private int age;

      private StripParticle(Vec3 center, double radius, double speedScale) {
         double angle = random(0.0, Math.PI * 2.0);
         double spawnRadius = random(radius * 0.35, radius);
         double height = random(-1.2, 3.6);
         this.position = center.add(Math.cos(angle) * spawnRadius, height, Math.sin(angle) * spawnRadius);
         this.renderPosition = this.position;
         this.direction = new Vec3(-Math.sin(angle), random(-0.18, 0.18), Math.cos(angle)).normalize();
         this.motion = this.direction.scale(0.028 + speedScale * 0.012);
         this.phase = random(0.0, Math.PI * 2.0);
         this.turnSpeed = random(0.018, 0.036);
         this.swaySpeed = random(0.032, 0.054);
         this.swayStrength = random(0.003, 0.009);
         this.colorOffset = ThreadLocalRandom.current().nextInt(0, 3600);
         this.maxAge = ThreadLocalRandom.current().nextInt(130, 230);
      }

      private void update(Vec3 center, double radius, double speedScale) {
         this.age++;
         double ageTime = this.age;
         Vec3 orbital = new Vec3(
            Math.cos(this.phase + ageTime * this.turnSpeed),
            Math.sin(this.phase * 0.7 + ageTime * this.swaySpeed) * 0.28,
            Math.sin(this.phase + ageTime * this.turnSpeed)
         );
         Vec3 desiredDirection = this.direction.scale(0.88).add(orbital.scale(0.12));
         if (desiredDirection.lengthSqr() > 1.0E-4) {
            desiredDirection = desiredDirection.normalize();
         } else {
            desiredDirection = new Vec3(1.0, 0.0, 0.0);
         }

         Vec3 pull = center.subtract(this.position);
         if (pull.lengthSqr() > radius * radius * 1.4) {
            desiredDirection = desiredDirection.add(pull.normalize().scale(0.28)).normalize();
         }

         this.direction = WorldStrips.lerp(this.direction, desiredDirection, 0.18).normalize();
         Vec3 sway = new Vec3(
            Math.cos(this.phase * 1.31 + ageTime * this.swaySpeed) * this.swayStrength,
            Math.sin(this.phase * 0.83 + ageTime * this.swaySpeed) * this.swayStrength * 0.7,
            Math.sin(this.phase * 1.17 + ageTime * this.swaySpeed) * this.swayStrength
         );
         double velocity = 0.016 + speedScale * 0.022;
         this.motion = this.motion.scale(0.84).add(this.direction.scale(velocity)).add(sway);
         this.position = this.position.add(this.motion);
         this.renderPosition = WorldStrips.lerp(this.renderPosition, this.position, 0.24);
      }

      private boolean shouldRemove(Vec3 center, double radius) {
         return this.age > this.maxAge || this.position.distanceTo(center) > radius * 2.4;
      }

      private float alpha() {
         float fadeIn = Math.min(1.0F, this.age / 14.0F);
         float fadeOut = Math.min(1.0F, (this.maxAge - this.age) / 24.0F);
         float pulse = 0.82F + 0.18F * (float)Math.sin(this.phase + this.age * 0.12);
         return Math.max(0.0F, fadeIn * fadeOut * pulse);
      }

      private void render(PoseStack matrices, Immediate immediate, Vec3 cameraPos, float stripLength, float stripWidth, boolean glowEnabled) {
         float alpha = this.alpha();
         if (alpha <= 0.01F) {
            return;
         }

         Vec3 center = this.renderPosition.subtract(cameraPos);
         Vec3 dir = this.direction.lengthSqr() > 1.0E-4 ? this.direction.normalize() : new Vec3(1.0, 0.0, 0.0);
         Vec3 toCamera = cameraPos.subtract(this.renderPosition);
         if (toCamera.lengthSqr() < 1.0E-4) {
            toCamera = new Vec3(0.0, 1.0, 0.0);
         } else {
            toCamera = toCamera.normalize();
         }

         Vec3 side = dir.cross(toCamera);
         if (side.lengthSqr() < 1.0E-4) {
            side = dir.cross(new Vec3(0.0, 1.0, 0.0));
         }
         if (side.lengthSqr() < 1.0E-4) {
            side = new Vec3(1.0, 0.0, 0.0);
         }

         side = side.normalize().scale(stripWidth * (0.95F + 0.18F * (float)Math.sin(this.phase + this.age * 0.11)));
         Vec3 half = dir.scale(stripLength * 0.5F);
         Vec3 start = center.subtract(half);
         Vec3 end = center.add(half);
         Matrix4f matrix = matrices.last().pose();
         int baseColor = ColorUtil.fade(this.colorOffset);
         int tipColor = ColorUtil.multAlpha(baseColor, alpha * 0.08F);
         int coreColor = ColorUtil.multAlpha(baseColor, alpha * 0.42F);
         VertexConsumer colorBuffer = immediate.getBuffer(COLOR_LAYER);

         drawSolidHalf(colorBuffer, matrix, start, center, side, tipColor, coreColor);
         drawSolidHalf(colorBuffer, matrix, center, end, side, coreColor, tipColor);

         if (glowEnabled) {
            Vec3 wideSide = side.scale(2.6);
            int glowTip = ColorUtil.multAlpha(baseColor, alpha * 0.18F);
            int glowCore = ColorUtil.multAlpha(baseColor, alpha * 0.55F);
            VertexConsumer glowBuffer = immediate.getBuffer(GLOW_LAYER);
            VertexConsumer softGlowBuffer = immediate.getBuffer(SOFT_GLOW_LAYER);
            drawGlowHalf(glowBuffer, matrix, start, center, wideSide, glowTip, glowCore);
            drawGlowHalf(glowBuffer, matrix, center, end, wideSide, glowCore, glowTip);
            drawGlowHalf(softGlowBuffer, matrix, start, center, wideSide.scale(1.45), glowTip, glowCore);
            drawGlowHalf(softGlowBuffer, matrix, center, end, wideSide.scale(1.45), glowCore, glowTip);

            matrices.pushPose();
            matrices.translate(center.x, center.y, center.z);
            matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
            matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));
            Matrix4f glowMatrix = matrices.last().pose();
            WorldRenderUtil.drawGlow(immediate.getBuffer(SOFT_GLOW_LAYER), glowMatrix, baseColor, (int)(78.0F * alpha), stripWidth * 7.5F);
            matrices.popPose();
         }
      }
   }
}
