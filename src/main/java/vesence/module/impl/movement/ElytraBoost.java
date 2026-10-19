package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.player.EventElytraBoost;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.ElytraTarget;

@IModule(
        name = "ElytraBoost",
        description = "Ускорение фейерверков на элитрах",
        category = Category.MOVEMENT,
        bind = 0
)
@Environment(EnvType.CLIENT)
public class ElytraBoost extends Module {
    public static ElytraBoost INSTANCE;

    private static final String[] RANGE_LABELS = new String[]{
            "0-5", "5-10", "10-15", "15-20", "20-25", "25-30", "30-35", "35-40", "40-45"
    };

    public final ModeSetting mode = new ModeSetting(
            "Режим",
            "Обычный",
            "Обычный", "Кастомный"
    );

    public final BooleanSetting adaptToTarget = new BooleanSetting(
            "Адаптация к цели",
            true
    );

    private final SliderSetting[] yawSpeeds = new SliderSetting[9];
    private final SliderSetting[] pitchSpeeds = new SliderSetting[9];

    public ElytraBoost() {
        INSTANCE = this;

        for (int i = 0; i < yawSpeeds.length; i++) {
            yawSpeeds[i] = new SliderSetting(
                    "Yaw " + RANGE_LABELS[i],
                    1.5F,
                    1.5F,
                    2.5F,
                    0.01F,
                    false
            ).hidden(() -> !mode.is("Кастомный"));
        }

        for (int i = 0; i < pitchSpeeds.length; i++) {
            pitchSpeeds[i] = new SliderSetting(
                    "Pitch " + RANGE_LABELS[i],
                    1.5F,
                    1.5F,
                    2.5F,
                    0.01F,
                    false
            ).hidden(() -> !mode.is("Кастомный"));
        }

        addSettings(new Setting[]{mode, adaptToTarget});
        for (SliderSetting s : yawSpeeds) addSettings(new Setting[]{s});
        for (SliderSetting s : pitchSpeeds) addSettings(new Setting[]{s});
    }

    @EventInit
    public void onElytraBoost(EventElytraBoost event) {
        if (!enable || mc.player == null || !mc.player.isGliding()) {
            return;
        }

        Vec3d boost = event.getBoost();
        
        // Apply speed multiplier based on rotation
        Vec2f multiplier = getBoostMultiplier();
        Vec3d modified = new Vec3d(
                boost.x * multiplier.x,
                boost.y * multiplier.y,
                boost.z * multiplier.x
        );

        // Adapt to target if enabled
        if (adaptToTarget.get()) {
            modified = adaptBoost(modified);
        }

        event.setBoost(modified);
    }

    private Vec2f getBoostMultiplier() {
        float yaw = mc.player.getYaw();
        float pitch = mc.player.getPitch();

        // Use target rotations if available (handled by AttackAura)

        float normalizedYaw = convertValToRange(MathHelper.wrapDegrees(yaw));
        float normalizedPitch = convertValToRange(Math.abs(pitch));

        int yawIndex = getRangeIndex(normalizedYaw, yawSpeeds.length);
        int pitchIndex = getRangeIndex(normalizedPitch, pitchSpeeds.length);

        float yawSpeed = yawSpeeds[yawIndex].get().floatValue();
        float pitchSpeed = pitchSpeeds[pitchIndex].get().floatValue();

        if (pitchSpeed > yawSpeed) {
            yawSpeed = pitchSpeed;
        }

        // Add jitter
        float jitter = (float) (Math.random() * 0.002 - 0.001);
        yawSpeed += jitter;
        pitchSpeed += jitter * 0.7F;

        return new Vec2f(yawSpeed, pitchSpeed);
    }

    private Vec3d adaptBoost(Vec3d boost) {
        if (mc.player == null || !mc.player.isGliding()) {
            return boost;
        }

        ElytraTarget elytraTarget = ElytraTarget.INSTANCE;
        if (elytraTarget == null || !elytraTarget.enable) {
            return boost;
        }

        LivingEntity target = AttackAura.target;
        if (target == null || !elytraTarget.isElytraTarget(target)) {
            return boost;
        }

        double targetBps = getBps(target);
        double playerBps = getBps(mc.player);
        double distance = mc.player.getEyePos().distanceTo(target.getEyePos());

        double desiredDistance = Math.max(elytraTarget.predictBlocks.get().doubleValue(),
                target.getWidth() + mc.player.getWidth());
        double distanceError = (distance - desiredDistance) / Math.max(desiredDistance, 1.0);

        double predictedTargetEyeY = target.getEyeY();
        double heightError = predictedTargetEyeY - mc.player.getEyeY();
        double relativeY = target.getVelocity().y - mc.player.getVelocity().y;
        double fallHold = Math.max(0.0, -mc.player.getVelocity().y);

        double bpsFactor = targetBps / Math.max(playerBps, 0.05);
        double distanceFactor = 1.0 + MathHelper.clamp(distanceError, -1.0, 1.0);
        double horizontalFactor = MathHelper.clamp(bpsFactor * distanceFactor, 0.35, 1.08);
        double verticalFactor = MathHelper.clamp(
                1.0 + (heightError * 0.72 + relativeY * 1.25 + fallHold * 2.4) / Math.max(distance, 1.0),
                0.62, 1.14
        );

        double baseHorizontal = Math.max(boost.x, boost.z);
        double horizontal = baseHorizontal * horizontalFactor;
        double vertical = boost.y * verticalFactor;

        return new Vec3d(horizontal, vertical, horizontal);
    }

    private double getBps(LivingEntity entity) {
        double dx = entity.getX() - entity.lastRenderX;
        double dy = entity.getY() - entity.lastRenderY;
        double dz = entity.getZ() - entity.lastRenderZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) * 20.0;
    }

    private int getRangeIndex(float value, int length) {
        return Math.min((int) (value / 5.0F), length - 1);
    }

    private float convertValToRange(float value) {
        float result = Math.abs(value);
        if (result > 90.0F) {
            result = 180.0F - result;
        }
        if (result > 45.0F) {
            result = 90.0F - result;
        }
        return result;
    }
}
