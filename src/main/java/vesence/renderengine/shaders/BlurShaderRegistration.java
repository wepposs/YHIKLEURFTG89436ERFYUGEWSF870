package vesence.renderengine.shaders;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.renderengine.providers.ShaderProgram;

@Environment(EnvType.CLIENT)
public final class BlurShaderRegistration {
    private static ShaderProgram maskProgram;
    private static ShaderProgram gaussianProgram;
    private static ShaderProgram copyProgram;
    private static boolean initialized;

    private BlurShaderRegistration() {
    }

    public static void init() {
        ensureInitialized();
    }

    public static void ensureInitialized() {
        if (initialized) {
            return;
        }
        maskProgram = ShaderProgram.fromResources(
                "assets/vesence/shaders/core/blur.vsh",
                "assets/vesence/shaders/core/blur.fsh"
        );
        gaussianProgram = ShaderProgram.fromResources(
                "assets/vesence/shaders/core/blur.vsh",
                "assets/vesence/shaders/core/blur_gaussian.fsh"
        );
        copyProgram = ShaderProgram.fromResources(
                "assets/vesence/shaders/core/blur.vsh",
                "assets/vesence/shaders/core/blur_copy.fsh"
        );
        initialized = true;
    }

    public static ShaderProgram getProgram() {
        return getMaskProgram();
    }

    public static ShaderProgram getMaskProgram() {
        ensureInitialized();
        return maskProgram;
    }

    public static ShaderProgram getGaussianProgram() {
        ensureInitialized();
        return gaussianProgram;
    }

    public static ShaderProgram getCopyProgram() {
        ensureInitialized();
        return copyProgram;
    }

    public static int getId() {
        ShaderProgram program = getMaskProgram();
        return program != null ? program.id() : -1;
    }

    public static void reset() {
        if (maskProgram != null) {
            try { maskProgram.delete(); } catch (Exception ignored) {}
            maskProgram = null;
        }
        if (gaussianProgram != null) {
            try { gaussianProgram.delete(); } catch (Exception ignored) {}
            gaussianProgram = null;
        }
        if (copyProgram != null) {
            try { copyProgram.delete(); } catch (Exception ignored) {}
            copyProgram = null;
        }
        initialized = false;
    }

    public static boolean isReady() {
        ensureInitialized();
        return maskProgram != null && gaussianProgram != null && copyProgram != null;
    }
}
