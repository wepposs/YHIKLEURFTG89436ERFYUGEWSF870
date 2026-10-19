package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;

/**
 * ElytraTarget - точный предикт для боя на элитрах
 * Чистая реализация из MeowDLC
 */
@IModule(
        name = "ElytraTarget",
        description = "Предсказывает позицию цели на элитрах",
        category = Category.COMBAT,
        bind = 0
)
@Environment(EnvType.CLIENT)
public class ElytraTarget extends Module {
    public static ElytraTarget INSTANCE;

    // Constants from MeowDLC
    private static final double OVERTAKE_DOT_MIN = -0.4D;
    private static final double OVERTAKE_SPEED_SCALE = 1.25D;
    private static final double GLIDE_DRAG = 2.7D;
    private static final double AIM_HEIGHT_FRAC = 0.4D;
    private static final double PITCH_HORIZONTAL = 0.35D;
    private static final double TICK_VERTICAL = 0.65D;
    private static final double VERTICAL_CLAMP_LOW = -0.6D;
    private static final double VERTICAL_CLAMP_HIGH = 0.6D;

    public final SliderSetting pursuitDistance = new SliderSetting(
            "Дистанция наводки",
            20.0F,
            10.0F,
            100.0F,
            5.0F,
            false
    );

    public final SliderSetting predictBlocks = new SliderSetting(
            "Предикт (блоки)",
            3.0F,
            0.5F,
            5.0F,
            0.1F,
            false
    );

    // Compatibility
    public SliderSetting aimDistance = pursuitDistance;
    public BooleanSetting overtake = new BooleanSetting("Перегон", true);
    public LivingEntity currentTarget = null;

    public ElytraTarget() {
        INSTANCE = this;
        addSettings(new Setting[]{
                pursuitDistance,
                predictBlocks
        });
    }

    @EventInit
    public void onTick(ClientTickEvent event) {
        if (mc.player == null) {
            return;
        }
        currentTarget = AttackAura.target;
    }

    /**
     * Get predicted point (MeowDLC logic)
     */
    public Vec3 getPredictedPoint(LivingEntity target) {
        if (target == null || mc.player == null) {
            return getCenterPoint(target);
        }

        if (!enable || !target.isGliding()) {
            return getCenterPoint(target);
        }

        // Alignment check
        if (!passesOvertakeAlignment(target)) {
            return getCenterPoint(target);
        }

        return calculatePrediction(target);
    }

    /**
     * Calculate prediction with physics (MeowDLC baseOffset)
     */
    private Vec3 calculatePrediction(LivingEntity target) {
        Vec3 base = getCenterPoint(target);
        double blocks = predictBlocks.get().doubleValue();

        if (blocks <= 0.0) {
            return base;
        }

        Vec3 center = target.getBoundingBox().getCenter();
        double centerYOffset = center.y - target.getY();
        double tickVertical = target.getY() - target.lastRenderY;
        Vec3 velocity = target.getDeltaMovement();
        double horizontal = Math.hypot(velocity.x, velocity.z);

        if (horizontal < 1.0E-4) {
            horizontal = Math.hypot(target.getX() - target.lastRenderX, target.getZ() - target.lastRenderZ);
        }

        if (horizontal < 1.0E-4) {
            return base;
        }

        // Convert blocks to ticks
        double ticks = blocks / Math.max(horizontal, 0.05);
        
        // Apply glide drag
        double drag = Math.pow(1.0 / GLIDE_DRAG, ticks);
        
        // Extrapolate position
        Vec3 extrapolated = velocity.multiply(ticks * drag * OVERTAKE_SPEED_SCALE);

        // Vertical prediction based on pitch
        double pitchRad = Math.toRadians(target.getXRot());
        double pitchLead = -Math.sin(pitchRad) * horizontal * PITCH_HORIZONTAL;
        double verticalStep = (tickVertical * TICK_VERTICAL + pitchLead) * blocks;
        verticalStep = Mth.clamp(verticalStep, VERTICAL_CLAMP_LOW, VERTICAL_CLAMP_HIGH);

        return base.add(extrapolated.x, extrapolated.y + centerYOffset + verticalStep, extrapolated.z);
    }

    /**
     * Check alignment (MeowDLC passesOvertakeAlignment)
     */
    private boolean passesOvertakeAlignment(LivingEntity target) {
        if (mc.player == null) {
            return false;
        }

        Vec3 toTarget = new Vec3(
                target.getX() - mc.player.getX(),
                target.getY() - mc.player.getY(),
                target.getZ() - mc.player.getZ()
        );
        Vec3 flatToTarget = new Vec3(toTarget.x, 0.0, toTarget.z);

        if (flatToTarget.lengthSqr() < 1.0E-6) {
            return true;
        }

        Vec3 own = mc.player.getDeltaMovement();
        Vec3 flatOwn = new Vec3(own.x, 0.0, own.z).normalize();
        Vec3 flatTarget = new Vec3(target.getDeltaMovement().x, 0.0, target.getDeltaMovement().z).normalize();

        double closing = flatOwn.dotProduct(flatTarget);
        double approach = flatOwn.dotProduct(flatToTarget.normalize());

        return closing >= OVERTAKE_DOT_MIN && approach >= OVERTAKE_DOT_MIN;
    }

    private Vec3 getCenterPoint(LivingEntity entity) {
        if (entity == null) {
            return Vec3.ZERO;
        }
        return new Vec3(
                entity.getX(),
                entity.getY() + entity.getHeight() * AIM_HEIGHT_FRAC,
                entity.getZ()
        );
    }

    public boolean isElytraTarget(Entity entity) {
        if (!enable) {
            return false;
        }

        if (!(entity instanceof LivingEntity living)) {
            return false;
        }

        if (!living.isGliding()) {
            return false;
        }

        if (mc.player == null) {
            return false;
        }

        double distance = mc.player.distanceTo(living);
        return distance <= aimRadius();
    }

    public boolean canPredict(LivingEntity target) {
        if (!enable) {
            return false;
        }

        if (target == null || mc.player == null) {
            return false;
        }

        return mc.player.isGliding() && target.isGliding();
    }

    public double aimRadius() {
        return pursuitDistance.get().doubleValue();
    }

    public boolean isOvertakeEnabled() {
        return enable && overtake.get();
    }

    @Override
    public void onEnable() {
        currentTarget = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        currentTarget = null;
        super.onDisable();
    }
}
