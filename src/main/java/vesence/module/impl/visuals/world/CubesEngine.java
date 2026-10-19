package vesence.module.impl.visuals.world;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
 * {@link RenderPipeline}/{@link RenderLayer} (raw-GL из Relevant в 1.21.11 в
 * мире не работает), визуально результат идентичен.
 */
@Environment(EnvType.CLIENT)
public final class CubesEngine {

   private static final MinecraftClient mc = MinecraftClient.getInstance();

   private static final Identifier GLOW_TEX = Identifier.of("vesence", "textures/particle/bloom.png");
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
      SourceFactor.SRC_ALPHA, DestFactor.ONE, SourceFactor.ZERO, DestFactor.ONE
   );

   private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "customworld_cubes_glow"))
         .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(SRC_ALPHA_ADDITIVE)
         .build()
   );
   private static final RenderLayer GLOW_LAYER = RenderLayer.of(
      "customworld_cubes_glow", RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(4096).translucent().texture("Sampler0", GLOW_TEX).build()
   );

   private static final RenderPipeline FACE_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "customworld_cubes_face"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .build()
   );
   private static final RenderLayer FACE_LAYER = RenderLayer.of(
      "customworld_cubes_face", RenderSetup.builder(FACE_PIPELINE).expectedBufferSize(4096).translucent().build()
   );

   private static final RenderPipeline TRI_FACE_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "customworld_cubes_tri_face"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.TRIANGLES)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.TRANSLUCENT)
         .build()
   );
   private static final RenderLayer TRI_FACE_LAYER = RenderLayer.of(
      "customworld_cubes_tri_face", RenderSetup.builder(TRI_FACE_PIPELINE).expectedBufferSize(4096).translucent().build()
   );

   private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "customworld_cubes_line"))
         .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(SRC_ALPHA_ADDITIVE)
         .build()
   );
   private static final RenderLayer LINE_LAYER = RenderLayer.of(
      "customworld_cubes_line", RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(4096).translucent().build()
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
   private Vec3d spawnAnchor = null;
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

   public void onRender3D(MatrixStack ms, Camera camera) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      boolean attackPressed = mc.options.attackKey.isPressed();
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

      Vec3d origin = camera.getCameraPos();
      float yaw = (float) Math.toRadians(camera.getYaw());
      float pitch = (float) Math.toRadians(camera.getPitch());

      double dirX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
      double dirY = -MathHelper.sin(pitch);
      double dirZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);

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
      Vec3d anchor = this.ensureAnchor();

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
   private Vec3d ensureAnchor() {
      Vec3d playerPos = mc.player.getEntityPos();
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

   private void renderCubes(MatrixStack ms, Camera camera) {
      Vec3d cam = camera.getCameraPos();
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
      float yaw = (float) Math.toRadians(camera.getYaw());
      float pitch = (float) Math.toRadians(camera.getPitch());
      double lookX = -MathHelper.sin(yaw) * MathHelper.cos(pitch);
      double lookY = -MathHelper.sin(pitch);
      double lookZ = MathHelper.cos(yaw) * MathHelper.cos(pitch);

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

      Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();

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

   private void drawGlowBatch(MatrixStack ms, Camera camera, Vec3d cam, float s, float glow, Immediate immediate) {
      VertexConsumer builder = immediate.getBuffer(GLOW_LAYER);

      for (int particleIndex = 0, sz = this.visibleCubes.size(); particleIndex < sz; particleIndex++) {
         CubeParticle p = this.visibleCubes.get(particleIndex);
         float alpha = p.renderAlpha;
         this.applyColor(p);

         ms.push();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camera.getYaw()));
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));

         Matrix4f matrix = ms.peek().getPositionMatrix();
         for (int i = 0; i < 3; i++) {
            float scale = s * GLOW_SCALES[i] * glow;
            int a = (int) (alpha * GLOW_ALPHA_SCALES[i] * glow * 255.0F);
            a = MathHelper.clamp(a, 0, 255);
            float hs = scale * 0.5F;

            builder.vertex(matrix, -hs, hs, 0).color(this.cr, this.cg, this.cb, a).texture(0F, 1F);
            builder.vertex(matrix, hs, hs, 0).color(this.cr, this.cg, this.cb, a).texture(1F, 1F);
            builder.vertex(matrix, hs, -hs, 0).color(this.cr, this.cg, this.cb, a).texture(1F, 0F);
            builder.vertex(matrix, -hs, -hs, 0).color(this.cr, this.cg, this.cb, a).texture(0F, 0F);
         }
         ms.pop();
      }
   }

   /** Кривая прозрачности 1:1 из Relevant: lifePct × fadeIn(20 тиков). */
   private float getAlpha(CubeParticle p) {
      float lifePct = MathHelper.clamp((float) p.life / p.maxLife, 0.0F, 1.0F);
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

   private void drawCubeFacesBatch(MatrixStack ms, Vec3d cam, float s, Immediate immediate) {
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
         ms.push();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
         ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
         ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
         this.appendCubeFaces(buffer, ms.peek().getPositionMatrix(), s, alpha);
         ms.pop();
      }
   }

   private void drawTriangleFacesBatch(MatrixStack ms, Vec3d cam, float s, Immediate immediate) {
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
         ms.push();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
         ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
         ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
         this.appendTriangleFaces(buffer, ms.peek().getPositionMatrix(), s, alpha);
         ms.pop();
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
      buffer.vertex(m, -s, -s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, -s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, s, s).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, s, -s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, -s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, s, -s).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, -s, s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, s, -s).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, -s, -s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, -s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, -s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, -s, s).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, s, -s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, -s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, s, s, s).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, -s, -s, -s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, -s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, s, s).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -s, s, -s).color(this.cr, this.cg, this.cb, a);
   }

   private void appendTriangleFaces(VertexConsumer buffer, Matrix4f m, float s, int a) {
      float top = s;
      float bottom = -s;
      float halfBase = s * 0.866F;

      buffer.vertex(m, 0, top, 0).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -halfBase, bottom, halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, halfBase, bottom, halfBase).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, 0, top, 0).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, halfBase, bottom, halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, halfBase, bottom, -halfBase).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, 0, top, 0).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, halfBase, bottom, -halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -halfBase, bottom, -halfBase).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, 0, top, 0).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -halfBase, bottom, -halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -halfBase, bottom, halfBase).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, -halfBase, bottom, halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, halfBase, bottom, halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, halfBase, bottom, -halfBase).color(this.cr, this.cg, this.cb, a);

      buffer.vertex(m, -halfBase, bottom, halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, halfBase, bottom, -halfBase).color(this.cr, this.cg, this.cb, a);
      buffer.vertex(m, -halfBase, bottom, -halfBase).color(this.cr, this.cg, this.cb, a);
   }

   private void drawCubeDashedEdgesBatch(MatrixStack ms, Vec3d cam, float s, Immediate immediate) {
      VertexConsumer buf = immediate.getBuffer(LINE_LAYER);
      for (int i = 0, sz = this.visibleCubes.size(); i < sz; i++) {
         CubeParticle p = this.visibleCubes.get(i);
         int alpha = MathHelper.clamp((int) (p.renderAlpha * 255.0F), 0, 255);

         this.applyColor(p);
         ms.push();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
         ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
         ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
         this.appendCubeDashedEdges(buf, ms.peek().getPositionMatrix(), s, alpha);
         ms.pop();
      }
   }

   private void drawTriangleDashedEdgesBatch(MatrixStack ms, Vec3d cam, float s, Immediate immediate) {
      VertexConsumer buf = immediate.getBuffer(LINE_LAYER);
      for (int i = 0, sz = this.visibleCubes.size(); i < sz; i++) {
         CubeParticle p = this.visibleCubes.get(i);
         int alpha = MathHelper.clamp((int) (p.renderAlpha * 255.0F), 0, 255);

         this.applyColor(p);
         ms.push();
         ms.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
         ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(p.rotX));
         ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(p.rotY));
         ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(p.rotZ));
         this.appendTriangleDashedEdges(buf, ms.peek().getPositionMatrix(), s, alpha);
         ms.pop();
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
      float len = MathHelper.sqrt(dx * dx + dy * dy + dz * dz);

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
            buf.vertex(mat, x1 + nx * pos, y1 + ny * pos, z1 + nz * pos).color(r, g, b, a);
            buf.vertex(mat, x1 + nx * end, y1 + ny * end, z1 + nz * end).color(r, g, b, a);
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

   private CubeParticle spawnCube(Vec3d anchor) {
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
         vx = -MathHelper.sin((float) Math.toRadians(yaw)) * vel;
         vz = MathHelper.cos((float) Math.toRadians(yaw)) * vel;
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
