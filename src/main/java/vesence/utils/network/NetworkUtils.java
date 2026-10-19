package vesence.utils.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;

public class NetworkUtils {
    private static final ThreadLocal<Boolean> SENDING = ThreadLocal.withInitial(() -> false);

    public static void sendSilentPacket(Packet<?> packet) {
        boolean prev = SENDING.get();
        SENDING.set(true);
        try {
            Minecraft.getInstance().getConnection().sendPacket(packet);
        } finally {
            SENDING.set(prev);
        }
    }

    public static boolean isSending() {
        return SENDING.get();
    }
}
