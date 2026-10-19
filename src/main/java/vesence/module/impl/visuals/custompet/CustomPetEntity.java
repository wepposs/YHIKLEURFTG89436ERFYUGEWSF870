package vesence.module.impl.visuals.custompet;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.animation.state.AnimationTest;
import software.bernie.geckolib.util.GeckoLibUtil;

public class CustomPetEntity extends FrogEntity implements GeoEntity {
    private static final AtomicInteger NEXT_ENTITY_ID = new AtomicInteger(-20000);
    private static final double MOVEMENT_ANIMATION_THRESHOLD_SQR = 2.5E-5;
    private static final double MOVEMENT_VERTICAL_THRESHOLD = 0.003;

    private static final SoundEvent AMBIENT_SOUND = SoundEvent.of(Identifier.of("vesence", "entity.ribbit.ambient"));
    private static final SoundEvent STEP_SOUND = SoundEvent.of(Identifier.of("vesence", "entity.ribbit.step"));

    private static final RawAnimation IDLE = RawAnimation.begin().thenPlay("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenPlay("walk");
    private static final RawAnimation RAIN_IDLE = RawAnimation.begin().thenPlay("idle_holding_1");
    private static final RawAnimation RAIN_WALK = RawAnimation.begin().thenPlay("walk_holding_1");
    private static final RawAnimation AIR_IDLE = RawAnimation.begin().thenPlay("idle_holding_2");
    private static final RawAnimation AIR_WALK = RawAnimation.begin().thenPlay("walk_holding_2");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private Vec3d desiredPosition = Vec3d.ZERO;
    private double desiredSpeed;
    private boolean desiredMoving;
    private boolean desiredUmbrella;
    private boolean desiredAirborne;
    private CustomPetVariant variant = CustomPetVariant.NITWIT;
    private float petScale = 1.0f;
    private boolean moving;
    private boolean usingUmbrella;
    private boolean airborneMode;
    private int movingTicks;
    private int ambientSoundCooldown;
    private int stepSoundCooldown;
    private int jumpCooldown;
    private int blockedTicks;
    private int pauseTicks;
    private float targetYaw;
    private float smoothedYaw;
    private double animationSpeed = 1.0;
    private double currentAnimationSpeed = 1.0;
    private double currentGroundSpeed;
    private double verticalVelocity;
    private double lastHorizontalDistance = Double.MAX_VALUE;
    private Vec3d airMotion = Vec3d.ZERO;

    public CustomPetEntity(World level) {
        this(CustomPetRegistry.CUSTOM_PET, level);
    }

    public CustomPetEntity(EntityType<? extends CustomPetEntity> type, World level) {
        super(type, level);
        this.noClip = false;
        this.setNoGravity(false);
        this.setAiDisabled(true);
        this.setId(NEXT_ENTITY_ID.getAndDecrement());
        this.setUuid(UUID.randomUUID());
        this.setVelocity(Vec3d.ZERO);
        this.targetYaw = this.getYaw();
        this.smoothedYaw = this.getYaw();
        this.ambientSoundCooldown = 70;
        this.verticalVelocity = 0.0;
    }

    public void setBehavior(Vec3d desiredPosition, double desiredSpeed, boolean desiredMoving, boolean desiredUmbrella, boolean desiredAirborne, double animationSpeed) {
        if (desiredPosition.squaredDistanceTo(this.desiredPosition) > 0.2) {
            this.blockedTicks = 0;
            this.pauseTicks = 0;
            this.lastHorizontalDistance = Double.MAX_VALUE;
        }
        this.desiredPosition = desiredPosition;
        this.desiredSpeed = desiredSpeed;
        this.desiredMoving = desiredMoving;
        this.desiredUmbrella = desiredUmbrella;
        this.desiredAirborne = desiredAirborne;
        this.animationSpeed = animationSpeed;
    }

    public void snapTo(Vec3d pos, float yaw) {
        this.setPosition(pos.x, pos.y, pos.z);
        this.lastX = pos.x;
        this.lastY = pos.y;
        this.lastZ = pos.z;
        this.lastYaw = yaw;
        this.lastPitch = 0.0f;
        this.moving = false;
        this.usingUmbrella = false;
        this.airborneMode = false;
        this.movingTicks = 0;
        this.stepSoundCooldown = 0;
        this.ambientSoundCooldown = 40;
        this.jumpCooldown = 0;
        this.blockedTicks = 0;
        this.pauseTicks = 0;
        this.targetYaw = yaw;
        this.smoothedYaw = yaw;
        this.animationSpeed = 1.0;
        this.currentAnimationSpeed = 1.0;
        this.currentGroundSpeed = 0.0;
        this.verticalVelocity = 0.0;
        this.lastHorizontalDistance = Double.MAX_VALUE;
        this.airMotion = Vec3d.ZERO;
        this.setVelocity(Vec3d.ZERO);
        this.setYaw(yaw);
        this.setHeadYaw(yaw);
        this.setBodyYaw(yaw);
    }

    public boolean shouldUseUmbrella() {
        return this.usingUmbrella;
    }

    public boolean isAirborneMode() {
        return this.airborneMode;
    }

    public CustomPetVariant getPetVariant() {
        return this.variant;
    }

    public boolean isPetMoving() {
        return this.moving;
    }

    public double getCurrentAnimationSpeed() {
        return this.currentAnimationSpeed;
    }

    public void setPetVariant(CustomPetVariant variant) {
        this.variant = variant == null ? CustomPetVariant.NITWIT : variant;
    }

    public float getPetScale() {
        return this.petScale;
    }

    public void setPetScale(float petScale) {
        this.petScale = petScale <= 0.0f ? 1.0f : petScale;
    }

    public boolean isMovementBlocked() {
        return this.pauseTicks > 0 || this.blockedTicks >= 3;
    }

    @Override
    public void tick() {
        this.setNoGravity(this.desiredAirborne);
        super.tick();
        this.tickCustomMovement();
    }

    @Override
    public int getMaxLookPitchChange() {
        return 0;
    }

    @Override
    public int getMaxHeadRotation() {
        return 0;
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    @Override
    public boolean canHit() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isCollidable(Entity entity) {
        return false;
    }

    @Override
    public boolean collidesWith(Entity entity) {
        return false;
    }

    @Override
    public void pushAwayFrom(Entity entity) {
    }

    @Override
    protected void pushAway(Entity entity) {
    }

    @Override
    protected void tickCramming() {
    }

    @Override
    public boolean isInteractable() {
        return false;
    }

    @Override
    public void addVelocity(double x, double y, double z) {
    }

    @Override
    public void addVelocity(Vec3d movement) {
    }

    @Override
    public PistonBehavior getPistonBehavior() {
        return PistonBehavior.IGNORE;
    }

    private void tickCustomMovement() {
        boolean madeProgress;
        boolean actualMoving;
        boolean keepMoving;
        boolean wantsToMove;
        boolean wasAirborne = this.airborneMode;
        this.airborneMode = this.desiredAirborne;
        this.usingUmbrella = this.desiredUmbrella && !this.airborneMode;
        this.setNoGravity(this.airborneMode);
        if (!(this.airborneMode || !wasAirborne && this.desiredMoving)) {
            this.snapToGroundIfClose();
        }
        if (this.jumpCooldown > 0) {
            --this.jumpCooldown;
        }
        if (this.pauseTicks > 0) {
            --this.pauseTicks;
            this.moving = false;
            this.movingTicks = 0;
            this.currentGroundSpeed = 0.0;
            this.verticalVelocity = 0.0;
            this.airMotion = Vec3d.ZERO;
            this.setVelocity(Vec3d.ZERO);
            this.currentAnimationSpeed += (1.0 - this.currentAnimationSpeed) * 0.28;
            this.lastYaw = this.smoothedYaw;
            this.setYaw(this.smoothedYaw);
            this.setHeadYaw(this.smoothedYaw);
            this.setBodyYaw(this.smoothedYaw);
            this.tickClientAudio(false, 0.0);
            return;
        }
        if (this.airborneMode) {
            this.tickAirMovement();
            return;
        }
        this.airMotion = Vec3d.ZERO;
        Vec3d current = this.getEntityPos();
        Vec3d toTarget = this.desiredPosition.subtract(current);
        Vec3d horizontal = new Vec3d(toTarget.x, 0.0, toTarget.z);
        double horizontalDistance = horizontal.horizontalLength();
        wantsToMove = this.desiredMoving && horizontalDistance > 0.045;
        if (wantsToMove) {
            float desiredYaw = (float) Math.toDegrees(MathHelper.atan2(horizontal.z, horizontal.x)) - 90.0f;
            float yawApproach = (float) Math.clamp(9.5 + horizontalDistance * 2.4 + this.desiredSpeed * 68.0, 9.5, 24.0);
            this.targetYaw = MathHelper.stepUnwrappedAngleTowards(this.targetYaw, desiredYaw, yawApproach);
        }
        float turnSpeed = wantsToMove ? (float) Math.clamp(8.5 + horizontalDistance * 1.8 + this.desiredSpeed * 86.0, 9.0, 22.0) : 4.8f;
        this.smoothedYaw = MathHelper.stepUnwrappedAngleTowards(this.smoothedYaw, this.targetYaw, turnSpeed);
        float remainingYawDifference = Math.abs(MathHelper.wrapDegrees(this.targetYaw - this.smoothedYaw));
        double turnPenalty = wantsToMove ? MathHelper.clamp(1.0 - Math.max(0.0, (double) (remainingYawDifference - 6.0f)) / 78.0, 0.18, 1.0) : 0.0;
        double slowdownFactor = wantsToMove ? MathHelper.clamp((horizontalDistance - 0.06) / 1.2, 0.14, 1.0) : 0.0;
        double targetGroundSpeed = wantsToMove ? Math.min(horizontalDistance, this.desiredSpeed * slowdownFactor * turnPenalty) : 0.0;
        double groundAcceleration = wantsToMove ? Math.clamp(0.18 + this.desiredSpeed * 0.95 + horizontalDistance * 0.06, 0.18, 0.52) : 0.24;
        this.currentGroundSpeed += (targetGroundSpeed - this.currentGroundSpeed) * groundAcceleration;
        if (Math.abs(this.currentGroundSpeed) < 1.0E-4) {
            this.currentGroundSpeed = 0.0;
        }
        keepMoving = this.currentGroundSpeed > 0.012;
        this.moving = wantsToMove ? keepMoving || horizontalDistance > 0.18 : keepMoving;
        Vec3d horizontalMove = this.currentGroundSpeed > 1.0E-5 && horizontalDistance > 1.0E-5 ? horizontal.normalize().multiply(Math.min(horizontalDistance, this.currentGroundSpeed)) : Vec3d.ZERO;
        double yDiff = this.desiredPosition.y - this.getY();
        if (this.isOnGround()) {
            if (yDiff > 0.55 && horizontalDistance <= 1.45 && wantsToMove && this.currentGroundSpeed > 0.035 && this.jumpCooldown <= 0 && this.blockedTicks < 3) {
                this.verticalVelocity = 0.34;
                this.jumpCooldown = 11;
            } else {
                this.verticalVelocity = yDiff < -0.35 ? Math.max(-0.16, yDiff) : 0.0;
            }
        } else {
            this.verticalVelocity = Math.max(this.verticalVelocity - 0.08, -0.36);
        }
        Vec3d moveVec = new Vec3d(horizontalMove.x, this.verticalVelocity, horizontalMove.z);
        this.move(MovementType.SELF, moveVec);
        Vec3d movedTo = this.getEntityPos();
        double actualHorizontalDeltaSqr = this.square(movedTo.x - current.x) + this.square(movedTo.z - current.z);
        actualMoving = actualHorizontalDeltaSqr > MOVEMENT_ANIMATION_THRESHOLD_SQR || Math.abs(movedTo.y - current.y) > MOVEMENT_VERTICAL_THRESHOLD;
        if (this.isOnGround() && this.verticalVelocity < 0.0) {
            this.verticalVelocity = 0.0;
        }
        if (this.isOnGround() && this.verticalVelocity <= 0.0 && this.desiredPosition.y <= this.getY() && Math.abs(this.desiredPosition.y - this.getY()) < 0.18) {
            this.setPosition(this.getX(), this.desiredPosition.y, this.getZ());
        }
        this.moving = wantsToMove && actualMoving;
        Vec3d remaining = this.desiredPosition.subtract(this.getEntityPos());
        double remainingHorizontalDistance = Math.hypot(remaining.x, remaining.z);
        madeProgress = remainingHorizontalDistance + 0.025 < this.lastHorizontalDistance;
        if (this.currentGroundSpeed > 0.02 && this.horizontalCollision && this.isOnGround()) {
            ++this.blockedTicks;
        } else if (!wantsToMove || madeProgress || remainingHorizontalDistance < 0.8) {
            this.blockedTicks = 0;
        }
        if (this.blockedTicks >= 4) {
            this.pauseTicks = 8;
            this.blockedTicks = 0;
            this.moving = false;
            this.movingTicks = 0;
            this.currentGroundSpeed = 0.0;
            this.verticalVelocity = 0.0;
        }
        this.lastHorizontalDistance = wantsToMove ? remainingHorizontalDistance : Double.MAX_VALUE;
        double animationTarget = this.moving ? Math.max(1.0, this.animationSpeed) : 1.0;
        this.currentAnimationSpeed += (animationTarget - this.currentAnimationSpeed) * (this.moving ? 0.22 : 0.18);
        this.setVelocity(Vec3d.ZERO);
        this.lastYaw = this.smoothedYaw;
        this.setYaw(this.smoothedYaw);
        this.setHeadYaw(this.smoothedYaw);
        this.setBodyYaw(this.smoothedYaw);
        this.tickClientAudio(this.moving, horizontalMove.horizontalLengthSquared());
    }

    private void tickAirMovement() {
        boolean idleVerticalHover;
        boolean wantsToMove;
        Vec3d current = this.getEntityPos();
        Vec3d toTarget = this.desiredPosition.subtract(current);
        Vec3d horizontal = new Vec3d(toTarget.x, 0.0, toTarget.z);
        double horizontalDistance = horizontal.horizontalLength();
        double totalDistance = toTarget.length();
        double verticalDistance = Math.abs(toTarget.y);
        wantsToMove = this.desiredMoving && totalDistance > 0.02;
        if (horizontalDistance > 1.0E-4) {
            float desiredYaw = (float) Math.toDegrees(MathHelper.atan2(horizontal.z, horizontal.x)) - 90.0f;
            this.targetYaw = MathHelper.stepUnwrappedAngleTowards(this.targetYaw, desiredYaw, 10.5f);
        }
        this.smoothedYaw = MathHelper.stepUnwrappedAngleTowards(this.smoothedYaw, this.targetYaw, wantsToMove ? 14.0f : 6.0f);
        float remainingYawDifference = Math.abs(MathHelper.wrapDegrees(this.targetYaw - this.smoothedYaw));
        boolean canAdvance = wantsToMove && (remainingYawDifference <= 9.0f || totalDistance <= 0.35);
        this.movingTicks = 0;
        this.jumpCooldown = 0;
        this.blockedTicks = 0;
        this.pauseTicks = 0;
        this.verticalVelocity = 0.0;
        boolean insideHoverDeadzone = horizontalDistance <= 0.16 && verticalDistance <= 0.012;
        boolean insideSettleZone = horizontalDistance <= 0.42 && verticalDistance <= 0.09;
        Vec3d desiredStep = Vec3d.ZERO;
        if (canAdvance && totalDistance > 1.0E-5 && !insideHoverDeadzone) {
            double slowdown = MathHelper.clamp((totalDistance - 0.2) / 1.6, 0.08, 1.0);
            double followStep = Math.clamp(this.desiredSpeed * slowdown, 0.012, 6.5);
            desiredStep = toTarget.normalize().multiply(Math.min(totalDistance, followStep));
        } else if (!this.desiredMoving && verticalDistance > 0.001) {
            double verticalStep = MathHelper.clamp(verticalDistance * 0.38, 0.0025, 0.018);
            desiredStep = new Vec3d(0.0, Math.copySign(Math.min(verticalDistance, verticalStep), toTarget.y), 0.0);
        }
        double blend = Math.clamp(0.12 + totalDistance * 0.06 + this.desiredSpeed * 0.028, 0.12, 0.38);
        this.airMotion = this.airMotion.lerp(desiredStep, blend);
        if (!this.desiredMoving) {
            this.airMotion = new Vec3d(this.airMotion.x * 0.4, this.airMotion.y, this.airMotion.z * 0.4);
        }
        double damping = insideHoverDeadzone ? 0.18 : (insideSettleZone ? 0.42 : (totalDistance < 1.5 ? 0.78 : 0.9));
        idleVerticalHover = !this.desiredMoving && verticalDistance > 0.001;
        if (!(canAdvance && !(desiredStep.lengthSquared() < 1.0E-6) || idleVerticalHover)) {
            this.airMotion = this.airMotion.multiply(damping);
        } else if (idleVerticalHover) {
            this.airMotion = new Vec3d(this.airMotion.x, this.airMotion.y * 0.96, this.airMotion.z);
        }
        if (insideHoverDeadzone && verticalDistance <= 0.0015 || this.airMotion.lengthSquared() < 4.0E-5) {
            this.airMotion = Vec3d.ZERO;
        }
        Vec3d moveVec = this.airMotion.lengthSquared() > 0.0 ? (this.airMotion.length() > totalDistance ? toTarget : this.airMotion) : Vec3d.ZERO;
        this.move(MovementType.SELF, moveVec);
        Vec3d movedTo = this.getEntityPos();
        double actualHorizontalDeltaSqr = this.square(movedTo.x - current.x) + this.square(movedTo.z - current.z);
        this.moving = wantsToMove && (actualHorizontalDeltaSqr > MOVEMENT_ANIMATION_THRESHOLD_SQR || Math.abs(movedTo.y - current.y) > MOVEMENT_VERTICAL_THRESHOLD);
        this.lastHorizontalDistance = wantsToMove ? horizontalDistance : Double.MAX_VALUE;
        double animationTarget = this.moving ? Math.max(1.0, this.animationSpeed) : 1.0;
        this.currentAnimationSpeed += (animationTarget - this.currentAnimationSpeed) * (this.moving ? 0.2 : 0.16);
        this.setVelocity(Vec3d.ZERO);
        this.lastYaw = this.smoothedYaw;
        this.setYaw(this.smoothedYaw);
        this.setHeadYaw(this.smoothedYaw);
        this.setBodyYaw(this.smoothedYaw);
        this.tickClientAudio(false, 0.0);
    }

    private void snapToGroundIfClose() {
        if (this.getEntityWorld() == null) {
            return;
        }
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();
        int blockX = MathHelper.floor(x);
        int blockZ = MathHelper.floor(z);
        int baseY = MathHelper.floor(y + 0.2);
        BlockPos.Mutable mutablePos = new BlockPos.Mutable(blockX, baseY + 1, blockZ);
        for (int blockY = baseY + 1; blockY >= baseY - 4; --blockY) {
            mutablePos.set(blockX, blockY, blockZ);
            BlockState state = this.getEntityWorld().getBlockState(mutablePos);
            VoxelShape shape = state.getCollisionShape((BlockView) this.getEntityWorld(), mutablePos);
            if (shape.isEmpty()) {
                continue;
            }
            double groundY = (double) blockY + shape.getMax(Direction.Axis.Y);
            double drop = y - groundY;
            if (!(drop >= -0.08) || !(drop <= 0.72)) {
                continue;
            }
            this.setPosition(x, groundY, z);
            this.lastX = x;
            this.lastY = groundY;
            this.lastZ = z;
            this.verticalVelocity = 0.0;
            this.fallDistance = 0.0f;
            this.setVelocity(Vec3d.ZERO);
            return;
        }
    }

    private PlayState predicate(AnimationTest<CustomPetEntity> state) {
        RawAnimation animation;
        AnimationController<CustomPetEntity> controller = state.controller();
        controller.setAnimationSpeed(this.currentAnimationSpeed);
        if (this.airborneMode) {
            animation = this.moving ? AIR_WALK : AIR_IDLE;
        } else if (this.usingUmbrella) {
            animation = this.moving ? RAIN_WALK : RAIN_IDLE;
        } else {
            animation = this.moving ? WALK : IDLE;
        }
        controller.setAnimation(animation);
        return PlayState.CONTINUE;
    }

    private void tickClientAudio(boolean movingNow, double horizontalDeltaSqr) {
        if (this.getEntityWorld() == null) {
            return;
        }
        if (this.ambientSoundCooldown > 0) {
            --this.ambientSoundCooldown;
        }
        if (this.stepSoundCooldown > 0) {
            --this.stepSoundCooldown;
        }
        if (movingNow && horizontalDeltaSqr > 4.0E-4 && this.isOnGround() && this.stepSoundCooldown <= 0) {
            this.playSound(STEP_SOUND, 0.55f, 0.94f + this.random.nextFloat() * 0.12f);
            this.stepSoundCooldown = 8 + this.random.nextInt(4);
        }
        if (!movingNow && this.ambientSoundCooldown <= 0) {
            this.playSound(AMBIENT_SOUND, 0.75f, 0.94f + this.random.nextFloat() * 0.14f);
            this.ambientSoundCooldown = 120 + this.random.nextInt(100);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllerRegistrar) {
        controllerRegistrar.add(new AnimationController<>("controller", 5, this::predicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    public float getLerpedYaw(float partialTick) {
        return MathHelper.lerpAngleDegrees(partialTick, this.lastYaw, this.getYaw());
    }

    private double square(double value) {
        return value * value;
    }
}
