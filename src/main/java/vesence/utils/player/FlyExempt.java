package vesence.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import vesence.utils.network.NetworkUtils;

/*
 * Periodically reports the player as flying via UpdatePlayerAbilitiesC2SPacket.
 * A vanilla server ignores this packet unless the player is in creative/spectator,
 * so no real flight is granted — but anticheats that trust the client-reported
 * flying status apply the same lenient movement checks as with server-side fly.
 */
public final class FlyExempt {

    private static final int INTERVAL_TICKS = 10;

    private static int tickCounter;

    private FlyExempt() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (++tickCounter >= INTERVAL_TICKS) {
            tickCounter = 0;
            send(mc);
        }
    }

    public static void sendNow() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            send(mc);
        }
    }

    private static void send(Minecraft mc) {
        Abilities abilities = new Abilities();
        abilities.invulnerable = mc.player.getAbilities().invulnerable;
        abilities.flying = true;
        abilities.mayfly = mc.player.getAbilities().mayfly;
        abilities.instabuild = mc.player.getAbilities().instabuild;
        NetworkUtils.sendSilentPacket(new ServerboundPlayerAbilitiesPacket(abilities));
    }
}
