package vesence.utils.player;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
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
        PlayerAbilities abilities = new PlayerAbilities();
        abilities.invulnerable = mc.player.getAbilities().invulnerable;
        abilities.flying = true;
        abilities.allowFlying = mc.player.getAbilities().allowFlying;
        abilities.creativeMode = mc.player.getAbilities().creativeMode;
        NetworkUtils.sendSilentPacket(new UpdatePlayerAbilitiesC2SPacket(abilities));
    }
}
