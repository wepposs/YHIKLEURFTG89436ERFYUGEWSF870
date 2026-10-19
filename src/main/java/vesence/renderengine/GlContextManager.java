package vesence.renderengine;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import vesence.Vesence;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@Environment(EnvType.CLIENT)
public final class GlContextManager {
    private static int sentinelTexture = -1;
    private static boolean contextLost = false;

    private GlContextManager() {}

    public static void initialize() {
        sentinelTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sentinelTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        ByteBuffer pixel = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder());
        pixel.put(new byte[]{(byte)255, (byte)255, (byte)255, (byte)255});
        pixel.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 1, 1, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        contextLost = false;
    }

    public static boolean isContextValid() {
        if (contextLost) return false;
        if (sentinelTexture <= 0) return true;
        if (!GL11.glIsTexture(sentinelTexture)) {
            contextLost = true;
            return false;
        }
        return true;
    }

    public static void onRenderFrame() {
        if (!isContextValid()) {
            Vesence.invalidateGlContext();
            contextLost = false;
            sentinelTexture = -1;
        }
    }

    public static boolean needsReinitialize() {
        return sentinelTexture <= 0;
    }
}
