package vesence;

import java.io.File;

import lombok.Generated;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.event.EventInit;
import vesence.event.EventManager;
import vesence.event.impl.EventChangeWorld;
import vesence.event.render.RenderEvent;
import vesence.module.api.GuiManager;
import vesence.module.api.ModuleManager;
import vesence.module.impl.combat.auraComponent.rotationComponent.ComponentManager;

import vesence.renderengine.GlContextManager;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.providers.GlBackend;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.cfg.ConfigManager;
import vesence.utils.commands.CommandBootstrap;
import vesence.utils.commands.CommandHandler;
import vesence.utils.config.friend.FriendManager;
import vesence.utils.render.animation.util.AnimationSystem;

import vesence.utils.render.ResourceManager;
import vesence.utils.render.text.FontRegistry;
import vesence.ui.clickgui.GuiClient;
import vesence.utils.render.math.animation.anim2.Animation;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim2.Easing;

@Environment(EnvType.CLIENT)
public class Vesence implements ClientModInitializer {
    public static Vesence get;
    public ModuleManager manager;
    public final String name = "vesence Recode";
    public static Animation alpha = new Animation(Easing.EASE_OUT_SINE, 1500L);
    public static Animation2 animation = new Animation2();
    public static Animation2 alphaPC = new Animation2();
    public final String version = "v1";
    public final File root = new File("C:\\", "vesence\\");
    public static final String rootRes = "vesence";
    private static volatile boolean modInitialized = false;
    private static volatile boolean initialized = false;
    public GuiManager guiManager;
    public FriendManager friendManager;
    public ComponentManager componentManager;
    public ConfigManager configManager;
    private static GlBackend backend;
    private static Renderer2D renderer2D;
    private static volatile boolean fontsInitialized = false;
    private static volatile long renderFrameId = 0L;

    @Generated
    public static boolean isModInitialized() {
        return modInitialized;
    }

    @Generated
    public static boolean areFontsInitialized() {
        return initialized;
    }

    @Generated
    public static Renderer2D getRenderer() {
        return renderer2D;
    }

    @Generated
    public static long getRenderFrameId() {
        return renderFrameId;
    }

    @Generated
    public ModuleManager getManager() {
        return this.manager;
    }

    @Generated
    public String getName() {
        return this.name;
    }

    @Generated
    public String getVersion() {
        return this.version;
    }

    @Generated
    public File getRoot() {
        return this.root;
    }

    @Generated
    public String getRootRes() {
        return this.rootRes;
    }

    @Generated
    public GuiManager getGuiManager() {
        return this.guiManager;
    }

    @Generated
    public ComponentManager getComponentManager() {
        return this.componentManager;
    }

    @Generated
    public ConfigManager getConfigManager() {
        return this.configManager;
    }

    @Generated
    public FriendManager getFriendManager() {
        return this.friendManager;
    }

    @Generated
    public void setManager(ModuleManager manager) {
        this.manager = manager;
    }

    @Generated
    public void setGuiManager(GuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @Generated
    public void setComponentManager(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    @Generated
    public void setConfigManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Generated
    public void setFriendManager(FriendManager friendManager) {
        this.friendManager = friendManager;
    }

    public void onInitializeClient() {
        get = this;

        // === ПРОВЕРКА СЕССИИ ЛАУНЧЕРА ===
        // Без валидного proof от лаунчера мод не грузится, а через 5 секунд
        // процесс завершается. Отложенный halt не даёт сразу понять, что это защита.
        if (!vesence.security.SessionCheck.verify()) {
            new Thread(() -> {
                try {
                    Thread.sleep(5000L);
                } catch (InterruptedException ignored) {
                }
                System.err.println("[weppos] Critical error: memory corruption detected");
                Runtime.getRuntime().halt(1);
            }, "weppos-guard").start();
            return;
        }
        // === /ПРОВЕРКА СЕССИИ ===

        vesence.debug.SkyShotTest.init();

        try {
            vesence.utils.debug.FreezeWatchdog.start();
        } catch (Throwable t) {
            System.err.println("[weppos] Failed to start FreezeWatchdog: " + t.getMessage());
        }

        vesence.module.impl.visuals.custompet.CustomPetRegistry.register();
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                vesence.module.impl.visuals.custompet.CustomPetRegistry.CUSTOM_PET,
                vesence.module.impl.visuals.custompet.CustomPetRenderer::new);

        vesence.module.impl.visuals.particular.ParticularFactoryRegistry.register();

        manager = new ModuleManager();
        manager.init();

        // F3+T: шейдеры неба перекомпилируются из свежих исходников.
        net.fabricmc.fabric.api.resource.ResourceManagerHelper
                .get(net.minecraft.resource.ResourceType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public net.minecraft.util.Identifier getFabricId() {
                        return net.minecraft.util.Identifier.of("vesence", "sky_shader_reload");
                    }

                    @Override
                    public void reload(net.minecraft.resource.ResourceManager manager) {
                        try {
                            vesence.renderengine.render.SkyShaderRenderer.getInstance().reloadPipelines();
                        } catch (Throwable ignored) {
                        }
                    }
                });

        vesence.hmi.LuaTestHMI.init();

        friendManager = new FriendManager();
        FriendManager.init();
        componentManager = new ComponentManager();
        componentManager.init();

        configManager = new ConfigManager();
        guiManager = new GuiManager();
        guiManager.init();

        CommandBootstrap.initialize();
        CommandHandler.getInstance().initialize();
        EventManager.register(CommandHandler.getInstance());
        
        // Initialize Neuro system
        vesence.module.impl.combat.neuro.NeuroProfileStorage.init();
        
        vesence.utils.waypoint.WaypointManager.getInstance().load();
        EventManager.register(vesence.utils.waypoint.WaypointManager.getInstance());
        EventManager.register(vesence.utils.waypoint.EventWaypointStorage.getInstance());
        vesence.overlay.OverlayModule.init();
        GuiClient.registerEventHandlers();
        vesence.ui.MainScreen.registerEventHandler();
        EventManager.register(this);

        if (configManager != null) {
            configManager.load();
            if (configManager.findConfig("default") != null) {
                configManager.loadConfig("default");
            } else {
                configManager.saveConfig("default");
            }
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (configManager != null) {
                configManager.autoSave();
            }
        }));

        modInitialized = true;
    }

    public static void ensureRendererInitialized() {
        GlContextManager.onRenderFrame();
        if (!initialized) {
            onInit();
        }
    }

    private static synchronized void onInit() {
        if (!initialized) {
            backend = new GlBackend();
            renderer2D = new Renderer2D(backend);
            GlContextManager.initialize();
            FontRegistry.initialize(backend, renderer2D);
            initialized = true;
            System.out.println("[weppos] MSDF font system initialized successfully");
        }
    }

    public static void invalidateGlContext() {
        System.out.println("[weppos] OpenGL context lost - invalidating all cached GL resources");
        if (backend != null) {
            try { backend.destroy(); } catch (Exception e) { System.err.println("[weppos] Error destroying backend: " + e.getMessage()); }
            backend = null;
        }
        if (renderer2D != null) {
            renderer2D.reset();
            renderer2D = null;
        }
        ResourceManager.clearCache();
        Renderer2D.clearImageTextureCache();
        FontRegistry.reset();

        initialized = false;
        fontsInitialized = false;
    }

    public static void onRender() {
        vesence.utils.debug.FreezeWatchdog.beat("render");
        GlContextManager.onRenderFrame();
        if (modInitialized) {
            long frameSection = vesence.utils.debug.FreezeWatchdog.sectionEnter("render.frame");
            GlState.Snapshot snapshot = GlState.push();
            try {
                ensureRendererInitialized();
                if (!initialized || renderer2D == null) {
                    return;
                }

                MinecraftClient client = MinecraftClient.getInstance();
                if (client == null || client.getWindow() == null) {
                    return;
                }

                int width = client.getWindow().getFramebufferWidth();
                int height = client.getWindow().getFramebufferHeight();
                if (width <= 0 || height <= 0) {
                    return;
                }

                renderFrameId++;
                AnimationSystem.getInstance().tickOnceForFrame(renderFrameId);
                boolean rendererBegun = false;

                try {
                    long beginSection = vesence.utils.debug.FreezeWatchdog.sectionEnter("renderer2d.begin.render");
                    try {
                        renderer2D.begin(width, height);
                    } finally {
                        vesence.utils.debug.FreezeWatchdog.sectionExit("renderer2d.begin.render", beginSection);
                    }
                    rendererBegun = true;
                    try {
                        EventManager.call(new RenderEvent(client, renderer2D, FontRegistry.SF_MEDIUM, width, height));
                    } finally {
                        if (rendererBegun) {
                            long endSection = vesence.utils.debug.FreezeWatchdog.sectionEnter("renderer2d.end.render");
                            try {
                                renderer2D.end();
                            } finally {
                                vesence.utils.debug.FreezeWatchdog.sectionExit("renderer2d.end.render", endSection);
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[weppos] Error in onRender: " + e.getMessage());
                } finally {
                }
            } finally {
                GlState.pop(snapshot);
                vesence.utils.debug.FreezeWatchdog.sectionExit("render.frame", frameSection);
            }
        }
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        if (configManager != null) {
            configManager.autoSave();
            System.out.println("[weppos] Config auto-saved on world change");
        }
    }
}
