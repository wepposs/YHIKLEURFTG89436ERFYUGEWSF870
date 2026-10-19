package vesence.module.impl.combat;

import net.fabricmc.api.*;
import net.minecraft.entity.*;
import net.minecraft.entity.decoration.*;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.*;
import net.minecraft.util.math.*;
import vesence.event.*;
import vesence.event.player.*;
import vesence.event.render.*;
import vesence.module.api.*;
import vesence.module.api.Module;
import vesence.module.api.setting.*;
import vesence.module.api.setting.impl.*;

@IModule(
    name = "AimAssist",
    description = "Плавно помогает доводить прицел до цели",
    category = Category.COMBAT,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class AimAssist extends Module {
    public static MultiBooleanSetting targets = new MultiBooleanSetting(
        "Цели",
        new BooleanSetting("Игроки", true),
        new BooleanSetting("Голые", true),
        new BooleanSetting("Мобы", false)
    );

    public static SliderSetting fov = new SliderSetting("FOV", 65.0, 5.0, 180.0, 1.0);
    public static BooleanSetting showFov = new BooleanSetting("Отображать Fov", true);
    public static SliderSetting speed = new SliderSetting("Скорость", 45.0, 1.0, 100.0, 1.0, true);
    public static SliderSetting smooth = new SliderSetting("Плавность", 60.0, 1.0, 95.0, 1.0, true);
    public static BooleanSetting onlyVisible = new BooleanSetting("Только видимые", true);
    public static BooleanSetting ignoreFriends = new BooleanSetting("Игнор друзей", true);
    public static BooleanSetting ignoreBots = new BooleanSetting("Игнор ботов", true);

    private LivingEntity target;
    private float assistYaw;
    private float assistPitch;

    public AimAssist() {
        this.addSettings(new Setting[]{
            targets, fov, showFov, speed, smooth, onlyVisible, ignoreFriends, ignoreBots
        });
    }

    @Override
    public void onEnable() {
        target = null;
        assistYaw = 0.0f;
        assistPitch = 0.0f;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        target = null;
        assistYaw = 0.0f;
        assistPitch = 0.0f;
        super.onDisable();
    }

    @EventInit
    public void onRender2D(EventScreen event) {
        if (!this.enable || !showFov.get() || mc.player == null || mc.level == null) {
            return;
        }

        float sw = event.viewportWidth() / 2.0f;
        float sh = event.viewportHeight() / 2.0f;

        float gameFov = mc.options.getFov().getValue().floatValue();
        float moduleFov = fov.get().floatValue();

        float radius = (float) (Math.tan(Math.toRadians(moduleFov / 2.0)) / Math.tan(Math.toRadians(gameFov / 2.0)) * sh);

        event.renderer().circleOutline(sw, sh, radius, 0.0f, 1.0f, 0x80FFFFFF, 1.0f);
    }

    @EventInit
    public void onLook(EventLook event) {
        if (!this.enable || mc.player == null || mc.level == null || mc.screen != null) {
            resetAssist();
            return;
        }

        target = selectTarget();
        if (target == null) {
            resetAssist();
            return;
        }

        Vec3 aimPoint = target.getBoundingBox().getCenter();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 diff = aimPoint.subtract(eye);

        double horizontal = Math.hypot(diff.x, diff.z);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontal)));

        float yawDelta = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float pitchDelta = Mth.clamp(targetPitch - mc.player.getXRot(), -90.0f, 90.0f);

        float baseSpeed = (float) speed.get().doubleValue() / 100.0f;
        float smoothFactor = 1.0f - (float) smooth.get().doubleValue() / 100.0f;

        float desiredYaw = Mth.clamp(yawDelta, -baseSpeed * 50.0f, baseSpeed * 50.0f);
        float desiredPitch = Mth.clamp(pitchDelta, -baseSpeed * 50.0f, baseSpeed * 50.0f);

        assistYaw += (desiredYaw - assistYaw) * smoothFactor;
        assistPitch += (desiredPitch - assistPitch) * smoothFactor;

        event.setYRot(event.getYRot() + assistYaw);
        event.setXRot(Mth.clamp(event.getXRot() + assistPitch, -90.0f, 90.0f));
    }

    private LivingEntity selectTarget() {
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : mc.level.getEntities()) {
            if (!(entity instanceof LivingEntity living) || !isValidTarget(living)) {
                continue;
            }

            Vec3 point = living.getBoundingBox().getCenter();
            float[] rotations = getRotationsTo(point);
            double angle = Math.hypot(rotations[0], rotations[1]);

            if (angle > fov.get() * 0.5) {
                continue;
            }

            double distance = mc.player.distanceTo(living);
            double score = angle + (distance * 2.0);

            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        return best;
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (entity == mc.player || !entity.isAlive() || entity.isInvulnerable() || entity instanceof ArmorStand) {
            return false;
        }

        if (mc.player.distanceTo(entity) > AttackAura.attackRange.get().doubleValue()) {
            return false;
        }

        if (onlyVisible.get() && !mc.player.canSee(entity)) {
            return false;
        }

        if (ignoreFriends.get() && NoFriendDamage.isFriend(entity)) {
            return false;
        }

        if (ignoreBots.get() && AntiBot.isBot(entity)) {
            return false;
        }

        boolean player = entity instanceof Player;
        if (player && !targets.get("Игроки")) {
            return false;
        }
        if (player && !targets.get("Голые") && entity.getArmor() == 0) {
            return false;
        }
        if (!player && !targets.get("Мобы")) {
            return false;
        }
        if (!player && !(entity instanceof Monster || entity instanceof SlimeEntity || entity instanceof VillagerEntity || entity instanceof AnimalEntity)) {
            return false;
        }
        if (entity instanceof Player playerEntity && playerEntity.isCreative()) {
            return false;
        }

        return true;
    }

    private float[] getRotationsTo(Vec3 point) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 diff = point.subtract(eye);
        double horizontal = Math.hypot(diff.x, diff.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(diff.y, horizontal)));
        return new float[]{
            Mth.wrapDegrees(yaw - mc.player.getYRot()),
            pitch - mc.player.getXRot()
        };
    }

    private void resetAssist() {
        target = null;
        assistYaw *= 0.5f;
        assistPitch *= 0.5f;
        if (Math.abs(assistYaw) < 0.01f) {
            assistYaw = 0.0f;
        }
        if (Math.abs(assistPitch) < 0.01f) {
            assistPitch = 0.0f;
        }
    }
}
