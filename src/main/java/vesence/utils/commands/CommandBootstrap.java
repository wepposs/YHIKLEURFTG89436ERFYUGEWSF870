package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.command.impl.NeuroCommand;
import vesence.utils.config.ConfigCommand;
import vesence.utils.friends.FriendCommand;
import vesence.utils.staff.StaffCommand;
import java.util.concurrent.atomic.AtomicBoolean;

@Environment(EnvType.CLIENT)
public final class CommandBootstrap {
   private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

   private CommandBootstrap() {
   }

   public static void initialize() {
      if (INITIALIZED.compareAndSet(false, true)) {
         CommandManager manager = CommandManager.getInstance();
         manager.register(ConfigCommand.getInstance());
         manager.register(FriendCommand.getInstance());
         manager.register(StaffCommand.getInstance());
         manager.register(vesence.utils.macro.MacroCommand.getInstance());
         manager.register(vesence.utils.bind.BindCommand.getInstance());
         manager.register(WaypointCommand.getInstance());
         manager.register(EventCommand.getInstance());
         manager.register(DebugCommand.getInstance());
         manager.register(NeuroCommand.getInstance());
      }
   }
}
