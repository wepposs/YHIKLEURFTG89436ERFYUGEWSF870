package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.Optional;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import net.minecraft.client.render.MultiBufferSource.Immediate;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Holder;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.ClipContext.FluidHandling;
import net.minecraft.world.ClipContext.ShapeType;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.render.ColorUtil;

/**
 * Trajectories — предсказание траектории предмета в руке (лук, арбалет, трезубец,
 * жемчуг, снежок, яйцо, зелья). Полный порт логики из RelevantPremiumpp4; рендер
 * адаптирован под пайплайн-систему 1.21.11 (RenderType + Immediate), как в
 * SkeletonESP/Projectile — линии рисуются без depth-теста (видны всегда).
 */
@IModule(name = "Trajectories", description = "Показывает траекторию предмета в руке", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Trajectories extends Module {

   private static final int MAX_STEPS = 440;
   private static final double SIMULATION_STEP = 0.5;
   private static final double SPLASH_RADIUS = 4.0;
   private static final ResourceLocation GLOW_TEXTURE = ResourceLocation.of("vesence", "textures/trajectories/glow.png");

   // ─── Линии (траектория + маркер попадания + бокс сущности) ───────────────
   private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
               .withLocation(ResourceLocation.of("vesence", "pipeline/world/trajectory_lines"))
               .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.DEBUG_LINES)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.TRANSLUCENT)
               .build());

   private static final RenderType LINES_LAYER = RenderType.of(
         "vesence_trajectory_lines",
         RenderSetup.builder(LINES_PIPELINE).expectedBufferSize(16384).translucent().build());

   // ─── Свечение зоны зелья (glow.png) ──────────────────────────────────────
   private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
               .withLocation(ResourceLocation.of("vesence", "pipeline/world/trajectory_glow"))
               .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.TRANSLUCENT)
               .build());

   private static final RenderType GLOW_LAYER = RenderType.of(
         "vesence_trajectory_glow",
         RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(2048).translucent()
               .texture("Sampler0", GLOW_TEXTURE).build());

   // ─── Настройки ───────────────────────────────────────────────────────────
   private final SliderSetting lineWidth = new SliderSetting("Ширина линии", 2.2, 0.5, 5.0, 0.1, false);
   private final BooleanSetting bowToggle = new BooleanSetting("Лук", true);
   private final BooleanSetting crossbowToggle = new BooleanSetting("Арбалет", true);
   private final BooleanSetting tridentToggle = new BooleanSetting("Трезубец", true);
   private final BooleanSetting pearlToggle = new BooleanSetting("Перла", true);

   private ByteBufferBuilder allocator;
   private Immediate immediate;

   public Trajectories() {
      this.addSettings(new Setting[]{this.lineWidth, this.bowToggle, this.crossbowToggle, this.tridentToggle, this.pearlToggle});
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.resetBuffer();
   }

   private void resetBuffer() {
      if (this.allocator != null) {
         try {
            this.allocator.close();
         } catch (Exception ignored) {
         }
         this.allocator = null;
         this.immediate = null;
      }
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (mc.player == null || mc.level == null) {
         return;
      }
      ItemStack stack = this.getHeldProjectileStack();
      if (stack.isEmpty()) {
         return;
      }
      ProjectileParams params = this.getParams(stack);
      if (params == null) {
         return;
      }

      float tickDelta = event.getTickDelta();
      Vec3 startPos = mc.player.getCameraPosVec(tickDelta);
      Vec3[] directions = this.getShotDirections(stack, tickDelta);
      PredictionResult[] results = new PredictionResult[directions.length];
      int resultCount = 0;

      for (Vec3 direction : directions) {
         PredictionResult result = this.predict(mc.player, params, startPos, direction);
         if (result != null && result.points.length >= 2) {
            results[resultCount++] = result;
         }
      }

      if (resultCount == 0) {
         return;
      }

      PoseStack matrices = event.getMatrixStack();
      Vec3 cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      int themeColor = ColorUtil.fade();
      if ((themeColor >> 24 & 0xFF) == 0) {
         themeColor |= 0xFF000000;
      }

      if (this.allocator == null) {
         this.allocator = new ByteBufferBuilder(262144);
         this.immediate = MultiBufferSource.immediate(this.allocator);
      }

      matrices.pushPose();
      try {
         matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
         Matrix4f matrix = matrices.last().getPositionMatrix();

         // Проход 1 — все линии (траектория, маркер, бокс).
         VertexConsumer lineBuffer = this.immediate.getBuffer(LINES_LAYER);
         for (int i = 0; i < resultCount; i++) {
            PredictionResult result = results[i];
            this.drawTrajectoryLine(lineBuffer, matrix, result.points, ColorUtil.replAlpha(themeColor, 190));
            if (result.entityHit != null && result.entityHit.isAlive()) {
               this.drawEntityBox(lineBuffer, matrix, result.entityHit, ColorUtil.getColor(255, 70, 70, 210));
            } else if (result.blockHit != null) {
               this.drawImpactMarker(lineBuffer, matrix, result.hitPos, result.blockHit.getSide(), ColorUtil.replAlpha(themeColor, 230));
            }
         }

         // Проход 2 — свечение зоны splash-зелья (отдельный слой/текстура).
         if (stack.isOf(Items.SPLASH_POTION)) {
            VertexConsumer glowBuffer = this.immediate.getBuffer(GLOW_LAYER);
            for (int i = 0; i < resultCount; i++) {
               PredictionResult result = results[i];
               if (result.hitPos != null) {
                  this.drawPotionRadiusGlow(glowBuffer, matrix, result.hitPos, themeColor);
               }
            }
         }

         this.immediate.draw();
      } catch (Exception e) {
         this.resetBuffer();
      } finally {
         matrices.popPose();
      }
   }

   private ItemStack getHeldProjectileStack() {
      ItemStack main = mc.player.getMainHandItem();
      if (!main.isEmpty() && this.getParams(main) != null) {
         return main;
      } else {
         ItemStack off = mc.player.getOffhandItem();
         return !off.isEmpty() && this.getParams(off) != null ? off : ItemStack.EMPTY;
      }
   }

   private ProjectileParams getParams(ItemStack stack) {
      if (stack.isOf(Items.ENDER_PEARL)) {
         return this.pearlToggle.get() ? new ProjectileParams(1.5, 0.03, 0.99) : null;
      } else if (stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG)) {
         return new ProjectileParams(1.5, 0.03, 0.99);
      } else if (stack.isOf(Items.SPLASH_POTION) || stack.isOf(Items.LINGERING_POTION)) {
         return new ProjectileParams(0.5, 0.05, 0.99);
      } else if (stack.isOf(Items.BOW)) {
         if (!this.bowToggle.get()) {
            return null;
         }

         float power = 1.0F;
         if (mc.player.isUsingItem() && mc.player.getActiveItem() == stack) {
            int useTicks = stack.getMaxUseTime(mc.player) - mc.player.getItemUseTimeLeft();
            float f = useTicks / 20.0F;
            f = (f * f + f * 2.0F) / 3.0F;
            power = Math.min(f, 1.0F);
         }

         double velocity = 3.0 * power;
         return velocity <= 0.01 ? null : new ProjectileParams(velocity, 0.05, 0.99);
      } else if (stack.isOf(Items.CROSSBOW)) {
         if (!this.crossbowToggle.get()) {
            return null;
         }

         return !CrossbowItem.isCharged(stack) ? null : new ProjectileParams(3.15, 0.05, 0.99);
      } else if (stack.isOf(Items.TRIDENT)) {
         return this.tridentToggle.get() ? new ProjectileParams(2.5, 0.05, 0.99) : null;
      } else {
         return null;
      }
   }

   private Vec3[] getShotDirections(ItemStack stack, float tickDelta) {
      Vec3 baseDir = mc.player.getRotationVec(tickDelta).normalize();
      if (stack.isOf(Items.CROSSBOW) && getEnchantmentLevel(stack, Enchantments.MULTISHOT) > 0) {
         float baseYaw = (float)(Mth.atan2(baseDir.z, baseDir.x) * (180.0 / Math.PI)) - 90.0F;
         float basePitch = (float)(-(Mth.atan2(baseDir.y, Mth.sqrt((float)(baseDir.x * baseDir.x + baseDir.z * baseDir.z))) * (180.0 / Math.PI)));
         return new Vec3[]{this.getDirectionFromYawPitch(baseYaw - 10.0F, basePitch), baseDir, this.getDirectionFromYawPitch(baseYaw + 10.0F, basePitch)};
      } else {
         return new Vec3[]{baseDir};
      }
   }

   private Vec3 getDirectionFromYawPitch(float yawDeg, float pitchDeg) {
      float yaw = yawDeg * (float) (Math.PI / 180.0);
      float pitch = pitchDeg * (float) (Math.PI / 180.0);
      float x = Mth.sin(-yaw - (float) Math.PI) * -Mth.cos(-pitch);
      float y = Mth.sin(-pitch);
      float z = Mth.cos(-yaw - (float) Math.PI) * -Mth.cos(-pitch);
      return new Vec3(x, y, z).normalize();
   }

   private PredictionResult predict(Player player, ProjectileParams params, Vec3 startPos, Vec3 direction) {
      Vec3 pos = startPos;
      Vec3 motion = direction.normalize().multiply(params.velocity);
      Vec3[] points = new Vec3[MAX_STEPS + 1];
      int count = 0;
      points[count++] = startPos;
      Entity entityHit = null;
      Vec3 entityHitPos = null;

      for (int i = 0; i < MAX_STEPS; i++) {
         Vec3 next = pos.add(motion.multiply(SIMULATION_STEP));
         if (entityHit == null) {
            EntityHit hit = this.rayTraceEntities(pos, next, player);
            if (hit != null) {
               entityHit = hit.entity;
               entityHitPos = hit.hitPos;
            }
         }

         BlockHitResult blockHit = mc.level.raycast(new ClipContext(pos, next, ShapeType.COLLIDER, FluidHandling.NONE, player));
         if (blockHit.getType() == HitResult.Type.BLOCK) {
            points[count++] = blockHit.getPos();
            return new PredictionResult(this.copyPoints(points, count), blockHit, blockHit.getPos(), entityHit, entityHitPos);
         }

         points[count++] = next;
         pos = next;
         boolean inWater = mc.level.getBlockState(BlockPos.ofFloored(next)).isOf(Blocks.WATER);
         double drag = Math.pow(inWater ? 0.8 : params.drag, 0.5);
         motion = motion.multiply(drag).subtract(0.0, params.gravity * 0.5, 0.0);
         if (next.y <= mc.level.getBottomY()) {
            break;
         }
      }

      Vec3 hitPos = entityHitPos != null ? entityHitPos : points[count - 1];
      return new PredictionResult(this.copyPoints(points, count), null, hitPos, entityHit, entityHitPos);
   }

   private Vec3[] copyPoints(Vec3[] points, int count) {
      Vec3[] out = new Vec3[count];
      System.arraycopy(points, 0, out, 0, count);
      return out;
   }

   private EntityHit rayTraceEntities(Vec3 from, Vec3 to, Entity owner) {
      AABB search = new AABB(from, to).expand(1.0);
      Entity closest = null;
      Vec3 closestHit = null;
      double closestDistance = Double.MAX_VALUE;

      for (Entity entity : mc.level.getOtherEntities(owner, search, entityx -> entityx != null && entityx.isAlive() && entityx.canHit())) {
         Optional<Vec3> hit = entity.getBoundingBox().expand(0.3).raycast(from, to);
         if (!hit.isEmpty()) {
            double distance = from.squaredDistanceTo(hit.get());
            if (distance < closestDistance) {
               closestDistance = distance;
               closest = entity;
               closestHit = hit.get();
            }
         }
      }

      return closest == null ? null : new EntityHit(closest, closestHit);
   }

   private void drawTrajectoryLine(VertexConsumer buffer, Matrix4f matrix, Vec3[] points, int color) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;

      for (int i = 0; i < points.length - 1; i++) {
         Vec3 start = points[i];
         Vec3 end = points[i + 1];
         buffer.vertex(matrix, (float)start.x, (float)start.y, (float)start.z).color(r, g, b, a);
         buffer.vertex(matrix, (float)end.x, (float)end.y, (float)end.z).color(r, g, b, a);
      }
   }

   private void drawImpactMarker(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, Direction side, int color) {
      Vec3 normal = Vec3.of(side.getVector()).normalize();
      Vec3 u = side != Direction.UP && side != Direction.DOWN ? normal.crossProduct(new Vec3(0.0, 1.0, 0.0)).normalize() : new Vec3(1.0, 0.0, 0.0);
      Vec3 v = normal.crossProduct(u).normalize();
      Vec3 center = pos.add(normal.multiply(0.004));
      double radius = 0.35;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      int segments = 48;
      Vec3 previous = null;

      for (int i = 0; i <= segments; i++) {
         double angle = (Math.PI * 2) * i / segments;
         Vec3 point = center.add(u.multiply(Math.cos(angle) * radius)).add(v.multiply(Math.sin(angle) * radius));
         if (previous != null) {
            buffer.vertex(matrix, (float)previous.x, (float)previous.y, (float)previous.z).color(r, g, b, a);
            buffer.vertex(matrix, (float)point.x, (float)point.y, (float)point.z).color(r, g, b, a);
         }

         previous = point;
      }

      Vec3 left = center.add(u.multiply(-radius));
      Vec3 right = center.add(u.multiply(radius));
      Vec3 down = center.add(v.multiply(-radius));
      Vec3 up = center.add(v.multiply(radius));
      buffer.vertex(matrix, (float)left.x, (float)left.y, (float)left.z).color(r, g, b, a);
      buffer.vertex(matrix, (float)right.x, (float)right.y, (float)right.z).color(r, g, b, a);
      buffer.vertex(matrix, (float)down.x, (float)down.y, (float)down.z).color(r, g, b, a);
      buffer.vertex(matrix, (float)up.x, (float)up.y, (float)up.z).color(r, g, b, a);
   }

   private void drawEntityBox(VertexConsumer buffer, Matrix4f matrix, Entity entity, int color) {
      AABB box = entity.getBoundingBox();
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      this.vertexBox(buffer, matrix, box, r, g, b, a);
   }

   private void vertexBox(VertexConsumer buffer, Matrix4f matrix, AABB box, int r, int g, int b, int a) {
      float minX = (float)box.minX;
      float minY = (float)box.minY;
      float minZ = (float)box.minZ;
      float maxX = (float)box.maxX;
      float maxY = (float)box.maxY;
      float maxZ = (float)box.maxZ;
      this.line(buffer, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
      this.line(buffer, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
      this.line(buffer, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
      this.line(buffer, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);
      this.line(buffer, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
      this.line(buffer, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
      this.line(buffer, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
      this.line(buffer, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);
      this.line(buffer, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
      this.line(buffer, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
      this.line(buffer, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
      this.line(buffer, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);
   }

   private void line(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, int r, int g, int b, int a) {
      buffer.vertex(matrix, x1, y1, z1).color(r, g, b, a);
      buffer.vertex(matrix, x2, y2, z2).color(r, g, b, a);
   }

   private void drawPotionRadiusGlow(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, int themeColor) {
      int color = ColorUtil.replAlpha(themeColor, 82);
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      float radius = (float) SPLASH_RADIUS;
      float cx = (float) pos.x;
      float cy = (float) (pos.y + 0.012);
      float cz = (float) pos.z;
      buffer.vertex(matrix, cx - radius, cy, cz - radius).texture(0.0F, 0.0F).color(r, g, b, a);
      buffer.vertex(matrix, cx - radius, cy, cz + radius).texture(0.0F, 1.0F).color(r, g, b, a);
      buffer.vertex(matrix, cx + radius, cy, cz + radius).texture(1.0F, 1.0F).color(r, g, b, a);
      buffer.vertex(matrix, cx + radius, cy, cz - radius).texture(1.0F, 0.0F).color(r, g, b, a);
   }

   private static int getEnchantmentLevel(ItemStack stack, ResourceKey<Enchantment> enchantmentKey) {
      ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);

      for (Holder<Enchantment> enchantment : enchantments.getEnchantments()) {
         if (enchantment.matchesKey(enchantmentKey)) {
            return enchantments.getLevel(enchantment);
         }
      }

      return 0;
   }

   private record EntityHit(Entity entity, Vec3 hitPos) {
   }

   private record PredictionResult(Vec3[] points, BlockHitResult blockHit, Vec3 hitPos, Entity entityHit, Vec3 entityHitPos) {
   }

   private record ProjectileParams(double velocity, double gravity, double drag) {
   }
}
