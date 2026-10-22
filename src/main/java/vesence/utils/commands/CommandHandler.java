package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.pathing.command.BaritoneCommandRegistry;

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
        if (event.isSend() && event.getPacket() instanceof ServerboundChatPacket packet) {
            String message = packet.message();
            if (message != null && !message.isEmpty()) {
                CommandManager commandManager = CommandManager.getInstance();
                if (commandManager.handleMessage(message)) {
                    event.cancel();
                    return;
                }
                // Baritone живёт под своим префиксом (#), чтобы не пересекаться с
                // командами клиента (.). Сообщение с решёткой не должно уходить
                // на сервер: это команда, а не разговор в общий чат.
                if (BaritoneCommandRegistry.getInstance().handle(message)) {
                    event.cancel();
                }
            }
        }
    }
}
