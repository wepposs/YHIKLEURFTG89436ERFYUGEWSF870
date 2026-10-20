package vesence.module.impl.visuals.custompet;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.util.RandomSource;
import com.mojang.math.Axis;

/**
 * Контроллер слежения локального питомца за игроком. Перенесён из RelevantPremiumpp4
 * (Yarn 1.21.4 -> Yarn 1.21.11). Логика версионно-независимая.
 *
 *  - {@link #tick(Player, CustomPetVariant)} — каждый клиентский тик;
 *  - {@link #reset()} — удалить питомца и сбросить состояние.
 */
public final class CustomPetFollowerController {
    private static final double MAX_WANDER_DISTANCE = 6.0;
    private static final double SNAP_BACK_DISTANCE = 9.0;
    private static final double FOLLOW_BREAK_DISTANCE = 5.0;
    private static final double TARGET_REACHED_DISTANCE = 0.9;

    private final Minecraft mc = Minecraft.getInstance();
    private final net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create();

    private CustomPetEntity pet;
    private Vec3 roamTarget;
    private Vec3 airHoverOffset;
    private Vec3 airSmoothedTarget;
    private Vec3 airFlightDirection;
    private boolean airChasing;
    private int idleTicks;
    private int followRefreshTicks;
    private int followSide = 1;

    public CustomPetEntity getPet() {
        return this.pet;
    }

    public void tick(Player player, CustomPetVariant variant, float scale) {
        if (player == null || player.level() == null || this.mc.level == null) {
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
            if (this.mc.level != null) {
                this.mc.level.removeEntity(this.pet.getId(), Entity.RemovalReason.DISCARDED);
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

    private void ensurePet(Player player, CustomPetVariant variant) {
        if (this.pet != null && this.pet.level() == this.mc.level && !this.pet.isRemoved()) {
            this.pet.setPetVariant(variant);
            return;
        }
        this.reset();
        this.pet = new CustomPetEntity(this.mc.level);
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
        this.mc.level.addEntity(this.pet);
    }

    private void tickPet(Player player, CustomPetVariant variant) {
        if (this.pet == null) {
            return;
        }
        this.pet.setPetVariant(variant);
        Vec3 current = this.pet.position();
        Vec3 groundedCurrent = this.snapCurrentToGround(current, player.getY());
        Vec3 playerPos = player.position();
        double playerDistanceSqr = current.distanceToSqr(playerPos);
        double playerDistance = Math.sqrt(playerDistanceSqr);
        double playerHorizontalSpeed = player.getDeltaMovement().horizontalDistance();
        boolean airbornePlayer = player.isFallFlying() || player.getAbilities().flying;
        boolean playerMovingFast = playerHorizontalSpeed > 0.17 || Math.abs(player.getDeltaMovement().y) > 0.08;
        boolean shouldFollowPlayer = playerDistanceSqr > 25.0 || playerMovingFast && playerDistanceSqr > 9.610000000000001;
        boolean rainy = this.isRainingAbove(current);
        if (playerDistanceSqr > 81.0) {
            this.roamTarget = this.pickRoamTarget(player, true);
            this.airFlightDirection = null;
            this.airChasing = false;
            this.idleTicks = this.randomBetween(12, 22);
            this.followRefreshTicks = 0;
            this.pet.snapTo(this.roamTarget, this.pet.getYRot());
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
            Vec3 desiredAirTarget;
            boolean airborneMoving;
            this.idleTicks = 0;
            airborneMoving = playerHorizontalSpeed > 0.08 || Math.abs(player.getDeltaMovement().y) > 0.08;
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
            double smoothing = Math.clamp(0.07 + player.getDeltaMovement().length() * (player.isFallFlying() ? 0.025 : 0.05) + Math.max(0.0, playerDistance - 1.5) * 0.012, 0.07, player.isFallFlying() ? 0.2 : 0.28);
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
                Vec3 targetFollow = this.computeFollowTarget(player, playerDistance, playerHorizontalSpeed);
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
                boolean closeRange = current.distanceToSqr(playerPos) > 16.0 || player.getDeltaMovement().horizontalDistanceSqr() > 0.04;
                this.roamTarget = this.pickRoamTarget(player, closeRange);
            }
        }
        if (this.roamTarget == null) {
            this.idleTicks = this.randomBetween(18, 36);
            this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
            return;
        }
        Vec3 desired = this.roamTarget;
        Vec3 toDesired = desired.subtract(current);
        double horizontalDistance = Math.hypot(toDesired.x, toDesired.z);
        double totalDistance = toDesired.length();
        if (airbornePlayer) {
            boolean forceCatchup;
            MobEffectInstance speedEffect = player.getEffect(MobEffects.SPEED);
            int speedLevel = speedEffect != null ? speedEffect.getAmplifier() + 1 : 0;
            double playerSpeed = player.getDeltaMovement().length();
            double stopDistance = player.isFallFlying() ? 0.82 : 0.48;
            double startDistance = player.isFallFlying() ? 1.28 : 0.78;
            forceCatchup = playerDistance > 4.0 || playerSpeed > 1.2 || playerHorizontalSpeed > 0.55;
            if (forceCatchup || totalDistance >= startDistance) {
                this.airChasing = true;
            } else if (totalDistance <= stopDistance) {
                this.airChasing = false;
            }
            double speed = Math.clamp(0.22 + playerSpeed * (player.isFallFlying() ? 2.45 : 1.65) + Math.max(0.0, playerDistance - 1.0) * (player.isFallFlying() ? 0.24 : 0.18) + (double) speedLevel * 0.07 + (player.isSprinting() ? 0.08 : 0.0), 0.16, player.isFallFlying() ? (playerDistance > 18.0 ? 6.8 : (playerDistance > 12.0 ? 5.0 : (playerDistance > 7.0 ? 3.6 : 1.9))) : (playerDistance > 16.0 ? 3.2 : (playerDistance > 10.0 ? 2.4 : (playerDistance > 6.0 ? 1.55 : 0.95))));
            double animationSpeed = Math.clamp(1.15 + Math.max(0.0, playerDistance - 1.2) * 0.22 + playerSpeed * (player.isFallFlying() ? 1.8 : 1.35) + (double) speedLevel * 0.16, 1.0, player.isFallFlying() ? 3.1 : 2.4);
            this.pet.setBehavior(desired, speed, this.airChasing, false, true, animationSpeed);
            return;
        }
        if (horizontalDistance <= TARGET_REACHED_DISTANCE && Math.abs(toDesired.y) <= 0.45) {
            this.roamTarget = null;
            this.idleTicks = shouldFollowPlayer ? this.randomBetween(8, 16) : this.randomBetween(18, 42);
            this.pet.setBehavior(groundedCurrent, 0.0, false, rainy, false, 1.0);
            return;
        }
        MobEffectInstance speedEffect = player.getEffect(MobEffects.SPEED);
        int speedLevel = speedEffect != null ? speedEffect.getAmplifier() + 1 : 0;
        double speed = Math.clamp(0.055 + playerHorizontalSpeed * 0.31 + Math.max(0.0, playerDistance - 1.8) * 0.072 + (double) speedLevel * 0.028 + (player.isSprinting() ? 0.028 : 0.0), 0.045, playerDistance > 8.5 ? 0.52 : (playerDistance > 6.0 ? 0.42 : (playerDistance > 4.0 ? 0.31 : 0.22)));
        double animationSpeed = Math.clamp(1.0 + Math.max(0.0, playerDistance - 1.4) * 0.14 + playerHorizontalSpeed * 0.88 + (double) speedLevel * 0.12 + (player.isSprinting() ? 0.06 : 0.0), 0.95, 1.85);
        this.pet.setBehavior(desired, speed, true, rainy, false, animationSpeed);
    }

    private boolean shouldPickNewTarget(Player player, Vec3 current) {
        if (this.roamTarget == null) {
            return true;
        }
        if (this.roamTarget.distanceToSqr(player.position()) > 36.0) {
            return true;
        }
        return current.distanceToSqr(player.position()) > 25.0 && current.distanceToSqr(this.roamTarget) > 25.0;
    }

    private boolean isRainingAbove(Vec3 pos) {
        if (this.mc.level == null) {
            return false;
        }
        return this.mc.level.isRainingAt(BlockPos.containing(pos.x, pos.y + 1.1, pos.z));
    }

    private Vec3 pickRoamTarget(Player player, boolean closeRange) {
        Vec3 motion = player.getDeltaMovement();
        double baseAngle = motion.horizontalDistanceSqr() > 0.0025 ? Math.atan2(motion.z, motion.x) : this.random.nextDouble() * 6.2831854820251465;
        double angle = baseAngle + Mth.lerp(this.random.nextDouble(), -1.65, 1.65);
        double radius = closeRange ? Mth.lerp(this.random.nextDouble(), 1.6, 3.0) : Mth.lerp(this.random.nextDouble(), 2.5, 5.75);
        double x = player.getX() + Math.cos(angle) * radius;
        double z = player.getZ() + Math.sin(angle) * radius;
        double y = this.findGroundY(x, z, player.getY());
        return new Vec3(x, y, z);
    }

    private Vec3 computeFollowTarget(Player player, double playerDistance, double playerHorizontalSpeed) {
        Vec3 motion = player.getDeltaMovement();
        Vec3 direction = new Vec3(motion.x, 0.0, motion.z);
        if (direction.lengthSqr() < 1.0E-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180);
            direction = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        }
        direction = direction.normalize();
        double leadDistance = Math.clamp(0.6 + playerHorizontalSpeed * 7.0 + Math.max(0.0, playerDistance - 3.0) * 0.35, 0.6, 3.0);
        Vec3 predictedPlayerPos = player.position().add(player.getDeltaMovement().scale(leadDistance));
        double backOffset = playerDistance > 5.5 ? 0.65 : 1.45;
        double sideOffset = playerDistance > 5.5 ? 0.0 : 0.55 * (double) this.followSide;
        Vec3 side = new Vec3(-direction.z, 0.0, direction.x);
        Vec3 followPos = predictedPlayerPos.subtract(direction.scale(backOffset)).add(side.scale(sideOffset));
        double y = this.findGroundY(followPos.x, followPos.z, player.getY());
        return new Vec3(followPos.x, y, followPos.z);
    }

    private Vec3 computeAirFollowTarget(Player player, double playerDistance, double playerHorizontalSpeed) {
        Vec3 motion = player.getDeltaMovement();
        double playerSpeed = motion.length();
        Vec3 desiredDirection = new Vec3(motion.x, 0.0, motion.z);
        if (desiredDirection.lengthSqr() < 1.0E-4) {
            float yaw = player.getYRot() * ((float) Math.PI / 180);
            desiredDirection = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        }
        desiredDirection = desiredDirection.normalize();
        if (this.airFlightDirection == null || this.airFlightDirection.lengthSqr() < 1.0E-4) {
            this.airFlightDirection = desiredDirection;
        } else {
            double directionSmoothing = player.isFallFlying() ? 0.14 : 0.26;
            this.airFlightDirection = this.airFlightDirection.lerp(desiredDirection, directionSmoothing);
            if (this.airFlightDirection.lengthSqr() < 1.0E-4) {
                this.airFlightDirection = desiredDirection;
            }
        }
        Vec3 direction = this.airFlightDirection.normalize();
        Vec3 side = new Vec3(-direction.z, 0.0, direction.x);
        boolean firstPerson = this.mc.options.getCameraType().isFirstPerson();
        double sideOffset = (player.isFallFlying() ? 0.82 : 0.65) * (double) this.followSide;
        double backOffset = (player.isFallFlying() ? 1.45 : 1.05) + Math.min(playerDistance * 0.08, player.isFallFlying() ? 0.55 : 0.35);
        if (player.isFallFlying() && firstPerson) {
            sideOffset *= 1.18;
            backOffset += 0.42;
        }
        double liftOffset = player.isFallFlying() ? 0.2 : 0.45;
        double leadDistance = Math.clamp(0.45 + playerHorizontalSpeed * (player.isFallFlying() ? 5.8 : 4.8) + Math.max(0.0, playerSpeed - 0.8) * (player.isFallFlying() ? 0.45 : 0.35), 0.45, player.isFallFlying() ? 2.8 : 1.6);
        Vec3 predictedPlayerPos = player.position().add(direction.scale(leadDistance));
        double bob = Math.sin((double) (player.tickCount + this.followSide * 7) * 0.1) * 0.028;
        return predictedPlayerPos.subtract(direction.scale(backOffset)).add(side.scale(sideOffset)).add(0.0, player.getY() - predictedPlayerPos.y + liftOffset + bob, 0.0);
    }

    private Vec3 computeAirIdleTarget(Player player) {
        Vec3 offset = this.airHoverOffset != null ? this.airHoverOffset : new Vec3(1.6 * (double) this.followSide, 0.35, 0.0);
        double bob = Math.sin((double) (player.tickCount + this.followSide * 7) * 0.085) * 0.09;
        return player.position().add(offset.x, offset.y + bob, offset.z);
    }

    private Vec3 pickAirHoverOffset(Player player, Vec3 current) {
        Vec3 offset = current.subtract(player.position());
        Vec3 horizontal = new Vec3(offset.x, 0.0, offset.z);
        double horizontalLength = horizontal.length();
        if (horizontalLength < 0.9 || horizontalLength > 2.5) {
            double angle = this.random.nextDouble() * 6.2831854820251465;
            double radius = Mth.lerp(this.random.nextDouble(), 1.35, 1.95);
            return new Vec3(Math.cos(angle) * radius, Mth.lerp(this.random.nextDouble(), 0.28, 0.44), Math.sin(angle) * radius);
        }
        Vec3 normalized = horizontal.normalize().scale(Mth.clamp(horizontalLength, 1.25, 1.95));
        double y = Mth.clamp(offset.y, 0.24, 0.46);
        return new Vec3(normalized.x, y, normalized.z);
    }

    private int randomBetween(int min, int max) {
        return min + this.random.nextInt(max - min + 1);
    }

    private Vec3 snapCurrentToGround(Vec3 current, double fallbackY) {
        double groundY = this.findGroundY(current.x, current.z, Math.max(current.y, fallbackY));
        return new Vec3(current.x, groundY, current.z);
    }

    private double findGroundY(double x, double z, double fallbackY) {
        if (this.mc.level == null) {
            return fallbackY;
        }
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        int baseY = Mth.floor(fallbackY);
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(blockX, baseY + 2, blockZ);
        for (int y = baseY + 2; y >= baseY - 6; --y) {
            mutablePos.setY(y);
            VoxelShape shape = this.mc.level.getBlockState(mutablePos).getCollisionShape(this.mc.level, mutablePos);
            if (shape.isEmpty()) {
                continue;
            }
            return (double) y + shape.max(Direction.Axis.Y);
        }
        return fallbackY;
    }
}
