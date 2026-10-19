package vesence.utils.render;

import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;

public class ResourceManager {

    private static final Map<String, Integer> textureCache = new HashMap<>();
    private static final String DEFAULT_NAMESPACE = "vesence";

    private final String path;
    private final String namespace;
    private final Identifier identifier;
    private int cachedGlId = -1;

    public ResourceManager(String path) {
        this(DEFAULT_NAMESPACE, normalizePath(path));
    }

    private static String normalizePath(String path) {
        if (path == null) return "";
        if (path.startsWith("vesence/")) {
            return path.substring("vesence/".length());
        }
        if (path.startsWith("minecraft/")) {
            return path.substring("minecraft/".length());
        }
        return path;
    }

    public ResourceManager(String namespace, String path) {
        this.namespace = namespace;
        this.path = path;
        this.identifier = Identifier.of(namespace, path);
    }

    public int getGlId() {
        if (cachedGlId > 0) {
            if (GL11.glIsTexture(cachedGlId)) return cachedGlId;
            cachedGlId = -1;
        }

        String cacheKey = namespace + ":" + path;
        Integer cached = textureCache.get(cacheKey);
        if (cached != null && cached > 0) {
            if (GL11.glIsTexture(cached)) {
                cachedGlId = cached;
                return cached;
            }
            textureCache.remove(cacheKey);
        }

        int glId = loadTexture();
        if (glId > 0) {
            cachedGlId = glId;
            textureCache.put(cacheKey, glId);
        }

        return glId;
    }

    private int loadTexture() {
        try {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null) {
                return 0;
            }

            AbstractTexture texture = mc.getTextureManager().getTexture(identifier);
            if (texture == null) {
                return 0;
            }

            GpuTextureView view = texture.getGlTextureView();
            if (view == null) {
                return 0;
            }

            GpuTexture gpuTexture = view.texture();
            if (gpuTexture instanceof GlTexture glTexture) {
                return glTexture.getGlId();
            }
        } catch (Exception e) {
            System.err.println("Failed to load texture: " + namespace + ":" + path);
            e.printStackTrace();
        }

        return 0;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public String getPath() {
        return path;
    }

    public String getNamespace() {
        return namespace;
    }

    public boolean isLoaded() {
        return getGlId() > 0;
    }

    public void invalidateCache() {
        cachedGlId = -1;
        String cacheKey = namespace + ":" + path;
        textureCache.remove(cacheKey);
    }

    public static void clearCache() {
        textureCache.clear();
    }

    public static int getCacheSize() {
        return textureCache.size();
    }

    @Override
    public String toString() {
        return "ResourceManager{" +
                "namespace='" + namespace + '\'' +
                ", path='" + path + '\'' +
                ", loaded=" + isLoaded() +
                '}';
    }
}
