package vesence.utils.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;

public class NetworkUtils {
    private static final ThreadLocal<Boolean> SENDING = ThreadLocal.withInitial(() -> false);

    public static void sendSilentPacket(Packet<?> packet) {
        boolean prev = SENDING.get();
        SENDING.set(true);
        try {
            MinecraftClient.getInstance().getNetworkHandler().sendPacket(packet);
        } finally {
            SENDING.set(prev);
        }
    }

    public static boolean isSending() {
        return SENDING.get();
    }
}
