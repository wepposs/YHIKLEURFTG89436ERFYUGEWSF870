package vesence.module.impl.combat.auraComponent;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MaceItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import net.minecraft.world.World;
import vesence.module.impl.combat.AttackAura;
import vesence.utils.other.IMinecraft;
import vesence.utils.other.StopWatch;
import vesence.utils.player.InventoryActionUtil;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

@Environment(EnvType.CLIENT)
public final class Attack implements IMinecraft {
   private static final SecureRandom secureRandom = new SecureRandom();
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   public static long hitCounterCPSBypass;
   private static final StopWatch cooldownTimer = new StopWatch();

   /**
    * Тики с последнего удара (Deluxe: Class83#field3654). Инкрементируется
    * раз в клиентский тик из ауры, обнуляется в {@link #useEntity}. Кулдаун-гейт
    * Deluxe работает по нему, а не по миллисекундному таймеру.
    */
   private static int ticksSinceHit;

   public static void tickTimers() {
      ticksSinceHit++;
   }

   public static void hitCounterCPSBypassNext() {
      hitCounterCPSBypass++;
   }

   public static void hitCounterCPSBypassReset() {
      hitCounterCPSBypass = 0L;
   }

   public static boolean cpsBypassTrigger() {
      return hitCounterCPSBypass % 7L == 3L;
   }

   public static World getWorld() {
      return mc.world;
   }

   public static int getAxeSlot() {
      if (mc.player == null) {
         return -1;
      } else {
         for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() instanceof AxeItem) {
               return i;
            }
         }

         return -1;
      }
   }

   public static Runnable[] hitShieldBreakTaskForUse(LivingEntity livingIn, boolean enabled) {
      Runnable[] pre$post = new Runnable[]{() -> {}, () -> {}};
      if (enabled && mc.player != null) {
         if (livingIn instanceof PlayerEntity player) {
            if (!player.isBlocking()) {
               return pre$post;
            }

            ItemStack main = player.getMainHandStack();
            ItemStack off = player.getOffHandStack();
            Item mainItem = main.isEmpty() ? null : main.getItem();
            Item offItem = off.isEmpty() ? null : off.getItem();
            if (mainItem == Items.SHIELD || offItem == Items.SHIELD) {
               int handSlot = mc.player.getInventory().getSelectedSlot();
               int slot;
               if ((slot = getAxeSlot()) != -1 && slot != handSlot) {
                  pre$post[0] = () -> {
                     if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
                     }
                  };
                  pre$post[1] = () -> {
                     if (mc.getNetworkHandler() != null) {
                        mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(handSlot));
                     }
                  };
               }
            }
         }

         return pre$post;
      } else {
         return pre$post;
      }
   }

   public static Runnable[] resetShieldSilentTaskForUse(boolean enabled) {
      Runnable[] pre$post = new Runnable[]{() -> {}, () -> {}};
      if (enabled && mc.player != null) {
         if (mc.player.isBlocking()) {
            Hand active = mc.player.getActiveHand();
            if (active == null) {
               return pre$post;
            }

            pre$post[0] = () -> mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
            pre$post[1] = () -> mc.getNetworkHandler().sendPacket(new PlayerInteractItemC2SPacket(active, 0, mc.player.getYaw(), mc.player.getPitch()));
         }

         return pre$post;
      } else {
         return pre$post;
      }
   }

   public static Runnable[] skipSilentSprintingTaskForUse(boolean enabled) {
      Runnable[] pre$post = new Runnable[]{() -> {}, () -> {}};
      if (enabled && mc.player != null) {
         if (mc.player.isSprinting() && !mc.player.isOnGround() && !mc.player.isSubmergedIn(FluidTags.WATER)) {
            pre$post[0] = () -> {
               mc.player.setSprinting(false);
               mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.STOP_SPRINTING));
            };
            pre$post[1] = () -> {
               mc.player.setSprinting(true);
               mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_SPRINTING));
            };
         }

         return pre$post;
      } else {
         return pre$post;
      }
   }

   public static double getYCapacityOnPlayerPos(int rangeY) {
      if (mc.world != null && mc.player != null) {
         Vec3d eyePos = mc.player.getEyePos();
         double minDst = rangeY * 2.0;
         double maxY = 320.0;
         double minY = -64.0;
         float selfWD2 = mc.player.getWidth() / 2.0F - 0.01F;

         for (Vec3d vec : Arrays.asList(
                 eyePos.add(-selfWD2, 0.0, -selfWD2),
                 eyePos.add(selfWD2, 0.0, selfWD2),
                 eyePos.add(selfWD2, 0.0, -selfWD2),
                 eyePos.add(-selfWD2, 0.0, selfWD2)
         )) {
            HitResult first = mc.world
                    .raycast(new RaycastContext(vec, vec.add(0.0, -rangeY, 0.0), ShapeType.VISUAL, FluidHandling.ANY, mc.player));
            HitResult second = mc.world
                    .raycast(new RaycastContext(vec, vec.add(0.0, rangeY, 0.0), ShapeType.VISUAL, FluidHandling.ANY, mc.player));
            if (maxY > second.getPos().y) {
               maxY = second.getPos().y;
            }

            if (minY < first.getPos().y) {
               minY = first.getPos().y;
            }

            double dst = maxY - minY;
            if (minDst > dst) {
               minDst = dst;
            }
         }

         return minDst - mc.player.getHeight();
      } else {
         return 1.0;
      }
   }

   public static double convenientFallOffset() {
      if (mc.player == null) {
         return 0.0;
      } else {
         double fallOffset = mc.player.fallDistance;
         if (mc.world != null && !mc.player.isOnGround() && mc.player.getVelocity().y < -0.0784000015258789) {
            boolean posLiquid = !mc.world.getFluidState(mc.player.getBlockPos()).isEmpty();
            boolean posUpLiquid = !mc.world.getFluidState(mc.player.getBlockPos().up()).isEmpty();
            if (!posLiquid && !posUpLiquid && mc.player.fallDistance < -mc.player.getVelocity().y && mc.player.age > 6) {
               fallOffset = -mc.player.getVelocity().y;
            }
         }

         return fallOffset;
      }
   }

   private static boolean isFrozen() {
      if (mc.player == null) return false;
      StatusEffectInstance slowness = mc.player.getStatusEffect(StatusEffects.SLOWNESS);
      if (slowness != null && slowness.getAmplifier() >= 4) {
         return true;
      }
      return mc.player.isFrozen();
   }

   /**
    * Гейт крита (Deluxe: Class83#m1203).
    *
    * <p>Порядок как в оригинале:
    * <ul>
    *   <li>булавa готова и игрок в падении — смэш бьёт по
    *   {@code MaceItem.shouldDealAdditionalDamage}, но глушится тайминг-гейтом
    *   {@code AttackAura.smashTimingBlocked} (Deluxe: Class55#m944);</li>
    *   <li>{@code fallCheck} выключен — бьём всегда;</li>
    *   <li>физически можем критовать (нет левитации/слепоты/воды/лавы в глазах,
    *   не летим/не на элитре/не лезем/не в транспорте) — бьём только в падении
    *   с {@code fallDistance > convenientFallOffset()} (при AirStuck порог 0);</li>
    *   <li>включены умные криты — на земле без зажатого прыжка бьём (крит
    *   организует сама аура);</li>
    *   <li>физически критовать НЕ можем — бьём обычные удары.</li>
    * </ul>
    */
   public static boolean isBestMomentToHit(boolean fallCheck) {
      if (mc.player == null || mc.world == null) {
         return true;
      }

      if (AttackAura.maceReady()
            && !mc.player.isOnGround()
            && InventoryActionUtil.findItem(Items.MACE, false) != -1
            && !mc.player.getItemCooldownManager().isCoolingDown(
                  mc.player.getInventory().getStack(InventoryActionUtil.findItem(Items.MACE, false)))) {
         if (AttackAura.smashTimingBlocked(AttackAura.target)) {
            return false;
         }
         if (MaceItem.shouldDealAdditionalDamage(mc.player) && mc.player.getVelocity().y < 0.0) {
            return true;
         }
      }

      if (!fallCheck) {
         return true;
      }

      boolean smartCrits = AttackAura.extraSettings.get("Умные криты");
      FluidState eyeFluid = mc.world.getFluidState(BlockPos.ofFloored(mc.player.getEyePos()));
      boolean physical = !mc.player.hasStatusEffect(StatusEffects.LEVITATION)
            && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
            && !eyeFluid.isIn(FluidTags.WATER)
            && !eyeFluid.isIn(FluidTags.LAVA)
            && !mc.player.getAbilities().flying
            && !mc.player.isGliding()
            && !mc.player.isClimbing()
            && !mc.player.hasVehicle();
      double threshold = airStuckActive() ? 0.0 : convenientFallOffset();
      return physical && mc.player.fallDistance > threshold && !mc.player.isOnGround()
            || smartCrits && mc.player.isOnGround() && !mc.player.input.playerInput.jump()
            || !physical;
   }

   /** AirStuck активен (Deluxe: Class357#m3897 — при зависании порог крита 0). */
   private static boolean airStuckActive() {
      if (vesence.Vesence.get == null || vesence.Vesence.get.manager == null) {
         return false;
      }
      vesence.module.impl.movement.AirStuck airStuck =
            vesence.Vesence.get.manager.get(vesence.module.impl.movement.AirStuck.class);
      return airStuck != null && airStuck.enable;
   }

   public static boolean useEntity(LivingEntity livingIn, Runnable preHit, Runnable postHit, Hand hand, boolean cpsBypass) {
      if (preHit != null) {
         preHit.run();
      }

      if (livingIn != null && mc.interactionManager != null && mc.player != null) {
         mc.interactionManager.attackEntity(mc.player, livingIn);
         if (hand != null) {
            mc.player.swingHand(hand);
         }

         if (cpsBypass) {
            hitCounterCPSBypassNext();
         } else {
            hitCounterCPSBypassReset();
         }

         ticksSinceHit = 0;
         cooldownTimer.reset();
      }

      if (postHit != null) {
         postHit.run();
      }

      return livingIn != null;
   }

   public static long getMsCooldown() {
      if (mc.player == null) {
         return 500L;
      } else {
         double attackSpeed = mc.player.getAttributeValue(EntityAttributes.ATTACK_SPEED);
         float maxDeviation = 0.2F;
         long msCooldown2 = (long)(1.0 / attackSpeed * 1000.0 * (1.0F - Math.min(maxDeviation, 1.0F)));
         long msCooldown = mc.player.getMainHandStack().isIn(ItemTags.SWORDS) ? msCooldown2 - ThreadLocalRandom.current().nextLong(20L, 40L) : msCooldown2 + ThreadLocalRandom.current().nextLong(5L, 20L);

         if (mc.player.getMainHandStack().isIn(ItemTags.SWORDS)) {
            if (mc.player.isJumping() && mc.player.age > 6 && AttackAura.extraSettings.get("Умные криты")) {
               msCooldown = msCooldown2 - ThreadLocalRandom.current().nextLong(50L, 70L);
            }

            if (mc.player.isSubmergedInWater()) {
               msCooldown = msCooldown2 - ThreadLocalRandom.current().nextLong(50L, 70L);
            }
         }

         return msCooldown;
      }
   }

   public static boolean msCooldownReached(long msOffset) {
      return cooldownTimer.finished(getMsCooldown() + msOffset);
   }

   public static float msCooldownPC01() {
      return Math.min((float)cooldownTimer.elapsedTime() / (float)getMsCooldown(), 1.0F);
   }

   public static float msCooldownReach() {
      return (float)cooldownTimer.elapsedTime();
   }

   public static boolean anyEntityOnRay(LivingEntity livingIn, double range) {
      return livingIn != null
              && RayTraceUtil.rayTraceEntity(MathHelper.wrapDegrees(mc.player.getYaw()), mc.player.getPitch(), (float)range, livingIn);
   }

   /**
    * Кулдаун-гейт удара (Deluxe: Class83#m1205).
    *
    * <ul>
    *   <li>смэш булавой в полёте — только предметный кулдаун;</li>
    *   <li>падение &gt; 1.5 — предметный кулдаун + минимум 3 тика с удара;</li>
    *   <li>булава в руке — предметный кулдаун + прогресс атаки &ge; 0.9;</li>
    *   <li>обычное оружие — прогресс атаки &ge; 0.9 + минимум 10 тиков с удара.</li>
    * </ul>
    *
    * Отрицательный {@code msOffset} («ранний удар») сдвигает гейт раньше:
    * прогресс считается с заглядыванием вперёд, тик-гейт ослабляется.
    */
   private static boolean cooldownReady(long msOffset) {
      if (mc.player == null) {
         return false;
      }
      if (AttackAura.maceSmashActive()) {
         return !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack());
      }
      if (mc.player.fallDistance > 1.5F) {
         return !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack())
               && ticksSinceHit > 3L + msOffset / 50L;
      }
      float progressOffset = 0.5F + (float) Math.max(0L, -msOffset) / 50.0F;
      if (mc.player.getMainHandStack().isOf(Items.MACE)) {
         return !mc.player.getItemCooldownManager().isCoolingDown(mc.player.getMainHandStack())
               && mc.player.getAttackCooldownProgress(progressOffset) >= 0.9F;
      }
      return mc.player.getAttackCooldownProgress(progressOffset) >= 0.9F
            && ticksSinceHit >= 10L + msOffset / 50L;
   }

   public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean distanceCheck, boolean fallCheck, long cooldownMSOffset, float[] ranges) {
      float range = ranges.length > 2 ? ranges[2] : ranges[0];
      if (distanceCheck && livingTarget != null && !AuraUtil.validDistance(livingTarget, range, true)) {
         return false;
      } else if (!cooldownReady(cooldownMSOffset)) {
         return false;
      } else {
         boolean validNext = isBestMomentToHit(fallCheck);
         if (validNext && rayCast && !anyEntityOnRay(livingTarget, ranges[0])) {
            validNext = false;
         }

         return validNext;
      }
   }

   public static boolean shouldAttack(LivingEntity livingTarget, boolean rayCast, boolean fallCheck, long cooldownMSOffset, float[] ranges) {
      return shouldAttack(livingTarget, rayCast, true, fallCheck, cooldownMSOffset, ranges);
   }

   /**
    * Пора гасить спринт под удар (Deluxe: Class83#m1206). Режим берётся из
    * настройки ауры «Тип спринта»: «Быстрый» — гейт -50мс и скорость падения
    * &le; 0.003, «Легитный» — -150мс и &le; 0.165.
    */
   public static boolean resetSprintTick(LivingEntity targetIn, float[] ranges) {
      if (targetIn == null || mc.player == null) {
         return false;
      }
      if (AttackAura.sprintType.is("Быстрый")) {
         return shouldAttack(targetIn, false, false, -50L, ranges)
               && !mc.player.isOnGround()
               && !mc.player.isSubmergedIn(FluidTags.WATER)
               && mc.player.getVelocity().y <= 0.0030162615090425808;
      }
      return shouldAttack(targetIn, false, false, -150L, ranges)
            && !mc.player.isOnGround()
            && !mc.player.isSubmergedIn(FluidTags.WATER)
            && mc.player.getVelocity().y <= 0.16477328182606651;
   }

   // ── Пробитие блоков (Deluxe: Class359) ────────────────────────────────────

   /** Class359#m3921: чистая линия видимости (collider raycast — MISS). */
   public static boolean clearLineOfSight(Vec3d from, Vec3d to) {
      if (mc.world == null || mc.player == null) {
         return true;
      }
      return mc.world.raycast(new RaycastContext(from, to, ShapeType.COLLIDER,
            FluidHandling.NONE, mc.player)).getType() == HitResult.Type.MISS;
   }

   /** Class359#m3899: неполный блок или блок, в котором стоит игрок. */
   private static boolean isNonFullOrOccupied(net.minecraft.block.BlockState state, BlockPos pos) {
      if (mc.player == null) {
         return false;
      }
      BlockPos eyeBlock = BlockPos.ofFloored(mc.player.getEyePos());
      if (eyeBlock.equals(pos)
            || mc.player.isInPose(net.minecraft.entity.EntityPose.SWIMMING) && eyeBlock.up().equals(pos)) {
         return true;
      }
      net.minecraft.block.Block block = state.getBlock();
      return block instanceof net.minecraft.block.FenceBlock || block instanceof net.minecraft.block.FenceGateBlock
            || block instanceof net.minecraft.block.WallBlock || block instanceof net.minecraft.block.PaneBlock
            || block instanceof net.minecraft.block.SlabBlock || block instanceof net.minecraft.block.StairsBlock
            || block instanceof net.minecraft.block.TrapdoorBlock || block instanceof net.minecraft.block.PointedDripstoneBlock;
   }

   /** Class359#m3903: луч проходит, если не считать неполные блоки. */
   public static boolean clearIgnoringNonFull(Vec3d from, Vec3d to) {
      if (mc.world == null || mc.player == null) {
         return true;
      }
      return net.minecraft.world.BlockView.raycast(from, to, null, (ctx, pos) -> {
         net.minecraft.block.BlockState state = mc.world.getBlockState(pos);
         if (isNonFullOrOccupied(state, pos)) {
            return null;
         }
         net.minecraft.util.shape.VoxelShape shape = state.getCollisionShape(mc.world, pos);
         return shape.isEmpty() || shape.raycast(from, to, pos) == null ? null : Boolean.FALSE;
      }, ctx -> Boolean.TRUE);
   }

   /** Class359#m3920: вектор взгляда по yaw/pitch. */
   public static Vec3d directionVec(float yaw, float pitch) {
      float yawRad = -yaw * ((float) Math.PI / 180.0F) - (float) Math.PI;
      float pitchRad = -pitch * ((float) Math.PI / 180.0F);
      float cosPitch = MathHelper.cos(pitchRad);
      return new Vec3d(MathHelper.sin(yawRad) * -cosPitch, MathHelper.sin(pitchRad),
            MathHelper.cos(yawRad) * -cosPitch);
   }

   /**
    * Class359#m3918: цель досягаема по текущему взгляду сквозь неполные блоки
    * (заборы, плиты и т.п. — режим «Неполноценные»).
    */
   public static boolean hitThroughNonFull(LivingEntity target, double range) {
      if (mc.player == null || mc.world == null || target == null) {
         return true;
      }
      Vec3d eye = mc.player.getEyePos();
      Box box = target.getBoundingBox().expand(target.getTargetingMargin());
      if (box.contains(eye)) {
         return true;
      }
      Vec3d hit = box.raycast(eye, eye.add(directionVec(mc.player.getYaw(), mc.player.getPitch())
            .multiply(range))).orElse(null);
      if (hit == null) {
         return false;
      }
      // Чуть-чуть не доходя до поверхности бокса (Deluxe: Class359#m3904).
      Vec3d beforeSurface = hit.add(eye.subtract(hit).normalize().multiply(0.05));
      return clearIgnoringNonFull(eye, beforeSurface);
   }

   @Generated
   private Attack() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }

   @Generated
   public static StopWatch getCooldownTimer() {
      return cooldownTimer;
   }
}