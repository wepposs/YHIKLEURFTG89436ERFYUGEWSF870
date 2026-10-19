package vesence.module.impl.combat;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.module.impl.visuals.custompet.CustomPetEntity;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;

/**
 * Полный порт модуля AimBot из RelevantPremiumpp4.
 *
 * <p>Авто-наведение для лука, арбалета и трезубца. Логика 1:1 с оригиналом:
 * <ul>
 *   <li>Настройки (типы целей / дистанция / время наводки / тихие повороты /
 *       предсказание трезубца / показ прицела / размер прицела).</li>
 *   <li>Выбор ближайшей валидной цели в радиусе.</li>
 *   <li>Баллистика лука (прямой расчёт) и трезубца (учёт гравитации снаряда).</li>
 *   <li>Плавная наводка через aimProgress за N тиков.</li>
 *   <li>Тихие (freelook, через URotations + GCD) либо видимые повороты.</li>
 *   <li>3D-прицел на цели с цветом темы и прозрачностью по прогрессу наводки.</li>
 * </ul>
 *
 * <p>Адаптация под vesence / MC 1.21.11:
 * <ul>
 *   <li>{@code Module(super)} -> {@link IModule} + {@link Category#COMBAT}.</li>
 *   <li>{@code @EventLink} -> {@link EventInit}; {@code EventGameUpdate}(расчёт) +
 *       {@code EventUpdate}(применение) объединены в один тиковый хендлер
 *       {@link #onUpdate(EventUpdate)} (расчёт -> применение).</li>
 *   <li>{@code RotationStorage.update(...)} -> {@link URotations#update} (та же
 *       сигнатура; общий с AttackAura механизм ротаций, как в оригинале).</li>
 *   <li>{@code ListSetting.is} -> {@link MultiBooleanSetting#get};
 *       {@code FloatSetting} -> {@link SliderSetting};
 *       {@code BooleanSetting.isState} -> {@code BooleanSetting.get}.</li>
 *   <li>Рендер прицела переписан под pipeline API (RenderPipeline/RenderLayer),
 *       т.к. в 1.21.11 нет ShaderProgramKeys/Tessellator.begin.</li>
 * </ul>
 */
@IModule(
   name = "AimBot",
   description = "Авто-наведение для лука, арбалета и трезубца",
   category = Category.COMBAT,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class AimBot extends Module {
   public static AimBot INSTANCE;

   private final MultiBooleanSetting targetTypes = new MultiBooleanSetting(
      "Типы целей",
      new BooleanSetting("Игроки", true),
      new BooleanSetting("В броне", true),
      new BooleanSetting("Без брони", false),
      new BooleanSetting("Мобы", false),
      new BooleanSetting("Зомби", false)
   );
   private final SliderSetting range = new SliderSetting("Дистанция", 40.0, 10.0, 100.0, 1.0);
   private final SliderSetting aimTime = new SliderSetting("Время наводки (тики)", 10.0, 0.0, 40.0, 1.0);
   private final BooleanSetting silentRotations = new BooleanSetting("Тихие повороты", true);
   private final BooleanSetting tridentPredict = new BooleanSetting("Предсказание трезубца", true);
   private final BooleanSetting showCrosshair = new BooleanSetting("Показать прицел", true);
   private final SliderSetting crosshairSize = new SliderSetting("Размер прицела", 1.0, 0.3, 3.0, 0.1);

   private LivingEntity target = null;
   private boolean isAiming = false;
   private float aimProgress = 0.0F;
   private Rotation targetRotation = null;
   // true если в руке трезубец (нужно для расчёта гравитации)
   private boolean holdingTrident = false;

   // ── Рендер прицела (порт под pipeline API 1.21.11, паттерн ContainerESP) ──
   private static final Identifier CROSSHAIR_TEXTURE = Identifier.of("vesence", "textures/cross/hit.png");
   private static final RenderPipeline CROSSHAIR_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
         .withLocation(Identifier.of("vesence", "aimbot_crosshair"))
         .withVertexFormat(VertexFormats.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
         .withDepthWrite(false)
         .withBlend(BlendFunction.LIGHTNING)
         .build());
   private static final RenderLayer CROSSHAIR_LAYER = RenderLayer.of(
      "aimbot_crosshair_layer",
      RenderSetup.builder(CROSSHAIR_PIPELINE).expectedBufferSize(512).translucent().texture("Sampler0", CROSSHAIR_TEXTURE).build());
   private BufferAllocator allocator = null;
   private Immediate immediate = null;

   public AimBot() {
      INSTANCE = this;
      this.addSettings(new Setting[]{
         this.targetTypes, this.range, this.aimTime, this.silentRotations,
         this.tridentPredict, this.showCrosshair, this.crosshairSize
      });
   }

   private boolean isHoldingRangedWeapon() {
      ItemStack mainHand = mc.player.getMainHandStack();
      ItemStack offHand = mc.player.getOffHandStack();
      return mainHand.getItem() instanceof BowItem
         || mainHand.getItem() instanceof CrossbowItem
         || mainHand.getItem() instanceof TridentItem
         || offHand.getItem() instanceof BowItem
         || offHand.getItem() instanceof CrossbowItem
         || offHand.getItem() instanceof TridentItem;
   }

   private boolean isTridentInHand() {
      ItemStack mainHand = mc.player.getMainHandStack();
      ItemStack offHand = mc.player.getOffHandStack();
      return mainHand.getItem() instanceof TridentItem
         || offHand.getItem() instanceof TridentItem;
   }

   private boolean isValidTarget(LivingEntity entity) {
      if (entity instanceof CustomPetEntity) {
         return false;
      }
      if (entity == mc.player) {
         return false;
      } else if (!entity.isAlive() || entity.getHealth() <= 0.0F) {
         return false;
      } else if (!(entity instanceof PlayerEntity)) {
         if (entity instanceof ZombieEntity) {
            return this.targetTypes.get("Зомби");
         } else {
            return entity instanceof HostileEntity ? this.targetTypes.get("Мобы") : false;
         }
      } else if (!this.targetTypes.get("Игроки")) {
         return false;
      } else if (FriendStorage.isFriend(entity.getName().getString())) {
         return false;
      } else {
         boolean hasArmor = false;
         PlayerEntity player = (PlayerEntity)entity;

         for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!player.getEquippedStack(slot).isEmpty()) {
               hasArmor = true;
               break;
            }
         }

         if (this.targetTypes.get("В броне") && hasArmor) {
            return true;
         } else {
            return this.targetTypes.get("Без брони") && !hasArmor ? true : !this.targetTypes.get("В броне") && !this.targetTypes.get("Без брони");
         }
      }
   }

   private LivingEntity findBestTarget() {
      List<LivingEntity> targets = new ArrayList<>();
      Box searchBox = mc.player.getBoundingBox().expand(this.range.get().floatValue());

      for (LivingEntity entity : mc.world.getEntitiesByClass(LivingEntity.class, searchBox, e -> true)) {
         if (this.isValidTarget(entity)) {
            double dist = mc.player.distanceTo(entity);
            if (!(dist > this.range.get().floatValue())) {
               targets.add(entity);
            }
         }
      }

      if (targets.isEmpty()) {
         return null;
      } else {
         targets.sort(Comparator.comparingDouble(entityx -> mc.player.distanceTo(entityx)));
         return targets.get(0);
      }
   }

   private Rotation calculateBowRotation(LivingEntity target) {
      Vec3d eyes = mc.player.getEyePos();
      Vec3d targetPos = target.getBoundingBox().getCenter();
      double dx = targetPos.x - eyes.x;
      double dy = targetPos.y - eyes.y;
      double dz = targetPos.z - eyes.z;
      double distance = Math.sqrt(dx * dx + dz * dz);
      float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
      float pitch = (float)(-Math.toDegrees(Math.atan2(dy, distance)));
      return new Rotation(yaw, pitch);
   }

   /**
    * Расчёт угла для трезубца с учётом гравитации снаряда.
    *
    * Трезубец: начальная скорость = 2.5 блока/тик, гравитация = 0.05 блока/тик².
    * Используем формулу баллистики: pitch = atan((v² ± sqrt(v⁴ - g*(g*x² + 2*dy*v²))) / (g*x))
    * где x = горизонтальное расстояние, dy = вертикальная разница.
    */
   private Rotation calculateTridentRotation(LivingEntity target) {
      Vec3d eyes = mc.player.getEyePos();
      Vec3d targetPos = target.getBoundingBox().getCenter();
      double dx = targetPos.x - eyes.x;
      double dy = targetPos.y - eyes.y;
      double dz = targetPos.z - eyes.z;

      float yaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;

      double horizontalDist = Math.sqrt(dx * dx + dz * dz);
      double v = 2.5;   // начальная скорость трезубца (блоков/тик)
      double g = 0.05;  // гравитация (блоков/тик²)

      double v2 = v * v;
      double discriminant = v2 * v2 - g * (g * horizontalDist * horizontalDist + 2.0 * dy * v2);

      float pitch;
      if (discriminant < 0) {
         // Цель вне досягаемости — целимся максимально высоко
         pitch = -45.0F;
      } else {
         // Берём меньший угол (настильная траектория — более точная)
         double sqrtD = Math.sqrt(discriminant);
         double angle1 = Math.atan((v2 - sqrtD) / (g * horizontalDist));
         double angle2 = Math.atan((v2 + sqrtD) / (g * horizontalDist));
         // angle1 — настильная, angle2 — навесная; берём настильную
         double angle = Math.abs(angle1) <= Math.abs(angle2) ? angle1 : angle2;
         pitch = (float)(-Math.toDegrees(angle));
      }

      return new Rotation(yaw, pitch);
   }

   /**
    * Тиковый хендлер (порт onGameUpdate + onTick из оригинала). В vesence
    * оба относятся к одному тику, поэтому объединены: сначала расчёт цели и
    * плавной ротации, затем её применение (тихо через URotations либо явно).
    */
   @EventInit
   public void onUpdate(EventUpdate e) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      // ── Расчёт (порт onGameUpdate) ──
      this.holdingTrident = this.isTridentInHand();
      this.isAiming = mc.player.isUsingItem() && this.isHoldingRangedWeapon();

      if (!this.isAiming) {
         this.target = null;
         this.targetRotation = null;
         this.aimProgress = 0.0F;
         this.holdingTrident = false;
         return;
      }

      LivingEntity newTarget = this.findBestTarget();
      if (newTarget == null) {
         this.target = null;
         this.targetRotation = null;
         this.aimProgress = 0.0F;
         return;
      }

      if (this.target != newTarget) {
         this.target = newTarget;
         this.aimProgress = 0.0F;
      }

      // Для трезубца — баллистический расчёт с гравитацией, для лука/арбалета — прямой
      Rotation newRotation = (this.holdingTrident && this.tridentPredict.get())
         ? this.calculateTridentRotation(this.target)
         : this.calculateBowRotation(this.target);

      float maxStep = 1.0F / Math.max(1.0F, this.aimTime.get().floatValue());
      this.aimProgress = Math.min(this.aimProgress + maxStep, 1.0F);

      float currentYaw   = mc.player.getYaw();
      float currentPitch = mc.player.getPitch();
      float yawDiff   = MathHelper.wrapDegrees(newRotation.yaw   - currentYaw);
      float pitchDiff = newRotation.pitch - currentPitch;

      this.targetRotation = new Rotation(
         currentYaw   + yawDiff   * this.aimProgress,
         currentPitch + pitchDiff * this.aimProgress
      );

      // ── Применение ротации (порт onTick) ──
      if (this.targetRotation == null) {
         return;
      }

      if (this.silentRotations.get()) {
         float gcd = GCDUtil.getGCD();
         float yaw   = this.targetRotation.yaw;
         float pitch = this.targetRotation.pitch;
         yaw   -= (yaw   - mc.player.getYaw())   % gcd;
         pitch -= (pitch - mc.player.getPitch()) % gcd;
         URotations.update(new Rotation(yaw, pitch), 180.0F, 180.0F, 45.0F, 45.0F, 0, 2, false);
      } else {
         mc.player.setYaw(this.targetRotation.yaw);
         mc.player.setPitch(this.targetRotation.pitch);
      }
   }

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (!this.showCrosshair.get() || this.target == null || !this.isAiming) {
         return;
      }
      if (mc.player == null || mc.world == null) {
         return;
      }

      float partialTicks = event.getTickDelta();
      Vec3d targetPos = new Vec3d(
         MathHelper.lerp(partialTicks, this.target.lastRenderX, this.target.getX()),
         MathHelper.lerp(partialTicks, this.target.lastRenderY, this.target.getY()) + this.target.getHeight() / 2.0,
         MathHelper.lerp(partialTicks, this.target.lastRenderZ, this.target.getZ())
      );
      Vec3d cameraPos = event.getCameraPos();
      MatrixStack matrices = event.getMatrixStack();
      double renderX = targetPos.x - cameraPos.x;
      double renderY = targetPos.y - cameraPos.y;
      double renderZ = targetPos.z - cameraPos.z;

      if (this.allocator == null) {
         this.allocator = new BufferAllocator(262144);
         this.immediate = VertexConsumerProvider.immediate(this.allocator);
      }

      try {
         matrices.push();
         matrices.translate(renderX, renderY, renderZ);
         matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYaw()));
         matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getPitch()));
         float size = this.crosshairSize.get().floatValue() * 0.5F;
         int alpha = (int)(255.0F * this.aimProgress);
         int color = ColorUtil.theme(255);
         int r = color >> 16 & 0xFF;
         int g = color >> 8 & 0xFF;
         int b = color & 0xFF;
         Matrix4f matrix = matrices.peek().getPositionMatrix();
         VertexConsumer buffer = this.immediate.getBuffer(CROSSHAIR_LAYER);
         buffer.vertex(matrix, -size, -size, 0.0F).texture(0.0F, 1.0F).color(r, g, b, alpha);
         buffer.vertex(matrix, -size, size, 0.0F).texture(0.0F, 0.0F).color(r, g, b, alpha);
         buffer.vertex(matrix, size, size, 0.0F).texture(1.0F, 0.0F).color(r, g, b, alpha);
         buffer.vertex(matrix, size, -size, 0.0F).texture(1.0F, 1.0F).color(r, g, b, alpha);
         matrices.pop();
         this.immediate.draw();
      } catch (Exception ex) {
         if (this.allocator != null) {
            try {
               this.allocator.close();
            } catch (Exception ignored) {
            }
         }
         this.allocator = null;
         this.immediate = null;
      }
   }

   public LivingEntity getTarget() {
      return this.target;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      this.target = null;
      this.isAiming = false;
      this.aimProgress = 0.0F;
      this.targetRotation = null;
      this.holdingTrident = false;
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.target = null;
      this.isAiming = false;
      this.aimProgress = 0.0F;
      this.targetRotation = null;
      this.holdingTrident = false;
      if (this.allocator != null) {
         try {
            this.allocator.close();
         } catch (Exception ignored) {
         }
         this.allocator = null;
         this.immediate = null;
      }
   }
}
