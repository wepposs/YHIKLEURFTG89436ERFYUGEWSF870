package vesence.module.impl.visuals.world;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import vesence.module.impl.visuals.Immediate;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

@Environment(EnvType.CLIENT)
public final class LineGlyphsEngine {

   private static final Minecraft mc = Minecraft.getInstance();

   private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloom.png");

   private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "customworld_glyphes_lines"))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
         .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
         .build()
   );
   // 26.3: RenderSetup больше не имеет expectedBufferSize/translucent — буферы динамические.
   private static final RenderType LINE_LAYER = RenderType.create(
      "customworld_glyphes_lines", RenderSetup.builder(LINE_PIPELINE).createRenderSetup()
   );

   private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "customworld_glyphes_glow"))
         .withVertexShader("core/position_tex_color")
         .withFragmentShader("core/position_tex_color")
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
         .build()
   );
   private static final RenderType GLOW_LAYER = RenderType.create(
      "customworld_glyphes_glow", RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", GLOW_TEXTURE).createRenderSetup()
   );

   private static final Vec3i[] AXIS_DIRECTIONS = new Vec3i[]{
      new Vec3i(1, 0, 0),
      new Vec3i(-1, 0, 0),
      new Vec3i(0, 1, 0),
      new Vec3i(0, -1, 0),
      new Vec3i(0, 0, 1),
      new Vec3i(0, 0, -1)
   };

   public final SliderSetting glyphsCount = new SliderSetting("Количество линий", 70.0, 10.0, 200.0, 1.0, false);
   public final BooleanSetting slowSpeed = new BooleanSetting("Медленные линии", false);
   public final BooleanSetting applyStippleLines = new BooleanSetting("Пунктирные линии", false);
   public final SliderSetting stippleStepPixels = new SliderSetting("Шаг пунктира", 3.0, 0.5, 20.0, 0.5, false)
      .hidden(() -> !applyStippleLines.get());
   public final BooleanSetting linesGlowing = new BooleanSetting("Свечение линий", false);

   private final Random rand = new Random(93882L);
   private final List<GlyphPath> glyphs = new ArrayList<>();
   private int colA, colB;

   public Setting[] settings() {
      return new Setting[]{glyphsCount, slowSpeed, applyStippleLines, stippleStepPixels, linesGlowing};
   }

   public void clear() {
      this.glyphs.clear();
   }

   public void onMotion() {
      if (mc.player == null || mc.level == null) {
         this.glyphs.clear();
         return;
      }

      this.updateGlyphs();
      this.maintainGlyphCount();
   }

   public void onRender3D(PoseStack matrices, float partialTicks) {
      if (mc.player == null || mc.level == null || this.glyphs.isEmpty()) {
         return;
      }

      List<GlyphPath> drawable = new ArrayList<>();
      for (GlyphPath glyph : this.glyphs) {
         if (glyph.getAlpha() > 0.01F && glyph.getPointCount() >= 2) {
            drawable.add(glyph);
         }
      }

      if (drawable.isEmpty()) {
         return;
      }

      int base = ColorUtil.fade();
      this.colA = base;
      this.colB = base;
      if (Renderer2D.ColorUtil.isDualColor()) {
         this.colA = Renderer2D.ColorUtil.getClientColor1();
         this.colB = Renderer2D.ColorUtil.getClientColor2();
      }

      Immediate immediate = new Immediate();
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      Matrix4f matrix = matrices.last().pose();

      Quaternionf cameraRotation = mc.gameRenderer.mainCamera().rotation();
      Vector3f cameraRight = new Vector3f(1.0F, 0.0F, 0.0F).rotate(cameraRotation);
      Vector3f cameraUp = new Vector3f(0.0F, 1.0F, 0.0F).rotate(cameraRotation);

      VertexConsumer lineBuffer = immediate.getBuffer(LINE_LAYER);

      boolean dashed = this.applyStippleLines.get();
      float dashStep = Math.max(0.04F, (float) this.stippleStepPixels.get().doubleValue() * 0.08F);
      float gapStep = dashStep * 0.85F;

      for (GlyphPath glyph : drawable) {
         float glyphAlpha = glyph.getAlpha();
         if (glyphAlpha <= 0.01F) {
            continue;
         }

         List<Vec3> points = glyph.getSmoothedPositions(partialTicks);
         int pointCount = points.size();
         if (pointCount < 2) {
            continue;
         }

         for (int i = 0; i < pointCount - 1; i++) {
            Vec3 from = points.get(i).subtract(cameraPos);
            Vec3 to = points.get(i + 1).subtract(cameraPos);
            float segmentAlpha = glyphAlpha * (0.25F + (float) i / (float) pointCount / 1.75F);
            int color = this.themeColor(glyph, segmentAlpha);

            if (dashed) {
               this.putDashedSegment(lineBuffer, matrix, from, to, color, dashStep, gapStep);
            } else {
               this.putSegment(lineBuffer, matrix, from, to, color);
            }
         }

         float markerSize = 0.018F;
         for (int i = 0; i < pointCount; i++) {
            Vec3 pos = points.get(i).subtract(cameraPos);
            float localAlpha = glyphAlpha * (0.25F + (float) i / (float) pointCount / 1.75F);
            int color = this.themeColor(glyph, localAlpha);
            this.putPointCross(lineBuffer, matrix, pos, color, markerSize);
         }
      }

      if (this.linesGlowing.get()) {
         VertexConsumer glowBuffer = immediate.getBuffer(GLOW_LAYER);
         for (GlyphPath glyph : drawable) {
            float glyphAlpha = glyph.getAlpha();
            if (glyphAlpha <= 0.01F) {
               continue;
            }

            List<Vec3> points = glyph.getSmoothedPositions(partialTicks);
            int pointCount = points.size();
            for (int i = 0; i < pointCount; i++) {
               Vec3 pos = points.get(i).subtract(cameraPos);
               float localAlpha = glyphAlpha * (0.25F + (float) i / (float) pointCount / 1.75F);
               int color = this.themeColor(glyph, localAlpha * 0.6F);
               this.putGlow(glowBuffer, matrix, pos, 0.25F, color, cameraRight, cameraUp);
            }
         }
      }

      immediate.draw();
   }

   private int themeColor(GlyphPath glyph, float alphaMul) {
      int base = glyph.colorGroup == 1 ? this.colB : this.colA;
      int alpha = Mth.clamp((int) (Mth.clamp(alphaMul, 0.0F, 1.0F) * 255.0F), 0, 255);
      return ColorUtil.getColor(ColorUtil.red(base), ColorUtil.green(base), ColorUtil.blue(base), alpha);
   }

   private void putSegment(VertexConsumer buffer, Matrix4f matrix, Vec3 from, Vec3 to, int color) {
      this.putColorVertex(buffer, matrix, from, color);
      this.putColorVertex(buffer, matrix, to, color);
   }

   private void putDashedSegment(VertexConsumer buffer, Matrix4f matrix, Vec3 from, Vec3 to, int color, float dashLen, float gapLen) {
      double dx = to.x - from.x;
      double dy = to.y - from.y;
      double dz = to.z - from.z;
      double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (length < 1.0E-4) {
         return;
      }

      double nx = dx / length;
      double ny = dy / length;
      double nz = dz / length;
      double cursor = 0.0;
      boolean draw = true;

      while (cursor < length) {
         double step = draw ? dashLen : gapLen;
         double next = Math.min(length, cursor + step);

         if (draw) {
            Vec3 s = new Vec3(from.x + nx * cursor, from.y + ny * cursor, from.z + nz * cursor);
            Vec3 e = new Vec3(from.x + nx * next, from.y + ny * next, from.z + nz * next);
            this.putColorVertex(buffer, matrix, s, color);
            this.putColorVertex(buffer, matrix, e, color);
         }

         cursor = next;
         draw = !draw;
      }
   }

   private void putPointCross(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int color, float size) {
      this.putColorVertex(buffer, matrix, new Vec3(pos.x - size, pos.y, pos.z), color);
      this.putColorVertex(buffer, matrix, new Vec3(pos.x + size, pos.y, pos.z), color);
      this.putColorVertex(buffer, matrix, new Vec3(pos.x, pos.y - size, pos.z), color);
      this.putColorVertex(buffer, matrix, new Vec3(pos.x, pos.y + size, pos.z), color);
      this.putColorVertex(buffer, matrix, new Vec3(pos.x, pos.y, pos.z - size), color);
      this.putColorVertex(buffer, matrix, new Vec3(pos.x, pos.y, pos.z + size), color);
   }

   private void putGlow(VertexConsumer buffer, Matrix4f matrix, Vec3 center, float size, int color, Vector3f cameraRight, Vector3f cameraUp) {
      double half = size * 0.5;
      Vec3 right = new Vec3(cameraRight.x(), cameraRight.y(), cameraRight.z()).scale(half);
      Vec3 up = new Vec3(cameraUp.x(), cameraUp.y(), cameraUp.z()).scale(half);
      Vec3 v0 = center.subtract(right).subtract(up);
      Vec3 v1 = center.add(right).subtract(up);
      Vec3 v2 = center.add(right).add(up);
      Vec3 v3 = center.subtract(right).add(up);
      this.putTexturedVertex(buffer, matrix, v0, color, 0.0F, 1.0F);
      this.putTexturedVertex(buffer, matrix, v1, color, 1.0F, 1.0F);
      this.putTexturedVertex(buffer, matrix, v2, color, 1.0F, 0.0F);
      this.putTexturedVertex(buffer, matrix, v3, color, 0.0F, 0.0F);
   }

   private void putColorVertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int color) {
      buffer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
         .setColor(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color));
   }

   private void putTexturedVertex(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int color, float u, float v) {
      buffer.addVertex(matrix, (float) pos.x, (float) pos.y, (float) pos.z)
         .setColor(ColorUtil.red(color), ColorUtil.green(color), ColorUtil.blue(color), ColorUtil.alpha(color))
         .setUv(u, v);
   }

   private void updateGlyphs() {
      Iterator<GlyphPath> iterator = this.glyphs.iterator();
      while (iterator.hasNext()) {
         GlyphPath glyph = iterator.next();
         glyph.update();
         if (glyph.isDead()) {
            iterator.remove();
         }
      }
   }

   private void maintainGlyphCount() {
      int targetCount = this.maxGlyphCount();
      int activeCount = this.countActiveGlyphs();

      while (activeCount < targetCount) {
         this.glyphs.add(new GlyphPath(this.randomGlyphSpawnPos(), this.randInt(7, 12)));
         activeCount++;
      }

      if (activeCount > targetCount) {
         for (GlyphPath glyph : this.glyphs) {
            if (activeCount <= targetCount) {
               break;
            }
            if (!glyph.isRemoving()) {
               glyph.setWantToRemove();
               activeCount--;
            }
         }
      }
   }

   private int countActiveGlyphs() {
      int count = 0;
      for (GlyphPath glyph : this.glyphs) {
         if (!glyph.isRemoving()) {
            count++;
         }
      }
      return count;
   }

   private int maxGlyphCount() {
      return Math.max(1, this.glyphsCount.get().intValue());
   }

   private Vec3i randomGlyphSpawnPos() {
      final int minDistance = 6;
      final int maxDistance = 24;
      final int minY = 0;
      final int maxY = 12;

      Vec3 cameraPos = this.getCameraPos();

      for (int attempt = 0; attempt < 16; attempt++) {
         int distance = this.randInt(minDistance, maxDistance + 1);
         float yawBase = mc.player != null ? mc.player.getYRot() : 0.0F;
         float randomYaw = yawBase + this.randFloat(-135.0F, 135.0F);
         float yawRad = (float) Math.toRadians(randomYaw);

         int offsetX = (int) (-(Mth.sin(yawRad) * distance));
         int offsetY = this.randInt(minY, maxY + 1);
         int offsetZ = (int) (Mth.cos(yawRad) * distance);

         Vec3i spawn = new Vec3i(
            (int) Math.floor(cameraPos.x) + offsetX,
            (int) Math.floor(cameraPos.y) + offsetY,
            (int) Math.floor(cameraPos.z) + offsetZ
         );

         if (this.isSpawnPosFree(spawn)) {
            return spawn;
         }
      }

      return new Vec3i(
         (int) Math.floor(cameraPos.x),
         (int) Math.floor(cameraPos.y),
         (int) Math.floor(cameraPos.z)
      );
   }

   private boolean isSpawnPosFree(Vec3i pos) {
      if (mc.level == null) {
         return true;
      }

      BlockPos bp = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
      BlockState state = mc.level.getBlockState(bp);
      if (state.isAir()) {
         return true;
      }
      if (!state.getFluidState().isEmpty()) {
         return false;
      }
      return state.getCollisionShape(mc.level, bp).isEmpty();
   }

   private Vec3 getCameraPos() {
      Camera camera = mc.gameRenderer != null ? mc.gameRenderer.mainCamera() : null;
      if (camera != null) {
         return camera.position();
      }
      return mc.player != null ? mc.player.position() : Vec3.ZERO;
   }

   private Vec3i randomAxisDirection() {
      return AXIS_DIRECTIONS[this.rand.nextInt(AXIS_DIRECTIONS.length)];
   }

   private Vec3i nextOrthogonalDirection(Vec3i previousDirection) {
      for (int i = 0; i < 12; i++) {
         Vec3i candidate = this.randomAxisDirection();
         int dot = candidate.getX() * previousDirection.getX()
            + candidate.getY() * previousDirection.getY()
            + candidate.getZ() * previousDirection.getZ();
         if (dot == 0) {
            return candidate;
         }
      }
      return this.randomAxisDirection();
   }

   private int randInt(int minInclusive, int maxExclusive) {
      if (maxExclusive <= minInclusive) {
         return minInclusive;
      }
      return this.rand.nextInt(maxExclusive - minInclusive) + minInclusive;
   }

   private float randFloat(float minInclusive, float maxInclusive) {
      if (maxInclusive <= minInclusive) {
         return minInclusive;
      }
      return minInclusive + this.rand.nextFloat() * (maxInclusive - minInclusive);
   }

   private float moveAdvanceFromTicks(int ticksSet, int ticksLeft, float partialTicks) {
      if (ticksSet <= 0) {
         return 1.0F;
      }
      float progress = 1.0F - ((float) ticksLeft - partialTicks) / (float) ticksSet;
      return Mth.clamp(progress, 0.0F, 1.0F);
   }

   private static double lerp(double start, double end, double delta) {
      return start + (end - start) * delta;
   }

   @Environment(EnvType.CLIENT)
   private final class GlyphPath {
      private final List<Vec3i> points = new ArrayList<>();
      private final LegacyAnimation alpha = new LegacyAnimation(0.0F, 0.12F);
      private Vec3i lastDirection;
      private int currentStepTicks;
      private int lastStepSet;
      private int stepsLeft;
      private boolean removing;
      private final int colorGroup;

      private GlyphPath(Vec3i spawnPos, int maxSteps) {
         this.points.add(spawnPos);
         this.lastDirection = LineGlyphsEngine.this.randomAxisDirection();
         this.stepsLeft = maxSteps;
         this.colorGroup = LineGlyphsEngine.this.rand.nextInt(2);
      }

      private void update() {
         this.alpha.to = this.removing ? 0.0F : 1.0F;

         if (this.removing) {
            return;
         }

         if (this.stepsLeft <= 0) {
            this.setWantToRemove();
            return;
         }

         if (this.currentStepTicks > 0) {
            this.currentStepTicks -= LineGlyphsEngine.this.slowSpeed.get() ? 1 : 2;
            if (this.currentStepTicks < 0) {
               this.currentStepTicks = 0;
            }
            return;
         }

         Vec3i last = this.points.get(this.points.size() - 1);
         boolean added = false;

         for (int attempt = 0; attempt < 8; attempt++) {
            Vec3i nextDirection = LineGlyphsEngine.this.nextOrthogonalDirection(this.lastDirection);
            int step = LineGlyphsEngine.this.randInt(1, 4);
            Vec3i next = new Vec3i(
               last.getX() + nextDirection.getX() * step,
               last.getY() + nextDirection.getY() * step,
               last.getZ() + nextDirection.getZ() * step
            );

            if (LineGlyphsEngine.this.isSpawnPosFree(next)) {
               this.lastDirection = nextDirection;
               this.lastStepSet = step;
               this.currentStepTicks = step;
               this.points.add(next);
               this.stepsLeft--;
               added = true;
               break;
            }
         }

         if (!added) {
            this.setWantToRemove();
         }
      }

      private List<Vec3> getSmoothedPositions(float partialTicks) {
         List<Vec3> smoothed = new ArrayList<>(this.points.size());
         float moveAdvance = LineGlyphsEngine.this.moveAdvanceFromTicks(this.lastStepSet, this.currentStepTicks, partialTicks);

         for (int i = 0; i < this.points.size(); i++) {
            Vec3i point = this.points.get(i);
            double x = point.getX();
            double y = point.getY();
            double z = point.getZ();

            if (this.points.size() >= 2 && i == this.points.size() - 1) {
               Vec3i previous = this.points.get(this.points.size() - 2);
               x = lerp(previous.getX(), x, moveAdvance);
               y = lerp(previous.getY(), y, moveAdvance);
               z = lerp(previous.getZ(), z, moveAdvance);
            }

            smoothed.add(new Vec3(x, y, z));
         }

         return smoothed;
      }

      private int getPointCount() {
         return this.points.size();
      }

      private float getAlpha() {
         return Mth.clamp(this.alpha.getAnim(), 0.0F, 1.0F);
      }

      private boolean isRemoving() {
         return this.removing;
      }

      private void setWantToRemove() {
         this.removing = true;
      }

      private boolean isDead() {
         return this.removing && this.getAlpha() <= 0.03F;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class LegacyAnimation {
      private long lastTime = System.currentTimeMillis();
      private float anim;
      private float to = 1.0F;
      private final float speed;

      private LegacyAnimation(float anim, float speed) {
         this.anim = anim;
         this.speed = speed;
      }

      private float getAnim() {
         if (Math.abs(this.anim - this.to) < 1.0E-4F) {
            this.anim = this.to;
         }

         int count = (int) (Math.min((float) (System.currentTimeMillis() - this.lastTime), 400.0F) / 5.0F);
         if (count > 0) {
            this.lastTime = System.currentTimeMillis();
         }

         for (int index = 0; index < count; index++) {
            this.anim = (float) LineGlyphsEngine.lerp(this.anim, this.to, this.speed);
         }

         return this.anim;
      }
   }
}
