package vesence.module.impl.visuals.custompet;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Контроллер слежения локального питомца за игроком. Перенесён из RelevantPremiumpp4
 * (Yarn 1.21.4 -> Yarn 1.21.11). Логика версионно-независимая.
 *
 *  - {@link #tick(PlayerEntity, CustomPetVariant)} — каждый клиентский тик;
 *  - {@link #reset()} — удалить питомца и сбросить состояние.
 */
public final class CustomPetFollowerController {
    private static final double MAX_WANDER_DISTANCE = 6.0;
    private static final double SNAP_BACK_DISTANCE = 9.0;
    private static final double FOLLOW_BREAK_DISTANCE = 5.0;
    private static final double TARGET_REACHED_DISTANCE = 0.9;

    private final MinecraftClient mc = MinecraftClient.getInstance();
    private final net.minecraft.util.math.random.Random random = net.minecraft.util.math.random.Random.create();

    private CustomPetEntity pet;
    private Vec3d roamTarget;
    private Vec3d airHoverOffset;
    private Vec3d airSmoothedTarget;
    private Vec3d airFlightDirection;
    private boolean airChasing;
    private int idleTicks;
    private int followRefreshTicks;
    private int followSide = 1;

    public CustomPetEntity getPet() {
        return this.pet;
    }

    public void tick(PlayerEntity player, CustomPetVariant variant, float scale) {
        if (player == null || player.getEntityWorld() == null || this.mc.world == null) {
            this.reset();
            return;
        }
        this.ensurePet(player, variant);
        if (this.pet != null) {
            this.pet.setPetScale(scale);
        }
        this.tickPet(player, variant);
    }

    public void reset() {
        if (this.pet != null && !this.pet.isRemoved()) {
            if (this.mc.world != null) {
                this.mc.world.removeEntity(this.pet.getId(), Entity.RemovalReason.DISCARDED);
            }
            this.pet.discard();
        }
        this.pet = null;
        this.roamTarget = null;
        this.airHoverOffset = null;
        this.airSmoothedTarget = null;
        this.airFlightDirection = null;
        this.airChasing = false;
        this.idleTicks = 0;
        this.followRefreshTicks = 0;
        this.followSide = 1;
    }

    private void ensurePet(PlayerEntity player, CustomPetVariant variant) {
        if (this.pet != null && this.pet.getEntityWorld() == this.mc.world && !this.pet.isRemoved()) {
            this.pet.setPetVariant(variant);
            return;
        }
        this.reset();
        this.pet = new CustomPetEntity(this.mc.world);
        this.pet.setPetVariant(variant);
        this.roamTarget = this.pickRoamTarget(player, true);
        this.airHoverOffset = null;
        this.airSmoothedTarget = null;
        this.airFlightDirection = null;
        this.airChasing = false;
        this.idleTicks = this.randomBetween(18, 34);
        this.followRefreshTicks = 0;
        this.followSide = this.random.nextBoolean() ? 1 : -1;
        this.pet.snapTo(this.roamTarget, this.random.nextFloat() * 360.0f);
        this.mc.world.addEntity(this.pet);
    }

    private void tickPet(PlayerEntity player, CustomPetVariant variant) {
        if (this.pet == null) {
            return;
        }
        this.pet.setPetVariant(variant);
        Vec3d current = this.pet.getEntityPos();
        Vec3d groundedCurrent = this.snapCurrentToGround(current, player.getY());
        Vec3d playerPos = player.getEntityPos();
        double playerDistanceSqr = current.squaredDistanceTo(playerPos);
        double playerDistance = Math.sqrt(playerDistanceSqr);
        double playerHorizontalSpeed = player.getVelocity().horizontalLength();
        boolean airbornePlayer = player.isGliding() || player.getAbilities().flying;
        boolean playerMovingFast = playerHorizontalSpeed > 0.17 || Math.abs(player.getVelocity().y) > 0.08;
        boolean shouldFollowPlayer = playerDistanceSqr > 25.0 || playerMovingFast && playerDistanceSqr > 9.610000000000001;
        boolean rainy = this.isRainingAbove(current);
        if (playerDistanceSqr > 81.0) {
            this.roamTarget = this.pickRoamTarget(player, true);
            this.airFlightDirection = null;
            this.airChasing = false;
            this.idleTicks = this.randomBetween(12, 22);
            this.followRefreshTicks = 0;
            this.pet.snapTo(this.roamTarget, this.pet.getYaw());
            this.pet.setBehavior(this.roamTarget, 0.0, false, rainy, false, 1.0);
            return;
        }
        if (this.pet.isMovementBlocked() && playerDistanceSqr <= 25.0) {
            this.roamTarget = null;
            this.airFlightDirection = null;
            this.airChasing = false;
            this.followRefreshTicks = 0;
            if (this.idleTicks <= 0) {
                this.idleTicks = this.randomBetween(18, 38);
            }
            --this.idleTicks;
            this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
            return;
        }
        if (!airbornePlayer && !shouldFollowPlayer && playerHorizontalSpeed < 0.02 && playerDistanceSqr < 7.290000000000001) {
            this.followRefreshTicks = 0;
            this.roamTarget = null;
            this.airFlightDirection = null;
            this.airChasing = false;
            if (this.idleTicks <= 0) {
                this.idleTicks = this.randomBetween(24, 52);
            }
            --this.idleTicks;
            this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
            return;
        }
        if (airbornePlayer) {
            Vec3d desiredAirTarget;
            boolean airborneMoving;
            this.idleTicks = 0;
            airborneMoving = playerHorizontalSpeed > 0.08 || Math.abs(player.getVelocity().y) > 0.08;
            if (airborneMoving) {
                this.followRefreshTicks = 0;
                this.airHoverOffset = null;
                desiredAirTarget = this.computeAirFollowTarget(player, playerDistance, playerHorizontalSpeed);
            } else {
                if (this.airHoverOffset == null || this.followRefreshTicks <= 0) {
                    this.airHoverOffset = this.pickAirHoverOffset(player, current);
                    this.followRefreshTicks = this.randomBetween(20, 42);
                } else {
                    --this.followRefreshTicks;
                }
                desiredAirTarget = this.computeAirIdleTarget(player);
            }
            if (this.airSmoothedTarget == null) {
                this.airSmoothedTarget = current;
            }
            double smoothing = Math.clamp(0.07 + player.getVelocity().length() * (player.isGliding() ? 0.025 : 0.05) + Math.max(0.0, playerDistance - 1.5) * 0.012, 0.07, player.isGliding() ? 0.2 : 0.28);
            this.roamTarget = this.airSmoothedTarget = this.airSmoothedTarget.lerp(desiredAirTarget, smoothing);
        } else if (shouldFollowPlayer) {
            this.airHoverOffset = null;
            this.airSmoothedTarget = null;
            this.airFlightDirection = null;
            this.airChasing = false;
            this.idleTicks = 0;
            if (this.followRefreshTicks <= 0 || this.roamTarget == null) {
                if (playerDistance < 4.5 && this.random.nextFloat() < 0.08f) {
                    this.followSide *= -1;
                }
                Vec3d targetFollow = this.computeFollowTarget(player, playerDistance, playerHorizontalSpeed);
                float followSmoothing = (float) Math.clamp(0.22 + Math.max(0.0, playerDistance - 2.0) * 0.05 + playerHorizontalSpeed * 0.35, 0.22, 0.62);
                this.roamTarget = this.roamTarget == null ? targetFollow : this.roamTarget.lerp(targetFollow, followSmoothing);
                this.followRefreshTicks = playerDistance > 7.0 ? 1 : 2;
            } else {
                --this.followRefreshTicks;
            }
        } else {
            this.airHoverOffset = null;
            this.airSmoothedTarget = null;
            this.airFlightDirection = null;
            this.airChasing = false;
            this.followRefreshTicks = 0;
            if (this.idleTicks > 0) {
                --this.idleTicks;
                this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
                return;
            }
            if (this.roamTarget == null || this.shouldPickNewTarget(player, current)) {
                if (this.random.nextFloat() < 0.24f) {
                    this.roamTarget = null;
                    this.idleTicks = this.randomBetween(18, 42);
                    this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
                    return;
                }
                this.followSide = this.random.nextBoolean() ? 1 : -1;
                boolean closeRange = current.squaredDistanceTo(playerPos) > 16.0 || player.getVelocity().horizontalLengthSquared() > 0.04;
                this.roamTarget = this.pickRoamTarget(player, closeRange);
            }
        }
        if (this.roamTarget == null) {
            this.idleTicks = this.randomBetween(18, 36);
            this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
            return;
        }
        Vec3d desired = this.roamTarget;
        Vec3d toDesired = desired.subtract(current);
        double horizontalDistance = Math.hypot(toDesired.x, toDesired.z);
        double totalDistance = toDesired.length();
        if (airbornePlayer) {
            boolean forceCatchup;
            StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
            int speedLevel = speedEffect != null ? speedEffect.getAmplifier() + 1 : 0;
            double playerSpeed = player.getVelocity().length();
            double stopDistance = player.isGliding() ? 0.82 : 0.48;
            double startDistance = player.isGliding() ? 1.28 : 0.78;
            forceCatchup = playerDistance > 4.0 || playerSpeed > 1.2 || playerHorizontalSpeed > 0.55;
            if (forceCatchup || totalDistance >= startDistance) {
                this.airChasing = true;
            } else if (totalDistance <= stopDistance) {
                this.airChasing = false;
            }
            double speed = Math.clamp(0.22 + playerSpeed * (player.isGliding() ? 2.45 : 1.65) + Math.max(0.0, playerDistance - 1.0) * (player.isGliding() ? 0.24 : 0.18) + (double) speedLevel * 0.07 + (player.isSprinting() ? 0.08 : 0.0), 0.16, player.isGliding() ? (playerDistance > 18.0 ? 6.8 : (playerDistance > 12.0 ? 5.0 : (playerDistance > 7.0 ? 3.6 : 1.9))) : (playerDistance > 16.0 ? 3.2 : (playerDistance > 10.0 ? 2.4 : (playerDistance > 6.0 ? 1.55 : 0.95))));
            double animationSpeed = Math.clamp(1.15 + Math.max(0.0, playerDistance - 1.2) * 0.22 + playerSpeed * (player.isGliding() ? 1.8 : 1.35) + (double) speedLevel * 0.16, 1.0, player.isGliding() ? 3.1 : 2.4);
            this.pet.setBehavior(desired, speed, this.airChasing, false, true, animationSpeed);
            return;
        }
        if (horizontalDistance <= TARGET_REACHED_DISTANCE && Math.abs(toDesired.y) <= 0.45) {
            this.roamTarget = null;
            this.idleTicks = shouldFollowPlayer ? this.randomBetween(8, 16) : this.randomBetween(18, 42);
            this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
            return;
        }
        StatusEffectInstance speedEffect = player.getStatusEffect(StatusEffects.SPEED);
        int speedLevel = speedEffect != null ? speedEffect.getAmplifier() + 1 : 0;
        double speed = Math.clamp(0.055 + playerHorizontalSpeed * 0.31 + Math.max(0.0, playerDistance - 1.8) * 0.072 + (double) speedLevel * 0.028 + (player.isSprinting() ? 0.028 : 0.0), 0.045, playerDistance > 8.5 ? 0.52 : (playerDistance > 6.0 ? 0.42 : (playerDistance > 4.0 ? 0.31 : 0.22)));
        double animationSpeed = Math.clamp(1.0 + Math.max(0.0, playerDistance - 1.4) * 0.14 + playerHorizontalSpeed * 0.88 + (double) speedLevel * 0.12 + (player.isSprinting() ? 0.06 : 0.0), 0.95, 1.85);
        this.pet.setBehavior(desired, speed, true, rainy, false, animationSpeed);
    }

    private boolean shouldPickNewTarget(PlayerEntity player, Vec3d current) {
        if (this.roamTarget == null) {
            return true;
        }
        if (this.roamTarget.squaredDistanceTo(player.getEntityPos()) > 36.0) {
            return true;
        }
        return current.squaredDistanceTo(player.getEntityPos()) > 25.0 && current.squaredDistanceTo(this.roamTarget) > 25.0;
    }

    private boolean isRainingAbove(Vec3d pos) {
        if (this.mc.world == null) {
            return false;
        }
        return this.mc.world.hasRain(BlockPos.ofFloored(pos.x, pos.y + 1.1, pos.z));
    }

    private Vec3d pickRoamTarget(PlayerEntity player, boolean closeRange) {
        Vec3d motion = player.getVelocity();
        double baseAngle = motion.horizontalLengthSquared() > 0.0025 ? Math.atan2(motion.z, motion.x) : this.random.nextDouble() * 6.2831854820251465;
        double angle = baseAngle + MathHelper.lerp(this.random.nextDouble(), -1.65, 1.65);
        double radius = closeRange ? MathHelper.lerp(this.random.nextDouble(), 1.6, 3.0) : MathHelper.lerp(this.random.nextDouble(), 2.5, 5.75);
        double x = player.getX() + Math.cos(angle) * radius;
        double z = player.getZ() + Math.sin(angle) * radius;
        double y = this.findGroundY(x, z, player.getY());
        return new Vec3d(x, y, z);
    }

    private Vec3d computeFollowTarget(PlayerEntity player, double playerDistance, double playerHorizontalSpeed) {
        Vec3d motion = player.getVelocity();
        Vec3d direction = new Vec3d(motion.x, 0.0, motion.z);
        if (direction.lengthSquared() < 1.0E-4) {
            float yaw = player.getYaw() * ((float) Math.PI / 180);
            direction = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
        }
        direction = direction.normalize();
        double leadDistance = Math.clamp(0.6 + playerHorizontalSpeed * 7.0 + Math.max(0.0, playerDistance - 3.0) * 0.35, 0.6, 3.0);
        Vec3d predictedPlayerPos = player.getEntityPos().add(player.getVelocity().multiply(leadDistance));
        double backOffset = playerDistance > 5.5 ? 0.65 : 1.45;
        double sideOffset = playerDistance > 5.5 ? 0.0 : 0.55 * (double) this.followSide;
        Vec3d side = new Vec3d(-direction.z, 0.0, direction.x);
        Vec3d followPos = predictedPlayerPos.subtract(direction.multiply(backOffset)).add(side.multiply(sideOffset));
        double y = this.findGroundY(followPos.x, followPos.z, player.getY());
        return new Vec3d(followPos.x, y, followPos.z);
    }

    private Vec3d computeAirFollowTarget(PlayerEntity player, double playerDistance, double playerHorizontalSpeed) {
        Vec3d motion = player.getVelocity();
        double playerSpeed = motion.length();
        Vec3d desiredDirection = new Vec3d(motion.x, 0.0, motion.z);
        if (desiredDirection.lengthSquared() < 1.0E-4) {
            float yaw = player.getYaw() * ((float) Math.PI / 180);
            desiredDirection = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
        }
        desiredDirection = desiredDirection.normalize();
        if (this.airFlightDirection == null || this.airFlightDirection.lengthSquared() < 1.0E-4) {
            this.airFlightDirection = desiredDirection;
        } else {
            double directionSmoothing = player.isGliding() ? 0.14 : 0.26;
            this.airFlightDirection = this.airFlightDirection.lerp(desiredDirection, directionSmoothing);
            if (this.airFlightDirection.lengthSquared() < 1.0E-4) {
                this.airFlightDirection = desiredDirection;
            }
        }
        Vec3d direction = this.airFlightDirection.normalize();
        Vec3d side = new Vec3d(-direction.z, 0.0, direction.x);
        boolean firstPerson = this.mc.options.getPerspective().isFirstPerson();
        double sideOffset = (player.isGliding() ? 0.82 : 0.65) * (double) this.followSide;
        double backOffset = (player.isGliding() ? 1.45 : 1.05) + Math.min(playerDistance * 0.08, player.isGliding() ? 0.55 : 0.35);
        if (player.isGliding() && firstPerson) {
            sideOffset *= 1.18;
            backOffset += 0.42;
        }
        double liftOffset = player.isGliding() ? 0.2 : 0.45;
        double leadDistance = Math.clamp(0.45 + playerHorizontalSpeed * (player.isGliding() ? 5.8 : 4.8) + Math.max(0.0, playerSpeed - 0.8) * (player.isGliding() ? 0.45 : 0.35), 0.45, player.isGliding() ? 2.8 : 1.6);
        Vec3d predictedPlayerPos = player.getEntityPos().add(direction.multiply(leadDistance));
        double bob = Math.sin((double) (player.age + this.followSide * 7) * 0.1) * 0.028;
        return predictedPlayerPos.subtract(direction.multiply(backOffset)).add(side.multiply(sideOffset)).add(0.0, player.getY() - predictedPlayerPos.y + liftOffset + bob, 0.0);
    }

    private Vec3d computeAirIdleTarget(PlayerEntity player) {
        Vec3d offset = this.airHoverOffset != null ? this.airHoverOffset : new Vec3d(1.6 * (double) this.followSide, 0.35, 0.0);
        double bob = Math.sin((double) (player.age + this.followSide * 7) * 0.085) * 0.09;
        return player.getEntityPos().add(offset.x, offset.y + bob, offset.z);
    }

    private Vec3d pickAirHoverOffset(PlayerEntity player, Vec3d current) {
        Vec3d offset = current.subtract(player.getEntityPos());
        Vec3d horizontal = new Vec3d(offset.x, 0.0, offset.z);
        double horizontalLength = horizontal.length();
        if (horizontalLength < 0.9 || horizontalLength > 2.5) {
            double angle = this.random.nextDouble() * 6.2831854820251465;
            double radius = MathHelper.lerp(this.random.nextDouble(), 1.35, 1.95);
            return new Vec3d(Math.cos(angle) * radius, MathHelper.lerp(this.random.nextDouble(), 0.28, 0.44), Math.sin(angle) * radius);
        }
        Vec3d normalized = horizontal.normalize().multiply(MathHelper.clamp(horizontalLength, 1.25, 1.95));
        double y = MathHelper.clamp(offset.y, 0.24, 0.46);
        return new Vec3d(normalized.x, y, normalized.z);
    }

    private int randomBetween(int min, int max) {
        return min + this.random.nextInt(max - min + 1);
    }

    private Vec3d snapCurrentToGround(Vec3d current, double fallbackY) {
        double groundY = this.findGroundY(current.x, current.z, Math.max(current.y, fallbackY));
        return new Vec3d(current.x, groundY, current.z);
    }

    private double findGroundY(double x, double z, double fallbackY) {
        if (this.mc.world == null) {
            return fallbackY;
        }
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);
        int baseY = MathHelper.floor(fallbackY);
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(blockX, baseY + 2, blockZ);
        for (int y = baseY + 2; y >= baseY - 6; --y) {
            mutablePos.setY(y);
            VoxelShape shape = this.mc.world.getBlockState(mutablePos).getCollisionShape((BlockView) this.mc.world, mutablePos);
            if (shape.isEmpty()) {
                continue;
            }
            return (double) y + shape.getMax(Direction.Axis.Y);
        }
        return fallbackY;
    }
}
