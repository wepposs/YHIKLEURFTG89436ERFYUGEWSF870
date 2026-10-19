package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.friends.FriendStorage;

import java.util.Random;

@IModule(
        name = "HitBox",
        description = "Увеличивает хитбокс таргета",
        category = Category.COMBAT,
        bind = -1
)
@Environment(EnvType.CLIENT)
public class HitBox extends Module {

    public final ModeSetting mode = new ModeSetting(
            "Режим",
            "Обычный",
            "Легит",
            "Обычный"
    );

    public final SliderSetting size = new SliderSetting(
            "Размер",
            0.2,
            0.0,
            5.0,
            0.1
    );

    public final BooleanSetting ignoreFriends = new BooleanSetting(
            "Игнор друзей",
            true
    );

    public final ModeSetting snapMode = new ModeSetting(
            "Режим снапа",
            "Fast",
            "Fast",
            "Smooth",
            "Random"
    );

    public final BooleanSetting visual = new BooleanSetting(
            "Визуал",
            true
    );

    private final Random random = new Random();

    private LivingEntity target;
    private int summary;

    private float targetYaw;
    private float targetPitch;

    private static final int MAX_LEGIT_TICKS = 6;
    private static final long ATTACK_DELAY = 80L;

    private long lastAttackTime;
    private boolean hitboxApplied;

    public HitBox() {
        addSettings(new Setting[]{
                mode,
                size,
                ignoreFriends,
                snapMode,
                visual
        });
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable) {
            restoreHitBoxes();
            resetTarget();
            return;
        }

        if (mc.player == null || mc.level == null) {
            restoreHitBoxes();
            resetTarget();
            return;
        }

        if (mode.is("Обычный")) {
            resetTarget();

            if (visual.get()) {
                applyHitBoxes();
            } else {
                restoreHitBoxes();
            }

            return;
        }

        handleLegit();
    }

    private void applyHitBoxes() {
        double expand = Math.max(0.0D, size.get() * 2.5D);

        for (Player player : mc.level.getPlayers()) {
            if (isInvalid(player)) {
                continue;
            }

            AABB original = player.getBoundingBox();

            double minX = player.getX() - 0.30D - expand;
            double minY = original.minY;
            double minZ = player.getZ() - 0.30D - expand;

            double maxX = player.getX() + 0.30D + expand;
            double maxY = original.maxY;
            double maxZ = player.getZ() + 0.30D + expand;

            player.setBoundingBox(new AABB(
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ
            ));
        }

        hitboxApplied = true;
    }

    private boolean isInvalid(Player player) {
        if (player == null) {
            return true;
        }

        if (player == mc.player) {
            return true;
        }

        if (!player.isAlive()) {
            return true;
        }

        return ignoreFriends.get()
                && FriendStorage.isFriend(player.getName().getString());
    }

    private void restoreHitBoxes() {
        if (!hitboxApplied || mc.level == null) {
            return;
        }

        for (Player player : mc.level.getPlayers()) {
            if (player == mc.player || !player.isAlive()) {
                continue;
            }

            AABB current = player.getBoundingBox();

            double halfWidth = 0.30D;

            double minX = player.getX() - halfWidth;
            double minZ = player.getZ() - halfWidth;
            double maxX = player.getX() + halfWidth;
            double maxZ = player.getZ() + halfWidth;

            player.setBoundingBox(new AABB(
                    minX,
                    current.minY,
                    minZ,
                    maxX,
                    current.maxY,
                    maxZ
            ));
        }

        hitboxApplied = false;
    }

    private void handleLegit() {
        LivingEntity selected = refresh();

        if (selected == null) {
            resetTarget();
            return;
        }

        if (target != selected) {
            target = selected;
            summary = 0;
        }

        Rotation rotation = calculateRotation(target);

        targetYaw = rotation.yaw;
        targetPitch = rotation.pitch;

        applyLegitRotation();

        summary++;

        if (summary >= 2 && canAttack(target)) {
            mc.gameMode.attackEntity(
                    mc.player,
                    target
            );

            mc.player.swingHand(InteractionHand.MAIN_HAND);

            lastAttackTime = System.currentTimeMillis();

            resetTarget();
        } else if (summary >= MAX_LEGIT_TICKS) {
            resetTarget();
        }
    }

    private LivingEntity refresh() {
        Vec3 eye = mc.player.getEyePosition();

        Vec3 look = mc.player
                .getRotationVec(1.0F)
                .normalize();

        LivingEntity result = null;
        double bestAngle = Double.MAX_VALUE;

        double fov = size.get() * 30.0D;
        fov = Math.max(fov, 1.0D);
        fov = Math.min(fov, 180.0D);

        double range = 3.0D;

        for (Entity entity : mc.level.getEntities()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }

            if (living == mc.player) {
                continue;
            }

            if (!living.isAlive()) {
                continue;
            }

            if (
                    ignoreFriends.get()
                            && living instanceof Player player
                            && FriendStorage.isFriend(
                            player.getName().getString()
                    )
            ) {
                continue;
            }

            if (mc.player.distanceTo(living) > range) {
                continue;
            }

            Vec3 point = getEntityCenter(living);

            Vec3 direction = point.subtract(eye);

            if (direction.lengthSqr() <= 1.0E-7D) {
                continue;
            }

            direction = direction.normalize();

            double dot = Mth.clamp(
                    look.dotProduct(direction),
                    -1.0D,
                    1.0D
            );

            double angle = Math.toDegrees(
                    Math.acos(dot)
            );

            if (angle <= fov && angle < bestAngle) {
                bestAngle = angle;
                result = living;
            }
        }

        return result;
    }

    private Rotation calculateRotation(LivingEntity entity) {
        Vec3 eye = mc.player.getEyePosition();

        Vec3 point = getEntityCenter(entity);

        if (size.get() > 0.0D) {
            AABB box = entity.getBoundingBox().expand(size.get());
            point = getClosestPoint(box, eye);
        }

        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;

        double horizontal = Math.sqrt(
                dx * dx + dz * dz
        );

        float yaw = (float) Math.toDegrees(
                Math.atan2(dz, dx)
        ) - 90.0F;

        float pitch = (float) -Math.toDegrees(
                Math.atan2(dy, horizontal)
        );

        return new Rotation(
                Mth.wrapDegrees(yaw),
                Mth.clamp(
                        pitch,
                        -90.0F,
                        90.0F
                )
        );
    }

    private Vec3 getEntityCenter(LivingEntity entity) {
        return new Vec3(
                entity.getX(),
                entity.getY() + entity.getHeight() * 0.5D,
                entity.getZ()
        );
    }

    private Vec3 getClosestPoint(AABB box, Vec3 point) {
        double x = Mth.clamp(
                point.x,
                box.minX,
                box.maxX
        );

        double y = Mth.clamp(
                point.y,
                box.minY,
                box.maxY
        );

        double z = Mth.clamp(
                point.z,
                box.minZ,
                box.maxZ
        );

        return new Vec3(
                x,
                y,
                z
        );
    }

    private void applyLegitRotation() {
        float currentYaw = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        float yawDelta = Mth.wrapDegrees(
                targetYaw - currentYaw
        );

        float pitchDelta = targetPitch - currentPitch;

        switch (snapMode.get()) {
            case "Fast" -> {
                float yawStep = Math.min(
                        Math.abs(yawDelta),
                        35.0F
                );

                float pitchStep = Math.min(
                        Math.abs(pitchDelta),
                        20.0F
                );

                mc.player.setYRot(
                        currentYaw
                                + Math.signum(yawDelta)
                                * yawStep
                );

                mc.player.setXRot(
                        Mth.clamp(
                                currentPitch
                                        + Math.signum(pitchDelta)
                                        * pitchStep,
                                -90.0F,
                                90.0F
                        )
                );
            }

            case "Smooth" -> {
                float yawStep = yawDelta * 0.45F;
                float pitchStep = pitchDelta * 0.45F;

                mc.player.setYRot(
                        currentYaw + yawStep
                );

                mc.player.setXRot(
                        Mth.clamp(
                                currentPitch + pitchStep,
                                -90.0F,
                                90.0F
                        )
                );
            }

            case "Random" -> {
                float factor =
                        0.30F
                                + random.nextFloat() * 0.30F;

                float yawStep =
                        yawDelta * factor;

                float pitchStep =
                        pitchDelta * factor;

                yawStep +=
                        (random.nextFloat() - 0.5F)
                                * 1.5F;

                pitchStep +=
                        (random.nextFloat() - 0.5F)
                                * 1.0F;

                mc.player.setYRot(
                        currentYaw + yawStep
                );

                mc.player.setXRot(
                        Mth.clamp(
                                currentPitch + pitchStep,
                                -90.0F,
                                90.0F
                        )
                );
            }

            default -> {
                mc.player.setYRot(
                        currentYaw + yawDelta
                );

                mc.player.setXRot(
                        Mth.clamp(
                                currentPitch + pitchDelta,
                                -90.0F,
                                90.0F
                        )
                );
            }
        }
    }

    private boolean canAttack(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) {
            return false;
        }

        if (mc.player.distanceTo(entity) > 3.0F) {
            return false;
        }

        long now = System.currentTimeMillis();

        if (now - lastAttackTime < ATTACK_DELAY) {
            return false;
        }

        Vec3 eye = mc.player.getEyePosition();

        Vec3 look = mc.player
                .getRotationVec(1.0F)
                .normalize();

        Vec3 point = getEntityCenter(entity);

        Vec3 direction = point.subtract(eye);

        if (direction.lengthSqr() <= 1.0E-7D) {
            return false;
        }

        direction = direction.normalize();

        double dot = Mth.clamp(
                look.dotProduct(direction),
                -1.0D,
                1.0D
        );

        double angle = Math.toDegrees(
                Math.acos(dot)
        );

        return angle <= 12.0D;
    }

    private void resetTarget() {
        target = null;
        summary = 0;
        targetYaw = 0.0F;
        targetPitch = 0.0F;
    }

    @Override
    public void onDisable() {
        restoreHitBoxes();
        resetTarget();
        super.onDisable();
    }

    private record Rotation(
            float yaw,
            float pitch
    ) {
    }
}