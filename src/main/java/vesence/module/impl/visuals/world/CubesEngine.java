package vesence.module.impl.visuals.world;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Полный порт Relevant {@code Cubes} в vesence.
 * <p>
 * Перенесены 1:1: якорная система спавна (облако не «летит» за игроком, а
 * стоит на месте и снэпается при дальнем перемещении), object pool частиц,
 * настройка «Дальность», glow (bloom.png, 3 слоя), формы (Кубы/Треугольники),
 * пунктирные рёбра, импульс от удара по перекрестью, кривая alpha из Relevant.
 * <p>
 * Форма «Лого» намеренно НЕ переносится. Рендер использует
 * {@link RenderPipeline}/{@link RenderType} (raw-GL из Relevant в 1.21.11 в
 * мире не работает), визуально результат идентичен.
 */
@Environment(EnvType.CLIENT)
public final class CubesEngine {

   private static final Minecraft mc = Minecraft.getInstance();

   private static final Identifier GLOW_TEX = Identifier.fromNamespaceAndPath("vesence", "textures/particle/bloom.png");
   private static final float PARTICLE_SIZE = 0.18F;
   private static final float PARTICLE_SPEED = 0.25F;
   private static final float GLOW_INTENSITY = 1.7F;
   /** Расстояние² при котором anchor снэпается к новой позиции игрока. 30² = 900. */
   private static final double ANCHOR_SNAP_DISTANCE_SQ = 900.0;

   private static final byte[][] CUBE_EDGES = {
      {-1, -1, -1, 1, -1, -1}, {1, -1, -1, 1, -1, 1}, {1, -1, 1, -1, -1, 1}, {-1, -1, 1, -1, -1, -1},
      {-1, 1, -1, 1, 1, -1}, {1, 1, -1, 1, 1, 1}, {1, 1, 1, -1, 1, 1}, {-1, 1, 1, -1, 1, -1},
      {-1, -1, -1, -1, 1, -1}, {1, -1, -1, 1, 1, -1}, {1, -1, 1, 1, 1, 1}, {-1, -1, 1, -1, 1, 1}
   };
   private static final byte[][] TRIANGLE_EDGES = {
      {0, 1}, {0, 2}, {0, 3}, {0, 4},
      {1, 2}, {2, 3}, {3, 4}, {4, 1}
   };
   private static final float[] GLOW_SCALES = {10.0F, 6.0F, 3.5F};
   private static final float[] GLOW_ALPHA_SCALES = {0.06F, 0.14F, 0.25F};

   /**
    * Блендинг свечения 1:1 из Relevant: {@code blendFuncSeparate(SRC_ALPHA,
    * ONE, ZERO, ONE)}. Цвет умножается на alpha (source-alpha additive) —
    * поэтому мягкие слои с alpha 0.06/0.14/0.25 дают тонкий ореол, а не
    * выбитый в белый шар (что давал бы обычный ADDITIVE = ONE,ONE).
    */
   private static final BlendFunction SRC_ALPHA_ADDITIVE = new BlendFunction(
      BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE
   );

   private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "customworld_cubes_glow"))
         .withVertexShader("core/position_tex_color")
         .withFragmentShader("core/position_tex_color")
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(SRC_ALPHA_ADDITIVE))
         .build()
   );
   // 26.3: RenderSetup больше не имеет expectedBufferSize/translucent — буферы динамические.
   private static final RenderType GLOW_LAYER = RenderType.create(
      "customworld_cubes_glow", RenderSetup.builder(GLOW_PIPELINE).withTexture("Sampler0", GLOW_TEX).createRenderSetup()
   );

   private static final RenderPipeline FACE_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "customworld_cubes_face"))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
         .build()
   );
   private static final RenderType FACE_LAYER = RenderType.create(
      "customworld_cubes_face", RenderSetup.builder(FACE_PIPELINE).createRenderSetup()
   );

   private static final RenderPipeline TRI_FACE_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "customworld_cubes_tri_face"))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
         .build()
   );
   private static final RenderType TRI_FACE_LAYER = RenderType.create(
      "customworld_cubes_tri_face", RenderSetup.builder(TRI_FACE_PIPELINE).createRenderSetup()
   );

   private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "customworld_cubes_line"))
         .withVertexShader("core/position_color")
         .withFragmentShader("core/position_color")
         .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
         .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
         .withColorTargetState(new ColorTargetState(SRC_ALPHA_ADDITIVE))
         .build()
   );
   private static final RenderType LINE_LAYER = RenderType.create(
      "customworld_cubes_line", RenderSetup.builder(LINE_PIPELINE).createRenderSetup()
   );

   public final ModeSetting animation = new ModeSetting("Анимация кубов", "Разлет", "Разлет", "Падение");
   public final ModeSetting shape = new ModeSetting("Форма кубов", "Кубы", "Кубы", "Треугольники");
   public final SliderSetting count = new SliderSetting("Количество кубов", 30.0, 5.0, 100.0, 1.0, false);
   public final SliderSetting size = new SliderSetting("Размер кубов", 1.0, 0.1, 3.0, 0.1, false);
   public final SliderSetting speed = new SliderSetting("Скорость кубов", 1.0, 0.1, 5.0, 0.1, false);
   /**
    * Дальность рендера/спавна (было хард-кодом SPAWN_RADIUS=12). Уменьшение
    * даёт прирост FPS — частицы вне радиуса не апдейтятся и не рендерятся.
    */
   public final SliderSetting renderDistance = new SliderSetting("Дальность кубов", 12.0, 4.0, 24.0, 1.0, false);

   private final List<CubeParticle> cubes = new ArrayList<>();
   private final List<CubeParticle> visibleCubes = new ArrayList<>();
   /** Object pool — переиспользуем умершие частицы вместо new CubeParticle. */
   private final ArrayDeque<CubeParticle> particlePool = new ArrayDeque<>();
   private final Random random = new Random();
   /**
    * Якорь спавна частиц в МИРОВЫХ координатах. Все спавны и cull считаются от
    * якоря, не от текущей позиции игрока — поэтому при ходьбе облако остаётся
    * на месте. Снэпается к игроку при дальнем уходе (телепорт/полёт).
    */
   private Vec3 spawnAnchor = null;
   private boolean lastAttackPressed;
   private int cr, cg, cb;
   private int c0r, c0g, c0b, c1r, c1g, c1b;
   private int updateCounter = 0;

   public Setting[] settings() {
      return new Setting[]{animation, shape, count, size, speed, renderDistance};
   }

   public void clear() {
      this.cubes.clear();
      this.spawnAnchor = null;
   }

   public void onAttack(Camera camera) {
      this.applyHitImpulseFromCrosshair(camera);
   }

   public void onRender3D(PoseStack ms, Camera camera) {
      if (mc.player == null || mc.level == null) {
         return;
      }

      boolean attackPressed = mc.options.keyAttack.isDown();
      if (attackPressed && !this.lastAttackPressed) {
         this.applyHitImpulseFromCrosshair(camera);
      }
      this.lastAttackPressed = attackPressed;

      this.updateCounter++;
      if (this.updateCounter % 2 == 0) {
         this.updateCubes();
      }

      this.renderCubes(ms, camera);
   }

   private void applyHitImpulseFromCrosshair(Camera camera) {
      if (this.cubes.isEmpty() || camera == null) {
         return;
      }

      Vec3 origin = camera.position();
      float yaw = (float) Math.toRadians(camera.yRot());
      float pitch = (float) Math.toRadians(camera.xRot());

      double dirX = -Mth.sin(yaw) * Mth.cos(pitch);
      double dirY = -Mth.sin(pitch);
      double dirZ = Mth.cos(yaw) * Mth.cos(pitch);

      CubeParticle best = null;
      double bestT = Double.MAX_VALUE;

      for (int i = 0, sz = this.cubes.size(); i < sz; i++) {
         CubeParticle p = this.cubes.get(i);
         double opX = p.x - origin.x;
         double opY = p.y - origin.y;
         double opZ = p.z - origin.z;
         double t = opX * dirX + opY * dirY + opZ * dirZ;

         if (t < 0.0 || t > 128.0) {
            continue;
         }

         double closestX = origin.x + dirX * t;
         double closestY = origin.y + dirY * t;
         double closestZ = origin.z + dirZ * t;
         double dx = p.x - closestX;
         double dy = p.y - closestY;
         double dz = p.z - closestZ;
         double distSq = dx * dx + dy * dy + dz * dz;

         if (distSq > 1.32 || t >= bestT) {
            continue;
         }
         bestT = t;
         best = p;
      }

      if (best != null) {
         double force = 0.08 * this.speed.get();
         best.vx += dirX * force;
         best.vy += dirY * force + 0.02;
         best.vz += dirZ * force;
      }
   }

   private void updateCubes() {
      // Anchor — спавн и cull считаются от якоря, не от player.getPos(),
      // поэтому при ходьбе облако остаётся на месте.
      Vec3 anchor = this.ensureAnchor();

      int target = (int) this.count.get().doubleValue();
      int currentSize = this.cubes.size();

      if (currentSize < target) {
         int toAdd = Math.min(target - currentSize, 5);
         for (int i = 0; i < toAdd; i++) {
            this.cubes.add(this.spawnCube(anchor));
         }
      } else if (currentSize > target) {
         // Излишек возвращаем в пул, не выбрасываем.
         for (int i = currentSize - 1; i >= target; i--) {
            CubeParticle removed = this.cubes.remove(i);
            if (removed != null) this.particlePool.offerFirst(removed);
         }
      }

      float spd = PARTICLE_SPEED * this.speed.get().floatValue();
      float maxR = this.renderDistance.get().floatValue();
      boolean falling = this.animation.is("Падение");
      double maxRSq = maxR * maxR * 6.25;

      for (int i = this.cubes.size() - 1; i >= 0; i--) {
         CubeParticle p = this.cubes.get(i);

         if (falling) {
            p.wobblePhase += 0.06F * spd;
            p.x += p.vx * spd + Math.sin(p.wobblePhase + p.wobbleOffset) * 0.0024F * spd;
            p.y += p.vy * spd;
            p.z += p.vz * spd + Math.cos(p.wobblePhase * 0.8F + p.wobbleOffset) * 0.0020F * spd;
            p.vy = Math.max(p.vy - 0.00008F * spd, -0.032F);
            p.rotX += p.rotSpeedX * 0.20F * spd;
            p.rotY += p.rotSpeedY * 0.20F * spd;
            p.rotZ += p.rotSpeedZ * 0.20F * spd;
         } else {
            p.x += p.vx * spd;
            p.y += p.vy * spd;
            p.z += p.vz * spd;
            p.rotX += p.rotSpeedX * spd;
            p.rotY += p.rotSpeedY * spd;
            p.rotZ += p.rotSpeedZ * spd;
            p.vx *= 0.995F;
            p.vy *= 0.995F;
            p.vz *= 0.995F;
         }

         p.life--;

         double dx = p.x - anchor.x;
         double dy = p.y - anchor.y;
         double dz = p.z - anchor.z;
         double distSq = dx * dx + dy * dy + dz * dz;

         if (p.life <= 0 || distSq > maxRSq || (falling && p.y < anchor.y - 2.5)) {
            CubeParticle dead = this.cubes.remove(i);
            if (dead != null) this.particlePool.offerFirst(dead);
            this.cubes.add(this.spawnCube(anchor));
         }
      }
   }

   /**
    * Возвращает текущий якорь, инициализирует при первом вызове и
    * перепривязывает при дальнем уходе игрока (телепорт/полёт).
    */
   private Vec3 ensureAnchor() {
      Vec3 playerPos = mc.player.position();
      if (this.spawnAnchor == null) {
         this.spawnAnchor = playerPos;
         return this.spawnAnchor;
      }
      double dx = playerPos.x - this.spawnAnchor.x;
      double dy = playerPos.y - this.spawnAnchor.y;
      double dz = playerPos.z - this.spawnAnchor.z;
      if (dx * dx + dy * dy + dz * dz > ANCHOR_SNAP_DISTANCE_SQ) {
         this.spawnAnchor = playerPos;
         // Частицы старой зоны больше не нужны — возвращаем в пул.
         for (int i = this.cubes.size() - 1; i >= 0; i--) {
            CubeParticle p = this.cubes.remove(i);
            if (p != null) this.particlePool.offerFirst(p);
         }
      }
      return this.spawnAnchor;
   }

   private void renderCubes(PoseStack ms, Camera camera) {
      Vec3 cam = camera.position();
      float s = PARTICLE_SIZE * this.size.get().floatValue();
      float glow = GLOW_INTENSITY;

      int baseRGB = ColorUtil.fade();
      int col0 = baseRGB, col1 = baseRGB;
      if (Renderer2D.ColorUtil.isDualColor()) {
         col0 = Renderer2D.ColorUtil.getClientColor1();
         col1 = Renderer2D.ColorUtil.getClientColor2();
      }
      this.c0r = ColorUtil.red(col0);  this.c0g = ColorUtil.green(col0);  this.c0b = ColorUtil.blue(col0);
      this.c1r = ColorUtil.red(col1);  this.c1g = ColorUtil.green(col1);  this.c1b = ColorUtil.blue(col1);
      this.cr = this.c0r;  this.cg = this.c0g;  this.cb = this.c0b;

      this.visibleCubes.clear();
      // Динамический render cull: = (renderDistance × 2.5)² — соответствует
      // "запасу" в updateCubes.
      float renderMaxR = this.renderDistance.get().floatValue() * 2.5F;
      double renderCullSq = renderMaxR * renderMaxR;
      float yaw = (float) Math.toRadians(camera.yRot());
      float pitch = (float) Math.toRadians(camera.xRot());
      double lookX = -Mth.sin(yaw) * Mth.cos(pitch);
      double lookY = -Mth.sin(pitch);
      double lookZ = Mth.cos(yaw) * Mth.cos(pitch);

      for (int i = 0, sz = this.cubes.size(); i < sz; i++) {
         CubeParticle p = this.cubes.get(i);
         double dx = p.x - cam.x;
         double dy = p.y - cam.y;
         double dz = p.z - cam.z;
         double distSq = dx * dx + dy * dy + dz * dz;

         if (distSq > renderCullSq) {
            continue;
         }
         if (dx * lookX + dy * lookY + dz * lookZ < -1.0) {
            continue;
         }
         p.renderAlpha = this.getAlpha(p);
         if (p.renderAlpha < 0.01F) {
            continue;
         }

         this.visibleCubes.add(p);
      }

      if (this.visibleCubes.isEmpty()) {
         return;
      }

      Immediate immediate = new Immediate();

      this.drawGlowBatch(ms, camera, cam, s, glow, immediate);

      boolean isCubes = this.shape.is("Кубы");
      boolean isTriangles = this.shape.is("Треугольники");

      if (isCubes) {
         this.drawCubeFacesBatch(ms, cam, s, immediate);
         this.drawCubeDashedEdgesBatch(ms, cam, s, immediate);
      } else if (isTriangles) {
         this.drawTriangleFacesBatch(ms, cam, s, immediate);
         this.drawTriangleDashedEdgesBatch(ms, cam, s, immediate);
      }

      immediate.draw();
   }

   private void drawGlowBatch(PoseStack ms, Camera camera, Vec3 cam, float s, float glow, Immediate immediate) {
      VertexConsumer builder = immediate.getBuffer(GLOW_LAYER);

      for (int particleIndex = 0, sz = this.visibleCubes.size(); particleIndex < sz; particleIndex++) {
         CubeParticle p = this.visibleCubes.get(particleIndex);
         float alpha = p.renderAlpha;
         this.applyColor(p);

         ms.pushPose();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.rotate(Axis.YP.rotationDegrees(-camera.yRot()));
         ms.rotate(Axis.XP.rotationDegrees(camera.xRot()));

         Matrix4f matrix = ms.last().pose();
         for (int i = 0; i < 3; i++) {
            float scale = s * GLOW_SCALES[i] * glow;
            int a = (int) (alpha * GLOW_ALPHA_SCALES[i] * glow * 255.0F);
            a = Mth.clamp(a, 0, 255);
            float hs = scale * 0.5F;

            builder.addVertex(matrix, -hs, hs, 0).setColor(this.cr, this.cg, this.cb, a).setUv(0F, 1F);
            builder.addVertex(matrix, hs, hs, 0).setColor(this.cr, this.cg, this.cb, a).setUv(1F, 1F);
            builder.addVertex(matrix, hs, -hs, 0).setColor(this.cr, this.cg, this.cb, a).setUv(1F, 0F);
            builder.addVertex(matrix, -hs, -hs, 0).setColor(this.cr, this.cg, this.cb, a).setUv(0F, 0F);
         }
         ms.popPose();
      }
   }

   /** Кривая прозрачности 1:1 из Relevant: lifePct × fadeIn(20 тиков). */
   private float getAlpha(CubeParticle p) {
      float lifePct = Mth.clamp((float) p.life / p.maxLife, 0.0F, 1.0F);
      float fadeIn = Math.min(1.0F, (p.maxLife - p.life) / 20.0F);
      return lifePct * fadeIn;
   }

   /** Активный цвет частицы: половина группы 0, половина группы 1 (для двухцветной темы). */
   private void applyColor(CubeParticle p) {
      if (p.colorGroup == 1) {
         this.cr = this.c1r; this.cg = this.c1g; this.cb = this.c1b;
      } else {
         this.cr = this.c0r; this.cg = this.c0g; this.cb = this.c0b;
      }
   }

   private void drawCubeFacesBatch(PoseStack ms, Vec3 cam, float s, Immediate immediate) {
      if (!this.hasFaceRenderableParticles()) {
         return;
      }
      VertexConsumer buffer = immediate.getBuffer(FACE_LAYER);
      for (int i = 0, sz = this.visibleCubes.size(); i < sz; i++) {
         CubeParticle p = this.visibleCubes.get(i);
         int alpha = (int) (p.renderAlpha * 0.4F * 255.0F);
         if (alpha < 3) {
            continue;
         }

         this.applyColor(p);
         ms.pushPose();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.rotate(Axis.XP.rotationDegrees(p.rotX));
         ms.rotate(Axis.YP.rotationDegrees(p.rotY));
         ms.rotate(Axis.ZP.rotationDegrees(p.rotZ));
         this.appendCubeFaces(buffer, ms.last().pose(), s, alpha);
         ms.popPose();
      }
   }

   private void drawTriangleFacesBatch(PoseStack ms, Vec3 cam, float s, Immediate immediate) {
      if (!this.hasFaceRenderableParticles()) {
         return;
      }
      VertexConsumer buffer = immediate.getBuffer(TRI_FACE_LAYER);
      for (int i = 0, sz = this.visibleCubes.size(); i < sz; i++) {
         CubeParticle p = this.visibleCubes.get(i);
         int alpha = (int) (p.renderAlpha * 0.4F * 255.0F);
         if (alpha < 3) {
            continue;
         }

         this.applyColor(p);
         ms.pushPose();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.rotate(Axis.XP.rotationDegrees(p.rotX));
         ms.rotate(Axis.YP.rotationDegrees(p.rotY));
         ms.rotate(Axis.ZP.rotationDegrees(p.rotZ));
         this.appendTriangleFaces(buffer, ms.last().pose(), s, alpha);
         ms.popPose();
      }
   }

   private boolean hasFaceRenderableParticles() {
      for (int i = 0, sz = this.visibleCubes.size(); i < sz; i++) {
         if (this.visibleCubes.get(i).renderAlpha >= 0.025F) {
            return true;
         }
      }
      return false;
   }

   private void appendCubeFaces(VertexConsumer buffer, Matrix4f m, float s, int a) {
      buffer.addVertex(m, -s, -s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, -s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, s, s).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, s, -s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, -s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, s, -s).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, -s, s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, s, -s).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, -s, -s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, -s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, -s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, -s, s).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, s, -s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, -s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, s, s, s).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, -s, -s, -s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, -s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, s, s).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -s, s, -s).setColor(this.cr, this.cg, this.cb, a);
   }

   private void appendTriangleFaces(VertexConsumer buffer, Matrix4f m, float s, int a) {
      float top = s;
      float bottom = -s;
      float halfBase = s * 0.866F;

      buffer.addVertex(m, 0, top, 0).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -halfBase, bottom, halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, halfBase, bottom, halfBase).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, 0, top, 0).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, halfBase, bottom, halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, halfBase, bottom, -halfBase).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, 0, top, 0).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, halfBase, bottom, -halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -halfBase, bottom, -halfBase).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, 0, top, 0).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -halfBase, bottom, -halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -halfBase, bottom, halfBase).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, -halfBase, bottom, halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, halfBase, bottom, halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, halfBase, bottom, -halfBase).setColor(this.cr, this.cg, this.cb, a);

      buffer.addVertex(m, -halfBase, bottom, halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, halfBase, bottom, -halfBase).setColor(this.cr, this.cg, this.cb, a);
      buffer.addVertex(m, -halfBase, bottom, -halfBase).setColor(this.cr, this.cg, this.cb, a);
   }

   private void drawCubeDashedEdgesBatch(PoseStack ms, Vec3 cam, float s, Immediate immediate) {
      VertexConsumer buf = immediate.getBuffer(LINE_LAYER);
      for (int i = 0, sz = this.visibleCubes.size(); i < sz; i++) {
         CubeParticle p = this.visibleCubes.get(i);
         int alpha = Mth.clamp((int) (p.renderAlpha * 255.0F), 0, 255);

         this.applyColor(p);
         ms.pushPose();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.rotate(Axis.XP.rotationDegrees(p.rotX));
         ms.rotate(Axis.YP.rotationDegrees(p.rotY));
         ms.rotate(Axis.ZP.rotationDegrees(p.rotZ));
         this.appendCubeDashedEdges(buf, ms.last().pose(), s, alpha);
         ms.popPose();
      }
   }

   private void drawTriangleDashedEdgesBatch(PoseStack ms, Vec3 cam, float s, Immediate immediate) {
      VertexConsumer buf = immediate.getBuffer(LINE_LAYER);
      for (int i = 0, sz = this.visibleCubes.size(); i < sz; i++) {
         CubeParticle p = this.visibleCubes.get(i);
         int alpha = Mth.clamp((int) (p.renderAlpha * 255.0F), 0, 255);

         this.applyColor(p);
         ms.pushPose();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.rotate(Axis.XP.rotationDegrees(p.rotX));
         ms.rotate(Axis.YP.rotationDegrees(p.rotY));
         ms.rotate(Axis.ZP.rotationDegrees(p.rotZ));
         this.appendTriangleDashedEdges(buf, ms.last().pose(), s, alpha);
         ms.popPose();
      }
   }

   private void appendCubeDashedEdges(VertexConsumer buf, Matrix4f mat, float s, int alpha) {
      int er = Math.min(255, (int) (this.cr * 1.5F));
      int eg = Math.min(255, (int) (this.cg * 1.5F));
      int eb = Math.min(255, (int) (this.cb * 1.5F));

      float dashLen = s * 0.3F;
      float gapLen = s * 0.25F;

      for (byte[] edge : CUBE_EDGES) {
         float x1 = edge[0] * s;
         float y1 = edge[1] * s;
         float z1 = edge[2] * s;
         float x2 = edge[3] * s;
         float y2 = edge[4] * s;
         float z2 = edge[5] * s;

         this.appendDashedLine(buf, mat, x1, y1, z1, x2, y2, z2, er, eg, eb, alpha, dashLen, gapLen);
      }
   }

   private void appendTriangleDashedEdges(VertexConsumer buf, Matrix4f mat, float s, int alpha) {
      int er = Math.min(255, (int) (this.cr * 1.5F));
      int eg = Math.min(255, (int) (this.cg * 1.5F));
      int eb = Math.min(255, (int) (this.cb * 1.5F));

      float dashLen = s * 0.3F;
      float gapLen = s * 0.25F;

      float top = s;
      float bottom = -s;
      float halfBase = s * 0.866F;

      for (byte[] edge : TRIANGLE_EDGES) {
         float x1 = this.trianglePointX(edge[0], halfBase);
         float y1 = edge[0] == 0 ? top : bottom;
         float z1 = this.trianglePointZ(edge[0], halfBase);
         float x2 = this.trianglePointX(edge[1], halfBase);
         float y2 = edge[1] == 0 ? top : bottom;
         float z2 = this.trianglePointZ(edge[1], halfBase);

         this.appendDashedLine(buf, mat, x1, y1, z1, x2, y2, z2, er, eg, eb, alpha, dashLen, gapLen);
      }
   }

   private void appendDashedLine(VertexConsumer buf, Matrix4f mat, float x1, float y1, float z1, float x2, float y2, float z2,
                                 int r, int g, int b, int a, float dashLen, float gapLen) {
      float dx = x2 - x1;
      float dy = y2 - y1;
      float dz = z2 - z1;
      float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);

      if (len < 0.001F) {
         return;
      }

      float nx = dx / len;
      float ny = dy / len;
      float nz = dz / len;

      float pos = 0;
      boolean drawing = true;

      while (pos < len) {
         float segLen = drawing ? dashLen : gapLen;
         float end = Math.min(pos + segLen, len);

         if (drawing) {
            buf.addVertex(mat, x1 + nx * pos, y1 + ny * pos, z1 + nz * pos).setColor(r, g, b, a);
            buf.addVertex(mat, x1 + nx * end, y1 + ny * end, z1 + nz * end).setColor(r, g, b, a);
         }

         pos = end;
         drawing = !drawing;
      }
   }

   private float trianglePointX(int index, float halfBase) {
      return switch (index) {
         case 1, 4 -> -halfBase;
         case 2, 3 -> halfBase;
         default -> 0.0F;
      };
   }

   private float trianglePointZ(int index, float halfBase) {
      return switch (index) {
         case 1, 2 -> halfBase;
         case 3, 4 -> -halfBase;
         default -> 0.0F;
      };
   }

   private CubeParticle spawnCube(Vec3 anchor) {
      float r = this.renderDistance.get().floatValue();
      boolean falling = this.animation.is("Падение");
      int life = falling ? 260 + this.random.nextInt(220) : 420 + this.random.nextInt(420);

      // Спавн от ЯКОРЯ, не от player.getPos() — облако не «летит» при ходьбе.
      double x = anchor.x + (this.random.nextDouble() * 2 - 1) * r;
      double y = falling
         ? anchor.y + 4.0 + this.random.nextDouble() * (r * 0.55)
         : anchor.y + 2.0 + this.random.nextDouble() * (r * 0.8);
      double z = anchor.z + (this.random.nextDouble() * 2 - 1) * r;

      float speedMult = this.speed.get().floatValue();
      float vx, vy, vz;

      if (falling) {
         vx = (this.random.nextFloat() - 0.5F) * 0.008F * speedMult;
         vy = (-0.012F - this.random.nextFloat() * 0.012F) * speedMult;
         vz = (this.random.nextFloat() - 0.5F) * 0.008F * speedMult;
      } else {
         float yaw = this.random.nextFloat() * 360F;
         float vel = (0.010F + this.random.nextFloat() * 0.020F) * speedMult;
         vx = -Mth.sin((float) Math.toRadians(yaw)) * vel;
         vz = Mth.cos((float) Math.toRadians(yaw)) * vel;
         vy = (this.random.nextFloat() - 0.5F) * 0.010F * speedMult;
      }

      // Object pool — берём умершую частицу или создаём новую если пул пуст.
      CubeParticle particle = this.particlePool.pollFirst();
      if (particle == null) {
         particle = new CubeParticle();
      }
      particle.colorGroup = this.random.nextInt(2);
      particle.init(x, y, z, vx, vy, vz,
         this.random.nextFloat() * 360, this.random.nextFloat() * 360, this.random.nextFloat() * 360,
         (this.random.nextFloat() - 0.5F) * 1.5F, (this.random.nextFloat() - 0.5F) * 1.5F, (this.random.nextFloat() - 0.5F) * 1.5F,
         life, (float) (this.random.nextDouble() * Math.PI * 2), this.random.nextFloat() * 10F);
      return particle;
   }

   @Environment(EnvType.CLIENT)
   private static final class CubeParticle {
      double x, y, z;
      float vx, vy, vz, rotX, rotY, rotZ, rotSpeedX, rotSpeedY, rotSpeedZ, wobblePhase, wobbleOffset;
      float renderAlpha;
      int life, maxLife;
      int colorGroup;

      CubeParticle() {
         // No-args ctor для object pool — заполняется через init(...).
      }

      void init(double x, double y, double z, float vx, float vy, float vz, float rotX, float rotY, float rotZ,
                float rotSpeedX, float rotSpeedY, float rotSpeedZ, int life, float wobblePhase, float wobbleOffset) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.vx = vx;
         this.vy = vy;
         this.vz = vz;
         this.rotX = rotX;
         this.rotY = rotY;
         this.rotZ = rotZ;
         this.rotSpeedX = rotSpeedX;
         this.rotSpeedY = rotSpeedY;
         this.rotSpeedZ = rotSpeedZ;
         this.life = this.maxLife = life;
         this.wobblePhase = wobblePhase;
         this.wobbleOffset = wobbleOffset;
         this.renderAlpha = 0.0F;
      }
   }
}
