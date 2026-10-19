package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.com.discord.DiscordEventHandlers;
import vesence.com.discord.DiscordNativeLoader;
import vesence.com.discord.DiscordRPC;
import vesence.com.discord.DiscordRichPresence;

@IModule(
        name = "DiscordRPC",
        description = "Активность в дискорде",
        category = Category.MISC,
        bind = -1
)
@Environment(EnvType.CLIENT)
public class DiscordRCP extends Module {

    private static final String APPLICATION_ID = "1546061279737810994";

    private static final String LARGE_IMAGE_URL =
            "https://i.postimg.cc/13bvLgH1/logo-discord.gif";
    private static final String LARGE_IMAGE_TOOLTIP = "weppos";

    private static final String SMALL_IMAGE_URL = "";
    private static final String SMALL_IMAGE_TOOLTIP = "";

    private DiscordRPC rpc;
    private DiscordEventHandlers handlers;
    private final DiscordRichPresence presence = new DiscordRichPresence();

    /*
     * Время старта сессии клиента: фиксируется один раз
     * и не сбрасывается при смене состояния/сервера,
     * пока клиент открыт.
     */
    private final long sessionStart = System.currentTimeMillis() / 1000L;

    private volatile boolean started = false;
    private volatile boolean ready = false;
    private volatile boolean shutdown = false;

    private Thread thread;

    private volatile ClientState currentState;
    private volatile long stateChangedAt;

    public DiscordRCP() {
        this.currentState = ClientState.LOADING;
        this.stateChangedAt = System.currentTimeMillis() / 1000L;

        this.enable = true;
        this.defaultEnabled = true;

        Thread initThread = new Thread(() -> {
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (this.enable && !this.shutdown) {
                this.startRpc();
            }
        }, "TH-RPC-Init");

        initThread.setDaemon(true);
        initThread.start();

        Runtime.getRuntime().addShutdownHook(
                new Thread(this::onShutdownEvent, "TH-RPC-Shutdown")
        );
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.startRpc();
    }

    public synchronized void startRpc() {
        if (this.started || this.shutdown) {
            return;
        }

        try {
            DiscordNativeLoader.ensureLoaded();
            this.rpc = DiscordRPC.INSTANCE;
        } catch (Throwable t) {
            this.started = false;
            System.out.println(
                    "[DiscordRPC] Не удалось загрузить нативную библиотеку: "
                            + t.getClass().getSimpleName()
                            + " "
                            + (t.getMessage() == null ? "" : t.getMessage())
            );
            t.printStackTrace();
            return;
        }

        this.handlers = new DiscordEventHandlers.Builder()
                .ready(user -> {
                    this.ready = true;
                    System.out.println(
                            "[DiscordRPC] Ready user="
                                    + (user == null
                                    ? "null"
                                    : user.username + "#" + user.discriminator)
                    );

                    this.updatePresence();
                    this.rpc.Discord_UpdatePresence(this.presence);
                })
                .errored((code, message) ->
                        System.out.println(
                                "[DiscordRPC] Error code="
                                        + code
                                        + " message="
                                        + message
                        )
                )
                .disconnected((code, message) -> {
                    this.ready = false;
                    System.out.println(
                            "[DiscordRPC] Disconnected code="
                                    + code
                                    + " message="
                                    + message
                    );
                })
                .build();

        this.rpc.Discord_Initialize(
                APPLICATION_ID,
                this.handlers,
                true,
                ""
        );

        this.presence.startTimestamp = this.sessionStart;

        this.started = true;
        this.thread = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted() && !this.shutdown) {
                    this.rpc.Discord_RunCallbacks();

                    ClientState newState = this.detectState();
                    if (newState != this.currentState) {
                        this.currentState = newState;
                        this.stateChangedAt = System.currentTimeMillis() / 1000L;
                    }

                    if (this.ready) {
                        this.updatePresence();
                        this.rpc.Discord_UpdatePresence(this.presence);
                    }

                    Thread.sleep(3000L);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (Throwable t) {
                System.out.println(
                        "[DiscordRPC] Handler thread stopped: "
                                + t.getClass().getSimpleName()
                                + " "
                                + (t.getMessage() == null ? "" : t.getMessage())
                );
            }
        }, "TH-RPC-Handler");

        this.thread.setDaemon(true);
        this.thread.start();
    }

    private ClientState detectState() {
        try {
            if (mc.player != null && mc.world != null) {
                return mc.getCurrentServerEntry() != null
                        ? ClientState.IN_GAME
                        : ClientState.SINGLEPLAYER;
            }

            var screen = mc.currentScreen;

            if (screen == null) {
                return ClientState.LOADING;
            }

            if (screen instanceof TitleScreen) {
                return ClientState.MAIN_MENU;
            }

            if (screen instanceof MultiplayerScreen) {
                return ClientState.MULTIPLAYER_MENU;
            }

            if (screen instanceof SelectWorldScreen) {
                return ClientState.SINGLEPLAYER;
            }

            return ClientState.MAIN_MENU;
        } catch (Exception ignored) {
            return ClientState.LOADING;
        }
    }

    private void updatePresence() {
        ClientState state = this.currentState;

        // Стартовый таймстамп не сбрасывается при смене состояния:
        // отсчёт времени в Discord идёт с момента запуска клиента.
        this.presence.startTimestamp = this.sessionStart;

        this.presence.largeImageKey = LARGE_IMAGE_URL;
        this.presence.largeImageText = LARGE_IMAGE_TOOLTIP;

        this.presence.smallImageKey =
                SMALL_IMAGE_URL.isEmpty() ? null : SMALL_IMAGE_URL;
        this.presence.smallImageText =
                SMALL_IMAGE_URL.isEmpty() ? null : SMALL_IMAGE_TOOLTIP;

        this.presence.button_label_1 = "Наш Сайт";
        this.presence.button_url_1 = "https://rt.pornhub.com/";

        this.presence.button_label_2 = "Наш Telegram";
        this.presence.button_url_2 = "https://t.me/wepposclient1337";

        switch (state) {
            case LOADING -> this.presence.details = "weppos";

            case MAIN_MENU -> this.presence.details = "weppos";

            case MULTIPLAYER_MENU -> this.presence.details = "weppos";

            case SINGLEPLAYER -> this.presence.details = "weppos";

            case IN_GAME -> {
                String serverIp = this.getServerIp();
                this.presence.details = serverIp.isEmpty() ? "weppos" : serverIp;

                this.presence.smallImageKey = "https://i.gifer.com/2unv.gif";
                this.presence.smallImageText = "В сети";
            }
        }

        // Ник больше НЕ отображается в Discord RPC.
        // this.presence.state = "ник: " + this.getPlayerName();
    }

    private String getPlayerName() {
        try {
            if (NameProtect.isEnabled()) {
                String name = NameProtect.fakeName;
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }

            if (mc.player != null) {
                return mc.player.getName().getString();
            }

            if (mc.getSession() != null) {
                return mc.getSession().getUsername();
            }
        } catch (Exception ignored) {
        }

        return "...";
    }

    private String getServerIp() {
        try {
            var entry = mc.getCurrentServerEntry();

            if (entry != null && entry.address != null) {
                return entry.address;
            }
        } catch (Exception ignored) {
        }

        return "";
    }

    @Override
    public void onDisable() {
        this.started = false;
        this.ready = false;

        if (this.thread != null && this.thread.isAlive()) {
            this.thread.interrupt();
        }

        if (this.rpc != null) {
            try {
                this.rpc.Discord_ClearPresence();
            } catch (Throwable ignored) {
            }

            try {
                this.rpc.Discord_Shutdown();
            } catch (Throwable ignored) {
            }
        }

        super.onDisable();
    }

    public void onShutdownEvent() {
        this.shutdown = true;

        Thread t = this.thread;

        if (t != null && t.isAlive()) {
            t.interrupt();

            try {
                t.join(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            if (this.rpc != null) {
                this.rpc.Discord_ClearPresence();
                this.rpc.Discord_Shutdown();
            }
        } catch (Throwable ignored) {
        }

        this.started = false;
        this.ready = false;
    }

    public enum ClientState {
        LOADING,
        MAIN_MENU,
        SINGLEPLAYER,
        MULTIPLAYER_MENU,
        IN_GAME
    }
}