package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.ElytraTarget;

/**
 * ElytraMotion — удержание у цели во время полёта на элитре (порт Meow ElytraMotion).
 * Гасит горизонтальную скорость вблизи цели, чтобы игрок не проскакивал и не
 * нарезал круги вокруг противника.
 */
@IModule(
        name = "ElytraMotion",
        description = "Удержание у цели на элитре (торможение вместо проскока)",
        category = Category.MOVEMENT,
        bind = 0
)
@Environment(EnvType.CLIENT)
public class ElytraMotion extends Module {

    /** Включает подтормаживание у цели. */
    public final BooleanSetting hold = new BooleanSetting("Удержание", true);

    /** Дистанция, ближе которой начинаем гасить скорость (блока). */
    public final SliderSetting holdDistance = new SliderSetting(
            "Дистанция удержания",
            3.0F,
            1.0F,
            8.0F,
            0.5F,
            false
    );

    /** Доля гашения горизонтальной скорости (0..1). */
    public final SliderSetting brakeStrength = new SliderSetting(
            "Сила торможения",
            0.85F,
            0.1F,
            1.0F,
            0.05F,
            false
    );

    /** Гасить только когда игрок сам не жмёт клавиши движения. */
    public final BooleanSetting onlyWhenIdle = new BooleanSetting("Только без ввода", false);

    /** Полный стоп, когда цель прямо по курсу и близко. */
    public final BooleanSetting freezeOnAlign = new BooleanSetting("Стоп при наведении", false);

    public ElytraMotion() {
        addSettings(new Setting[]{
                hold,
                holdDistance,
                brakeStrength,
                onlyWhenIdle,
                freezeOnAlign
        });
    }

    private boolean active() {
        return this.enable
                && mc != null
                && mc.player != null
                && mc.world != null
                && mc.player.isGliding();
    }

    private LivingEntity target() {
        ElytraTarget elytra = ElytraTarget.INSTANCE;
        if (elytra == null || !elytra.enable) {
            return null;
        }
        // ElytraTarget no longer has ownTarget(), use currentTarget from findTarget
        LivingEntity own = elytra.currentTarget;
        if (own != null) {
            return own;
        }
        return AttackAura.target;
    }

    @EventInit
    public void onTick(ClientTickEvent event) {
        if (!active() || !hold.get()) {
            return;
        }

        LivingEntity target = target();
        if (target == null || !target.isAlive()) {
            return;
        }

        if (onlyWhenIdle.get() && isPlayerInputting()) {
            return;
        }

        Vec3d velocity = mc.player.getVelocity();
        double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
        if (horizontalSpeed < 1.0E-3D) {
            return;
        }

        double distance = Math.hypot(
                target.getX() - mc.player.getX(),
                target.getZ() - mc.player.getZ()
        );

        double limit = holdDistance.get();
        if (distance > limit) {
            return;
        }

        double closeness = 1.0D - clamp(distance / Math.max(1.0E-3D, limit), 0.0D, 1.0D);
        double strength = brakeStrength.get() * closeness;

        if (freezeOnAlign.get() && alignedWithTarget(velocity, target)) {
            strength = brakeStrength.get();
        }

        strength = clamp(strength, 0.0D, 1.0D);
        double keep = 1.0D - strength;
        mc.player.setVelocity(velocity.x * keep, velocity.y, velocity.z * keep);
    }

    private boolean isPlayerInputting() {
        if (mc.player == null || mc.player.input == null) return false;
        net.minecraft.util.math.Vec2f move = mc.player.input.getMovementInput();
        return move.x != 0.0F || move.y != 0.0F;
    }

    private boolean alignedWithTarget(Vec3d velocity, LivingEntity target) {
        if (velocity.lengthSquared() < 1.0E-8D) return true;
        Vec3d toTarget = new Vec3d(
                target.getX() - mc.player.getX(),
                0.0D,
                target.getZ() - mc.player.getZ()
        );
        if (toTarget.lengthSquared() < 1.0E-8D) return true;
        return velocity.normalize().dotProduct(toTarget.normalize()) > 0.95D;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
}