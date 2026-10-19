package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.Mathf;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontRegistry;

@IModule(name = "Projectile", description = "Траектория снарядов: жемчуг, стрелы, трезубец, предметы", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Projectile extends Module {

   private static final Identifier BLOOM_TEXTURE = Identifier.of("vesence", "textures/world/dashbloom.png");

   private static final RenderPipeline BLOOM_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
               .withLocation(Identifier.of("vesence", "projectile_bloom"))
               .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.LIGHTNING)
               .build());
   private static final RenderLayer BLOOM_LAYER = RenderLayer.of(
         "projectile_bloom",
         RenderSetup.builder(BLOOM_PIPELINE)
               .expectedBufferSize(8192)
               .translucent()
               .texture("Sampler0", BLOOM_TEXTURE)
               .build());

   private final SliderSetting size = new SliderSetting("Размер", 1.2F, 0.6F, 2.4F, 0.1F, false);
   private final BooleanSetting pearls = new BooleanSetting("Жемчуг", true);
   private final BooleanSetting arrows = new BooleanSetting("Стрелы", true);
   private final BooleanSetting tridents = new BooleanSetting("Трезубец", true);
   private final BooleanSetting items = new BooleanSetting("Падающие предметы", true);

   private final List<ImpactPoint> impactPoints = new ArrayList<>();
   private final java.util.Map<Integer, Anim> animations = new java.util.HashMap<>();
   private final Matrix4f lastProjectionMatrix = new Matrix4f();
   private final Quaternionf lastCameraRotation = new Quaternionf();
   private Vec3d lastCameraPos = Vec3d.ZERO;
   private boolean hasMatrices;

   private BufferAllocator allocator;
   private Immediate immediate;

   public Projectile() {
      this.addSettings(new Setting[]{this.size, this.pearls, this.arrows, this.tridents, this.items});
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (mc.world == null || mc.player == null) return;

      impactPoints.clear();
      hasMatrices = true;
      lastProjectionMatrix.set(event.getProjectionMatrix());
      lastCameraPos = event.getCameraPos();
      lastCameraRotation.set(event.getCameraRotation());

      MatrixStack matrices = event.getMatrixStack();
      Camera camera = mc.gameRenderer.getCamera();
      Vec3d cameraPos = camera.getCameraPos();
      Quaternionf cameraRotation = camera.getRotation();
      float tickDelta = event.getTickDelta();

      if (allocator == null) {
         allocator = new BufferAllocator(262144);
         immediate = VertexConsumerProvider.immediate(allocator);
      }

      try {
         Box searchBox = mc.player.getBoundingBox().expand(128.0D);

         for (Entity entity : mc.world.getEntitiesByClass(Entity.class, searchBox, this::shouldTrack)) {
            ProjectileType type = this.classify(entity);
            if (type == null) continue;

            if (this.hasLanded(entity)) continue;

            List<Vec3d> points = simulate(entity, type, tickDelta);
            if (points.size() < 2) continue;

            float seconds = (points.size() - 1) / 20.0F;
            Vec3d endPos = points.get(points.size() - 1);
            impactPoints.add(new ImpactPoint(endPos, seconds, this.iconFor(entity, type), entity.getId()));

            float quadSize = size.get().floatValue() * 0.2F;
            int color = ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 40);
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            int a = (color >> 24) & 0xFF;

            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

            VertexConsumer buffer = immediate.getBuffer(BLOOM_LAYER);
            for (int i = 0; i < points.size() - 1; i++) {
               Vec3d start = points.get(i);
               Vec3d end = points.get(i + 1);

               int samples = Math.max(2, Math.min(12, (int) Math.ceil(start.distanceTo(end) / Math.max(quadSize * 1.75F, 0.08F))));
               for (int j = 0; j < samples; j++) {
                  Vec3d interp = start.lerp(end, j / (double) samples);

                  matrices.push();
                  matrices.translate(interp.x, interp.y, interp.z);
                  matrices.multiply(cameraRotation);
                  Matrix4f matrix = matrices.peek().getPositionMatrix();
                  addBloomQuad(buffer, matrix, -quadSize, -quadSize, quadSize * 2.0F, quadSize * 2.0F, r, g, b, a);
                  matrices.pop();
               }
            }
            matrices.pop();
         }

         immediate.draw();
      } catch (Exception e) {
         resetBuffer();
      }
   }

   @EventInit
   public void onRender2D(EventScreen event) {
      if (!hasMatrices || mc.player == null) return;

      Renderer2D renderer = event.renderer();
      float guiScale = (float) Mathf.getScaleFactor();
      long now = System.currentTimeMillis();

      java.util.Set<Integer> alive = new java.util.HashSet<>();
      for (ImpactPoint impact : impactPoints) {
         alive.add(impact.entityId);
      }

      java.util.Iterator<java.util.Map.Entry<Integer, Anim>> it = animations.entrySet().iterator();
      while (it.hasNext()) {
         java.util.Map.Entry<Integer, Anim> entry = it.next();
         Anim anim = entry.getValue();
         boolean present = alive.contains(entry.getKey());
         anim.update(now, present);
         if (!present && anim.value <= 0.001F) {
            it.remove();
         }
      }

      for (ImpactPoint impact : impactPoints) {
         Anim anim = animations.computeIfAbsent(impact.entityId, k -> new Anim(now));
         float progress = anim.value;
         if (progress <= 0.001F) continue;

         Vec3d smoothWorld = anim.smooth(impact.pos, now);
         Vec3d screen = worldToScreen(smoothWorld);
         if (screen == null) continue;

         float alpha = MathHelper.clamp(progress, 0.0F, 1.0F);

         boolean hasIcon = impact.icon != null && !impact.icon.isEmpty();

         float cx = (float) screen.x;
         float cy = (float) screen.y;
         float radius = 9.0F * guiScale;

         int circleAlpha = (int) (100 * alpha);
         renderer.blur(cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, 10, 25, alpha);
         renderer.rect(cx - radius, cy - radius, radius * 2.0f, radius * 2.0f, 25, ColorUtil.getColor(0, 0, 0, circleAlpha));

         if (hasIcon) {
            float iconSize = 12 * guiScale;
            DrawContextDraw(event, impact.icon, cx - 0.5f, cy - 0.5f, iconSize, guiScale, alpha);
         }

         String text = formatOneDecimal(impact.seconds) + " сек";
         float textSize = 24;
         float textWidth = renderer.measureText(FontRegistry.SF_MEDIUM, text, textSize).width;
         float textX = cx - textWidth / 2.0F;
         float textY = cy + radius + 6.5f * guiScale;
         int textColor = ColorUtil.replAlpha(-1, (int) (255 * alpha));
         renderer.text(FontRegistry.SF_MEDIUM, textX, textY, textSize, text, textColor);
      }
   }

   private void DrawContextDraw(EventScreen event, ItemStack stack, float cx, float cy, float iconSize, float guiScale, float alpha) {
      net.minecraft.client.gui.DrawContext ctx = event.drawContext();
      if (ctx == null) return;

      float scaledCx = cx / guiScale;
      float scaledCy = cy / guiScale;

      float itemScale = (iconSize / guiScale) / 16.0F;

      ctx.getMatrices().pushMatrix();
      ctx.getMatrices().translate(scaledCx, scaledCy);
      ctx.getMatrices().scale(itemScale, itemScale);
      ctx.drawItem(stack, -8, -8);
      ctx.getMatrices().popMatrix();
   }

   @Override
   public void onDisable() {
      impactPoints.clear();
      animations.clear();
      hasMatrices = false;
      resetBuffer();
      super.onDisable();
   }

   private void resetBuffer() {
      if (allocator != null) {
         try {
            allocator.close();
         } catch (Exception ignored) {
         }
         allocator = null;
         immediate = null;
      }
   }

   private boolean shouldTrack(Entity entity) {
      return entity.isAlive() && this.classify(entity) != null;
   }

   private boolean hasLanded(Entity entity) {
      if (entity.isOnGround()) {
         return true;
      }

      return entity.getVelocity().lengthSquared() < 1.0E-4;
   }

   private ProjectileType classify(Entity entity) {
      if (entity instanceof EnderPearlEntity) {
         return pearls.get() ? ProjectileType.PEARL : null;
      }
      if (entity instanceof net.minecraft.entity.projectile.TridentEntity) {
         return tridents.get() ? ProjectileType.PROJECTILE : null;
      }
      if (entity instanceof PersistentProjectileEntity) {
         return arrows.get() ? ProjectileType.PROJECTILE : null;
      }
      if (entity instanceof ItemEntity) {
         return items.get() ? ProjectileType.ITEM : null;
      }
      return null;
   }

   private ItemStack iconFor(Entity entity, ProjectileType type) {
      if (entity instanceof ItemEntity itemEntity) {
         return itemEntity.getStack();
      }
      if (type == ProjectileType.PEARL) {
         return new ItemStack(Items.ENDER_PEARL);
      }
      if (entity instanceof net.minecraft.entity.projectile.TridentEntity) {
         return new ItemStack(Items.TRIDENT);
      }
      return new ItemStack(Items.ARROW);
   }

   private static void addBloomQuad(VertexConsumer buffer, Matrix4f matrix, float x, float y, float w, float h, int r, int g, int b, int a) {
      float x2 = x + w;
      float y2 = y + h;
      buffer.vertex(matrix, x, y, 0).texture(0, 0).color(r, g, b, a);
      buffer.vertex(matrix, x, y2, 0).texture(0, 1).color(r, g, b, a);
      buffer.vertex(matrix, x2, y2, 0).texture(1, 1).color(r, g, b, a);
      buffer.vertex(matrix, x2, y, 0).texture(1, 0).color(r, g, b, a);
   }

   private Vec3d worldToScreen(Vec3d worldPos) {
      if (mc == null || mc.getWindow() == null) return null;

      Vector3f relative = new Vector3f(
            (float) (worldPos.x - lastCameraPos.x),
            (float) (worldPos.y - lastCameraPos.y),
            (float) (worldPos.z - lastCameraPos.z));

      Quaternionf invCameraRot = new Quaternionf(lastCameraRotation).conjugate();
      relative.rotate(invCameraRot);

      Vector4f clip = new Vector4f(relative.x, relative.y, relative.z, 1.0F);
      lastProjectionMatrix.transform(clip);

      float w = clip.w;
      if (w <= 0.00001F) return null;

      float ndcX = clip.x / w;
      float ndcY = clip.y / w;
      float ndcZ = clip.z / w;

      float scaledWidth = mc.getWindow().getScaledWidth();
      float scaledHeight = mc.getWindow().getScaledHeight();
      float screenX = (ndcX * 0.5F + 0.5F) * scaledWidth;
      float screenY = (1.0F - (ndcY * 0.5F + 0.5F)) * scaledHeight;

      if (Float.isNaN(screenX) || Float.isNaN(screenY) || Float.isInfinite(screenX) || Float.isInfinite(screenY)) {
         return null;
      }
      if (screenX < -400 || screenY < -400 || screenX > scaledWidth + 400 || screenY > scaledHeight + 400) {
         return null;
      }

      float guiScale = (float) Mathf.getScaleFactor();
      return new Vec3d(screenX * guiScale, screenY * guiScale, ndcZ);
   }

   private String formatOneDecimal(float value) {
      int scaled = Math.round(value * 10.0F);
      return (scaled / 10) + "." + Math.abs(scaled % 10);
   }

   private List<Vec3d> simulate(Entity entity, ProjectileType type, float tickDelta) {
      List<Vec3d> points = new ArrayList<>();
      Vec3d pos = new Vec3d(
            MathHelper.lerp(tickDelta, entity.lastX, entity.getX()),
            MathHelper.lerp(tickDelta, entity.lastY, entity.getY()),
            MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ()));
      Vec3d motion = entity.getVelocity();
      points.add(pos);

      if (motion.lengthSquared() < 1.0E-6) {
         return points;
      }

      double gravity = type.gravity;
      double airDrag = type.airDrag;
      double waterDrag = type.waterDrag;

      for (int i = 0; i < 300; i++) {
         Vec3d lastPos = pos;
         Vec3d nextPos = pos.add(motion);

         BlockHitResult hit = mc.world.raycast(new RaycastContext(
               lastPos,
               nextPos,
               RaycastContext.ShapeType.COLLIDER,
               RaycastContext.FluidHandling.NONE,
               mc.player));

         if (hit.getType() == HitResult.Type.BLOCK) {
            points.add(hit.getPos());
            break;
         }

         points.add(nextPos);
         pos = nextPos;

         boolean inWater = mc.world.getBlockState(BlockPos.ofFloored(pos)).isOf(Blocks.WATER);
         double drag = inWater ? waterDrag : airDrag;
         motion = motion.multiply(drag).subtract(0.0, gravity, 0.0);

         if (type == ProjectileType.ITEM && Math.abs(motion.y) < 1.0E-3 && motion.horizontalLengthSquared() < 1.0E-4) {
            break;
         }

         if (pos.y <= mc.world.getBottomY()) break;
      }

      return points;
   }

   private record ImpactPoint(Vec3d pos, float seconds, ItemStack icon, int entityId) {
   }

   private static final class Anim {
      private static final float SPEED = 0.004F;
      private float value;
      private long lastTime;
      private long lastSmoothTime;
      private Vec3d smoothPos;

      private Anim(long now) {
         this.value = 0.0F;
         this.lastTime = now;
         this.lastSmoothTime = now;
         this.smoothPos = null;
      }

      private void update(long now, boolean appearing) {
         float dt = Math.max(0.0F, Math.min(100.0F, now - this.lastTime));
         this.lastTime = now;
         float step = SPEED * dt;
         if (appearing) {
            this.value = Math.min(1.0F, this.value + step);
         } else {
            this.value = Math.max(0.0F, this.value - step);
         }
      }

      private Vec3d smooth(Vec3d target, long now) {
         float dt = Math.max(0.0F, Math.min(100.0F, now - this.lastSmoothTime));
         this.lastSmoothTime = now;

         if (this.smoothPos == null || this.smoothPos.squaredDistanceTo(target) > 64.0) {
            this.smoothPos = target;
            return this.smoothPos;
         }

         double factor = 1.0 - Math.exp(-dt / 55.0);
         this.smoothPos = this.smoothPos.add(target.subtract(this.smoothPos).multiply(factor));
         return this.smoothPos;
      }
   }

   private enum ProjectileType {

      PEARL(0.03, 0.99, 0.8),
      PROJECTILE(0.05, 0.99, 0.6),
      ITEM(0.04, 0.98, 0.99);

      private final double gravity;
      private final double airDrag;
      private final double waterDrag;

      ProjectileType(double gravity, double airDrag, double waterDrag) {
         this.gravity = gravity;
         this.airDrag = airDrag;
         this.waterDrag = waterDrag;
      }
   }
}
