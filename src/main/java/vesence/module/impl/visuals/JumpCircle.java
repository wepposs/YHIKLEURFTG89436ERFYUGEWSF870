package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import net.minecraft.client.render.MultiBufferSource.Immediate;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.util.math.PoseStack.Entry;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;

@IModule(name = "Jump Circle", description = "Круг под ногами при прыжке", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class JumpCircle extends Module {

   private static final float MAX_LIFETIME_MS = 1850.0F;
   private static final float ROTATION_SPEED = 120.0F;
   private static final float PULSE_SPEED = 7.0F;
   private static final float PULSE_SCALE = 0.06F;
   private static final float PULSE_ALPHA = 0.12F;
   private static final int MAX_CIRCLES = 8;

   private final ModeSetting type = new ModeSetting("Вид", "Первый", "Первый", "Второй");
   private final SliderSetting radius = new SliderSetting("Радиус", 1.85, 0.5, 4.0, 0.1);
   private final SliderSetting speed = new SliderSetting("Скорость", 1.2, 1.0, 5.0, 0.1);
   private final SliderSetting fadeSpeed = new SliderSetting("Скорость исчезновения", 1.5, 1.0, 5.0, 0.5);

   private final List<CircleData> circles = new ArrayList<>();
   private ByteBufferBuilder circleAllocator = null;
   private final ResourceLocation circleTexture = ResourceLocation.of("vesence", "textures/world/jumpcircle.png");
   private final ResourceLocation circleTextureSecond = ResourceLocation.of("vesence", "textures/world/jumpcircle2.png");
   private boolean wasOnGround = true;

   private static final RenderPipeline JUMP_CIRCLE_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
               .withLocation(ResourceLocation.of("vesence", "pipeline/world/jump_circle"))
               .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());

   public JumpCircle() {
      this.addSettings(new Setting[]{this.type, this.radius, this.speed, this.fadeSpeed});
   }

   @Override
   public void onEnable() {
      if (mc.player != null) {
         this.wasOnGround = mc.player.onGround();
      }
      super.onEnable();
   }

   @Override
   public void onDisable() {
      this.circles.clear();
      if (circleAllocator != null) { circleAllocator.close(); circleAllocator = null; }
      super.onDisable();
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.level == null) {
         return;
      }
      boolean isOnGround = mc.player.onGround();
      if (this.wasOnGround && !isOnGround) {
         Vec3 pos = new Vec3(mc.player.getX(), Math.floor(mc.player.getY()) + 0.001, mc.player.getZ());
         this.circles.add(new CircleData(pos, System.currentTimeMillis()));
         while (this.circles.size() > MAX_CIRCLES) {
            this.circles.remove(0);
         }
      }
      this.wasOnGround = isOnGround;

      long now = System.currentTimeMillis();
      float lifeTimeMs = this.getLifeTimeMs();
      Iterator<CircleData> iterator = this.circles.iterator();
      while (iterator.hasNext()) {
         CircleData circle = iterator.next();
         if (now - circle.startTimeMs > (long) lifeTimeMs) {
            iterator.remove();
         }
      }
   }

   @EventInit
   public void onRender(EventRender3D e) {
      if (this.circles.isEmpty()) {
         return;
      }
      long now = System.currentTimeMillis();
      Vec3 cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      PoseStack matrices = e.getMatrixStack();
      ResourceLocation texture = this.type.is("Второй") ? this.circleTextureSecond : this.circleTexture;

      if (circleAllocator == null) {
         circleAllocator = new ByteBufferBuilder(262144);
      }
      Immediate immediate = MultiBufferSource.immediate(circleAllocator);
      try {
         RenderType renderLayer = RenderType.of(
               texture.toString(),
               RenderSetup.builder(JUMP_CIRCLE_PIPELINE)
                     .expectedBufferSize(2048)
                     .translucent()
                     .texture("Sampler0", texture)
                     .build());
         VertexConsumer buffer = immediate.getBuffer(renderLayer);

         for (CircleData circle : this.circles) {
            float progress = this.getProgress(now, circle);
            if (progress >= 1.0F) {
               continue;
            }
            float alpha = this.getAlpha(progress);
            if (alpha <= 0.01F) {
               continue;
            }
            this.renderGlowCircle(matrices, buffer, cameraPos, circle, progress, alpha, now);
         }

         immediate.draw();
      } finally {
         // reused across frames
      }
   }

   private float getLifeTimeMs() {
      return MAX_LIFETIME_MS / (float) Math.max(0.25, this.speed.get());
   }

   private float getProgress(long now, CircleData circle) {
      return (float) (now - circle.startTimeMs) / this.getLifeTimeMs();
   }

   private float getAlpha(float progress) {
      float fade = Mth.clamp(progress * (float) this.fadeSpeed.get().doubleValue(), 0.0F, 1.0F);
      return 1.0F - fade;
   }

   private void renderGlowCircle(PoseStack matrices, VertexConsumer buffer, Vec3 cameraPos, CircleData circle, float progress, float alpha, long now) {
      float spd = (float) this.speed.get().doubleValue();
      float rad = (float) this.radius.get().doubleValue();
      float lifeTimeSec = (float) (now - circle.startTimeMs) / 1000.0F;
      float easedProgress = easeOutCubic(progress);
      float scale = Math.min(easedProgress * rad, rad);
      float rotation = lifeTimeSec * ROTATION_SPEED * spd;
      rotation += (float) Math.sin(progress * Math.PI * 2.0) * 30.0F;
      float pulse = (float) Math.sin(lifeTimeSec * PULSE_SPEED * spd);
      float pulseScale = 1.0F + pulse * PULSE_SCALE;
      float pulseAlpha = Mth.clamp(alpha * (1.0F + pulse * PULSE_ALPHA), 0.0F, 1.0F);
      float alphaBoost = Mth.clamp(pulseAlpha * 1.25F, 0.0F, 1.0F);
      float finalScale = scale * pulseScale;

      int baseTheme = Renderer2D.ColorUtil.getMainColor(1, 1);
      int secondaryTheme = Renderer2D.ColorUtil.getMainColor2(1, 1);
      int colorA = Renderer2D.ColorUtil.replAlpha(baseTheme, (int) (255.0F * alphaBoost));
      int colorB = Renderer2D.ColorUtil.replAlpha(secondaryTheme, (int) (255.0F * alphaBoost));
      int darkAlpha = (int) (255.0F * Mth.clamp(alphaBoost * 0.9F, 0.0F, 1.0F));
      int darkA = Renderer2D.ColorUtil.replAlpha(darken(baseTheme, 0.65F), darkAlpha);
      int darkB = Renderer2D.ColorUtil.replAlpha(darken(secondaryTheme, 0.65F), darkAlpha);

      matrices.pushPose();
      matrices.translate(circle.pos.x - cameraPos.x, circle.pos.y - cameraPos.y, circle.pos.z - cameraPos.z);
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
      Matrix4f matrix = matrices.last().getPositionMatrix();
      float half = finalScale * 0.5F;
      float thickHalf = finalScale * 1.08F * 0.5F;
      // Затемнённый контур под основным слоем + основной слой сверху.
      this.addTexturedQuad(buffer, matrix, -thickHalf, -thickHalf, thickHalf, thickHalf, darkA, darkB);
      this.addTexturedQuad(buffer, matrix, -half, -half, half, half, colorA, colorB);
      matrices.popPose();
   }

   private void addTexturedQuad(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float x2, float y2, int colorA, int colorB) {
      int aR = colorA >> 16 & 0xFF;
      int aG = colorA >> 8 & 0xFF;
      int aB = colorA & 0xFF;
      int aA = colorA >> 24 & 0xFF;
      int bR = colorB >> 16 & 0xFF;
      int bG = colorB >> 8 & 0xFF;
      int bB = colorB & 0xFF;
      int bA = colorB >> 24 & 0xFF;
      buffer.vertex(matrix, x1, y1, 0.0F).texture(0.0F, 1.0F).color(aR, aG, aB, aA);
      buffer.vertex(matrix, x1, y2, 0.0F).texture(0.0F, 0.0F).color(bR, bG, bB, bA);
      buffer.vertex(matrix, x2, y2, 0.0F).texture(1.0F, 0.0F).color(bR, bG, bB, bA);
      buffer.vertex(matrix, x2, y1, 0.0F).texture(1.0F, 1.0F).color(aR, aG, aB, aA);
   }

   private static int darken(int color, float factor) {
      int a = color >> 24 & 0xFF;
      int r = (int) ((color >> 16 & 0xFF) * factor);
      int g = (int) ((color >> 8 & 0xFF) * factor);
      int b = (int) ((color & 0xFF) * factor);
      return a << 24 | r << 16 | g << 8 | b;
   }

   private static float easeOutCubic(float t) {
      float u = 1.0F - t;
      return 1.0F - u * u * u;
   }

   @Environment(EnvType.CLIENT)
   private static final class CircleData {
      private final Vec3 pos;
      private final long startTimeMs;

      private CircleData(Vec3 pos, long startTimeMs) {
         this.pos = pos;
         this.startTimeMs = startTimeMs;
      }
   }
}
