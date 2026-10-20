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

@IModule(name = "Bloom Tadpoles", description = "Soft translucent tadpoles with bloom and smooth drifting", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class BloomTadpoles extends Module {
   private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloom.png");
   private static final Identifier SOFT_GLOW_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloomsample.png");
   private static final RenderPipeline COLOR_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "bloom_tadpoles_color"))
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
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "bloom_tadpoles_glow"))
         .withVertexShader("core/position_tex_color")
         .withFragmentShader("core/position_tex_color")
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
         .build()
   );
   private static final RenderType COLOR_LAYER = RenderType.create("bloom_tadpoles_color", RenderSetup.builder(COLOR_PIPELINE).createRenderSetup());
   private static final RenderType GLOW_LAYER = RenderType.create("bloom_tadpoles_glow", RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", GLOW_TEXTURE).createRenderSetup());
   private static final RenderType SOFT_GLOW_LAYER = RenderType.create("bloom_tadpoles_soft_glow", RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", SOFT_GLOW_TEXTURE).createRenderSetup());

   public static final SliderSetting count = new SliderSetting("Count", 12.0, 3.0, 32.0, 1.0, false);
   public static final SliderSetting radius = new SliderSetting("Radius", 7.5, 3.0, 18.0, 0.5, false);
   public static final SliderSetting speed = new SliderSetting("Speed", 0.85, 0.2, 2.0, 0.05, false);
   public static final SliderSetting headSize = new SliderSetting("Head Size", 0.25, 0.1, 0.55, 0.01, false);
   public static final SliderSetting tailLength = new SliderSetting("Tail Length", 1.35, 0.6, 3.2, 0.05, false);
   public static final BooleanSetting bloom = new BooleanSetting("Bloom", true);

   private final List<TadpoleParticle> tadpoles = new ArrayList<>();
   private final TimerUtil.satosTime spawnTimer = new TimerUtil.satosTime();

   public BloomTadpoles() {
      this.addSettings(new Setting[]{count, radius, speed, headSize, tailLength, bloom});
   }

   @Override
   public void onDisable() {
      this.tadpoles.clear();
      super.onDisable();
   }

   @EventInit
   public void onMotion(EventMotion event) {
      if (mc.player == null || mc.level == null) {
         this.tadpoles.clear();
         return;
      }

      int desiredCount = count.get().intValue();
      double orbitRadius = radius.get();
      double moveSpeed = speed.get();
      Vec3 playerCenter = mc.player.position().add(0.0, mc.player.getBbHeight() * 0.55, 0.0);

      while (this.tadpoles.size() > desiredCount) {
         this.tadpoles.remove(0);
      }

      if (this.tadpoles.size() < desiredCount && (this.tadpoles.isEmpty() || this.spawnTimer.hasReached(120L))) {
         this.tadpoles.add(new TadpoleParticle(playerCenter, orbitRadius, moveSpeed));
         this.spawnTimer.reset();
      }

      Iterator<TadpoleParticle> iterator = this.tadpoles.iterator();

      while (iterator.hasNext()) {
         TadpoleParticle tadpole = iterator.next();
         tadpole.update(playerCenter, orbitRadius, moveSpeed);
         if (tadpole.shouldRemove(playerCenter, orbitRadius)) {
            iterator.remove();
         }
      }
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (this.tadpoles.isEmpty() || mc.player == null) {
         return;
      }

      Immediate immediate = new Immediate();
      PoseStack matrices = event.getMatrixStack();
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      float size = headSize.get().floatValue();
      float tail = tailLength.get().floatValue();
      boolean glowEnabled = bloom.get();

      for (TadpoleParticle tadpole : this.tadpoles) {
         tadpole.render(matrices, immediate, cameraPos, size, tail, glowEnabled);
      }

      immediate.draw();
   }

   private static Vec3 lerp(Vec3 from, Vec3 to, double delta) {
      return from.add(to.subtract(from).scale(delta));
   }

   private static double random(double min, double max) {
      return ThreadLocalRandom.current().nextDouble(min, max);
   }

   private static void drawSolidQuad(VertexConsumer buffer, Matrix4f matrix, float size, int color) {
      float half = size * 0.5F;
      int red = ColorUtil.red(color);
      int green = ColorUtil.green(color);
      int blue = ColorUtil.blue(color);
      int alpha = ColorUtil.alpha(color);
      buffer.addVertex(matrix, -half, -half, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, half, -half, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, half, half, 0.0F).setColor(red, green, blue, alpha);
      buffer.addVertex(matrix, -half, half, 0.0F).setColor(red, green, blue, alpha);
   }

   @Environment(EnvType.CLIENT)
   private static final class TadpoleParticle {
      private Vec3 position;
      private Vec3 renderPosition;
      private Vec3 motion;
      private Vec3 heading;
      private double orbitAngle;
      private double verticalAngle;
      private final double phase;
      private final double orbitRadius;
      private final double orbitSpeed;
      private final double driftStrength;
      private final double liftOffset;
      private final int colorOffset;
      private final int maxAge;
      private int age;

      private TadpoleParticle(Vec3 center, double radius, double moveSpeed) {
         this.orbitAngle = random(0.0, Math.PI * 2.0);
         this.verticalAngle = random(0.0, Math.PI * 2.0);
         this.phase = random(0.0, Math.PI * 2.0);
         this.orbitRadius = random(radius * 0.35, radius * 0.92);
         this.orbitSpeed = random(0.016, 0.032);
         this.driftStrength = random(0.003, 0.0065);
         this.liftOffset = random(0.4, 2.4);
         this.position = center.add(Math.cos(this.orbitAngle) * this.orbitRadius, this.liftOffset, Math.sin(this.orbitAngle) * this.orbitRadius);
         this.renderPosition = this.position;
         this.heading = new Vec3(-Math.sin(this.orbitAngle), 0.08, Math.cos(this.orbitAngle)).normalize();
         this.motion = this.heading.scale(0.018 + moveSpeed * 0.012);
         this.colorOffset = ThreadLocalRandom.current().nextInt(0, 4000);
         this.maxAge = ThreadLocalRandom.current().nextInt(220, 360);
      }

      private void update(Vec3 center, double radius, double moveSpeed) {
         this.age++;
         this.orbitAngle += this.orbitSpeed * (0.7 + moveSpeed * 0.55);
         this.verticalAngle += 0.018 + moveSpeed * 0.012;

         Vec3 orbitCenter = center.add(
            Math.cos(this.orbitAngle) * this.orbitRadius,
            this.liftOffset + Math.sin(this.verticalAngle + this.phase) * 0.85,
            Math.sin(this.orbitAngle) * this.orbitRadius
         );
         Vec3 tangent = new Vec3(
            -Math.sin(this.orbitAngle),
            Math.cos(this.verticalAngle + this.phase) * 0.18,
            Math.cos(this.orbitAngle)
         ).normalize();
         Vec3 desiredPoint = orbitCenter.add(tangent.scale(0.55));
         Vec3 seek = desiredPoint.subtract(this.position);
         if (seek.lengthSqr() > 1.0E-4) {
            this.motion = this.motion.scale(0.88).add(seek.normalize().scale(0.008 + moveSpeed * 0.010));
         }

         Vec3 drift = new Vec3(
            Math.cos(this.phase + this.age * 0.09),
            Math.sin(this.phase * 0.5 + this.age * 0.07) * 0.35,
            Math.sin(this.phase + this.age * 0.09)
         ).scale(this.driftStrength);
         this.motion = this.motion.add(drift).scale(0.97);

         if (this.motion.lengthSqr() > 1.0E-4) {
            this.heading = BloomTadpoles.lerp(this.heading, this.motion.normalize(), 0.16).normalize();
         }

         if (this.position.distanceTo(center) > radius * 1.75) {
            Vec3 pull = center.subtract(this.position).normalize().scale(0.016);
            this.motion = this.motion.add(pull);
         }

         this.position = this.position.add(this.motion);
         this.renderPosition = BloomTadpoles.lerp(this.renderPosition, this.position, 0.22);
      }

      private boolean shouldRemove(Vec3 center, double radius) {
         return this.age > this.maxAge || this.position.distanceTo(center) > radius * 2.6;
      }

      private float alpha() {
         float fadeIn = Math.min(1.0F, this.age / 18.0F);
         float fadeOut = Math.min(1.0F, (this.maxAge - this.age) / 28.0F);
         return Math.max(0.0F, fadeIn * fadeOut);
      }

      private void render(PoseStack matrices, Immediate immediate, Vec3 cameraPos, float baseSize, float tailLength, boolean glowEnabled) {
         float alpha = this.alpha();
         if (alpha <= 0.01F) {
            return;
         }

         Vec3 dir = this.heading.lengthSqr() > 1.0E-4 ? this.heading.normalize() : new Vec3(0.0, 0.0, 1.0);
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
         side = side.normalize();
         Vec3 up = side.cross(dir);
         if (up.lengthSqr() < 1.0E-4) {
            up = new Vec3(0.0, 1.0, 0.0);
         } else {
            up = up.normalize();
         }

         int headColor = ColorUtil.overCol(ColorUtil.fade(this.colorOffset), ColorUtil.getColor(170, 255, 235), 0.22F);
         int tailColor = ColorUtil.overCol(headColor, ColorUtil.getColor(255, 255, 255), 0.18F);
         float cameraYaw = mc.gameRenderer.mainCamera().yRot();
         float cameraPitch = mc.gameRenderer.mainCamera().xRot();

         for (int i = 0; i < 8; i++) {
            float progress = i / 7.0F;
            float inverse = 1.0F - progress;
            double wave = Math.sin(this.phase + this.age * 0.18 + progress * 6.0) * 0.24 * inverse;
            double lift = Math.cos(this.phase * 0.7 + this.age * 0.14 + progress * 3.4) * 0.08 * inverse;
            Vec3 segmentWorld = this.renderPosition
               .subtract(dir.scale(tailLength * progress))
               .add(side.scale(wave))
               .add(up.scale(lift));
            Vec3 segmentRender = segmentWorld.subtract(cameraPos);
            float size = baseSize * (0.2F + inverse * 0.92F);
            float segmentAlpha = alpha * (0.18F + inverse * 0.82F) * inverse;
            int color = i == 0 ? headColor : tailColor;
            renderBillboardBlob(matrices, immediate, segmentRender, size, color, segmentAlpha, cameraYaw, cameraPitch, glowEnabled, i == 0);
         }
      }

      private static void renderBillboardBlob(
         PoseStack matrices,
         Immediate immediate,
         Vec3 offset,
         float size,
         int color,
         float alpha,
         float cameraYaw,
         float cameraPitch,
         boolean glowEnabled,
         boolean head
      ) {
         if (alpha <= 0.01F) {
            return;
         }

         matrices.pushPose();
         matrices.translate(offset.x, offset.y, offset.z);
         matrices.rotate(Axis.YP.rotationDegrees(-cameraYaw));
         matrices.rotate(Axis.XP.rotationDegrees(cameraPitch));
         Matrix4f matrix = matrices.last().pose();
         int coreColor = ColorUtil.multAlpha(color, alpha * (head ? 0.72F : 0.44F));
         drawSolidQuad(immediate.getBuffer(COLOR_LAYER), matrix, size, coreColor);

         if (glowEnabled) {
            float glowScale = head ? 3.3F : 2.3F;
            float softScale = head ? 5.3F : 3.2F;
            int glowAlpha = (int)(alpha * (head ? 120.0F : 62.0F));
            int softAlpha = (int)(alpha * (head ? 84.0F : 38.0F));
            WorldRenderUtil.drawGlow(immediate.getBuffer(GLOW_LAYER), matrix, color, glowAlpha, size * glowScale);
            WorldRenderUtil.drawGlow(immediate.getBuffer(SOFT_GLOW_LAYER), matrix, color, softAlpha, size * softScale);
         }

         matrices.popPose();
      }
   }
}
