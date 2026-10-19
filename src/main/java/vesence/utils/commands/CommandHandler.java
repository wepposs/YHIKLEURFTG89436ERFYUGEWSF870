package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;

@Environment(EnvType.CLIENT)
public final class CommandHandler {
    private static final CommandHandler INSTANCE = new CommandHandler();
    private boolean initialized = false;

    private CommandHandler() {}

    public static CommandHandler getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!initialized) {
            initialized = true;
        }
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (event.isSend() && event.getPacket() instanceof ChatMessageC2SPacket packet) {
            String message = packet.chatMessage();
            if (message != null && !message.isEmpty()) {
                CommandManager commandManager = CommandManager.getInstance();
                if (commandManager.handleMessage(message)) {
                    event.cancel();
                }
            }
        }
    }
}
