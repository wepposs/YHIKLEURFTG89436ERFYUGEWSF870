package vesence.hmi;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Точная детекция критического удара для анимации режима «Новый» в Living Hands.
 *
 * <p>Условие 1-в-1 повторяет ванильный крит из {@code Player.attack}: сильный
 * удар (прогресс кулдауна атаки &gt; 0.9), падение вниз, игрок не на земле, не на
 * лестнице, не в воде, без слепоты, без транспорта, без спринта, и цель — живое
 * существо.</p>
 *
 * <p><b>Важно по таймингу:</b> вызывать нужно в HEAD того места, где атака ещё не
 * применена (HEAD {@code attackEntity} / {@code doAttack}), т.к. {@code Player.attack}
 * сбрасывает кулдаун — после сброса {@code getAttackCooldownProgress} вернёт ~0.</p>
 */
@Environment(EnvType.CLIENT)
public final class CritDetector {

    private CritDetector() {
    }

    public static boolean isCriticalAttack(Player player, Entity target) {
        if (player == null) {
            return false;
        }
        return player.getAttackCooldownProgress(0.5f) > 0.9f
                && player.fallDistance > 0.0f
                && !player.onGround()
                && !player.isClimbing()
                && !player.isTouchingWater()
                && !player.hasStatusEffect(MobEffects.BLINDNESS)
                && !player.hasVehicle()
                && !player.isSprinting()
                && target instanceof LivingEntity;
    }
}
