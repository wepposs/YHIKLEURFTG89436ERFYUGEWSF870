package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

import static vesence.utils.other.IMinecraft.mc;

/**
 * SpookyTiming - port of Energy Spooky (combat timing helper).
 *
 * Provides:
 *   - Sprint-stop optimization for critical hits
 *   - Cooldown prediction for the aura
 *   - Battle-timer detection (spookytime sidebar)
 *
 * This is NOT a rotation - it's a timing layer that AttackAura consults
 * before hitting, so the critical-hit window is not missed.
 */
@Environment(EnvType.CLIENT)
public class USpookyTimingRotation {

    private static final float COOLDOWN_MULTIPLIER = 0.9f;
    private static final double MAX_RANGE = 20.0;
    private static final String BATTLE_TIMER_PREFIX = "Время боя: ";
    private static final String OPPONENT_PREFIX = "Противник: ";

    private static long lastAttackTime = 0L;
    private static boolean shouldSprintStop = false;
    private static boolean sprinting = false;
    private static int lastTargetId = -1;

    /** True if the server sidebar shows an active spookytime battle. */
    public static boolean isSpookyTime() {
        if (mc.level == null) return false;
        var scoreboard = mc.level.getScoreboard();
        var objective = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (objective == null) return false;

        boolean hasTimer = false;
        boolean hasOpponent = false;
        for (var entry : scoreboard.listPlayerScores(objective)) {
            String text = entry.owner();
            if (text.contains(BATTLE_TIMER_PREFIX)) hasTimer = true;
            else if (text.contains(OPPONENT_PREFIX)) hasOpponent = true;
            if (hasTimer && hasOpponent) return true;
        }
        return false;
    }

    /** Called on each attack: record timing and trigger sprint-stop if needed. */
    public static void onAttack() {
        lastAttackTime = System.currentTimeMillis();
        sprinting = mc.player != null && mc.player.isSprinting();
        if (sprinting) {
            shouldSprintStop = true;
        }
    }

    /** Per-tick update, mirrors Energy Spooky#onTick. */
    public static void tick() {
        if (mc.player == null || mc.level == null) return;
        sprinting = isSpookyTime();
    }

    /**
     * Predict the attack-cooldown progress (0..1) at the given tick offset.
     */
    public static float predictCooldown(float offsetTicks) {
        if (mc.player == null) return 0.0f;
        float progress = mc.player.getAttackStrengthScale(offsetTicks);
        return Math.max(0.0f, Math.min(1.0f, progress * COOLDOWN_MULTIPLIER));
    }

    /** True when an attack can land now (cooldown reached the threshold). */
    public static boolean canAttackNow() {
        return mc.player != null && predictCooldown(0.0f) >= COOLDOWN_MULTIPLIER;
    }

    /**
     * Execute the sprint-stop: if the player was sprinting before the attack,
     * send a STOP_SPRINTING packet. Mirrors Energy Spooky#executeSprintStop.
     */
    public static void executeSprintStop() {
        if (!shouldSprintStop) return;
        shouldSprintStop = false;
        if (mc.player == null || mc.getConnection() == null) return;
        mc.getConnection().send(
                new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
        mc.player.setSprinting(true);
    }

    public static boolean shouldSprintStop() {
        return shouldSprintStop;
    }

    /** Reset all timing state. */
    public static void state() {
        lastAttackTime = 0L;
        shouldSprintStop = false;
        sprinting = false;
        lastTargetId = -1;
    }
}