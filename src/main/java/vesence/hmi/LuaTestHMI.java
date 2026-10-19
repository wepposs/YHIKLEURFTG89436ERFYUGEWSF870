package vesence.hmi;

import vesence.hmi.resource_controller.LuaAnimationResourceLoader;
import vesence.hmi.resource_controller.LuaScriptCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceType;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Ядро движка "живых рук" (порт мода Hold My Items).
 *
 * В оригинале это был ModInitializer отдельного мода. В Vesence движок инициализируется
 * из {@link vesence.Vesence#onInitializeClient()} через {@link #init()} — отдельная точка
 * входа не нужна, ресурсы (Lua-скрипты, модели, текстуры частиц) встроены в ресурсы Vesence
 * под assets/minecraft/holdmyitems/.
 */
public class LuaTestHMI {
    public static final String MOD_ID = "holdmyitems";
    public static int swingSpeed = 9;
    public static final Logger LOGGER = LoggerFactory.getLogger("holdmyitems");
    public static MinecraftClient client = MinecraftClient.getInstance();
    public static int age = 0;
    public static int prevAge = 0;
    private static float prevTime = 0.0f;
    public static float deltaTime = 0.0f;
    public static LuaScriptCache handScriptCache;
    public static LuaScriptCache handRelativeScriptCache;
    public static LuaScriptCache itemScriptCache;
    public static HashMap<String, Boolean> renderAsBlock = new HashMap<>();
    public static HashMap<String, Boolean> translateItem = new HashMap<>();
    public static ArrayList<LuaScriptCache> handAddonsCache = new ArrayList<>();
    public static ArrayList<LuaScriptCache> handRelativeAddonsCache = new ArrayList<>();
    public static ArrayList<LuaScriptCache> itemAddonsCache = new ArrayList<>();

    private static boolean initialized = false;

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES)
                .registerReloadListener((IdentifiableResourceReloadListener) new LuaAnimationResourceLoader());
        try {
            handScriptCache = new LuaScriptCache("return");
            handRelativeScriptCache = new LuaScriptCache("return");
            itemScriptCache = new LuaScriptCache("return");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        LOGGER.info("[weppos] Living Hands (HMI) engine initialized");
    }

    /**
     * Обновление deltaTime, вызывается каждый кадр из конвейера рендера Vesence
     * (замена WorldRenderEvents.START из оригинального мода).
     */
    public static void updateDeltaTime() {
        float currentTime = (float) GLFW.glfwGetTime();
        deltaTime = currentTime - prevTime;
        prevTime = currentTime;
        deltaTime = MinecraftClient.getInstance().isPaused() ? 0.0f : (float) Math.min(0.05, (double) deltaTime);
    }
}
