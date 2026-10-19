package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.waypoint.EventWaypointStorage;

import java.util.List;
import java.util.Map;

/**
 * Порт команды /event из RelevantPremiumpp4 (управление ивент-метками EventParser).
 */
@Environment(EnvType.CLIENT)
public final class EventCommand implements Command {

   private static final EventCommand INSTANCE = new EventCommand();
   private static final List<String> ALIASES = List.of(".event", ".ev");

   private EventCommand() {
   }

   public static EventCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "Event";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
      return ".event <clear|clearall|list>";
   }

   @Override
   public String description() {
      return "Управление ивент-метками (EventParser)";
   }

   @Override
   public void execute(CommandContext ctx, String args) throws CommandException {
      EventWaypointStorage storage = EventWaypointStorage.getInstance();

      String trimmed = args == null ? "" : args.trim();
      if (trimmed.isEmpty()) {
         ctx.sendInfo("Использование: " + usage());
         return;
      }

      String sub = trimmed.split("\\s+")[0].toLowerCase();

      switch (sub) {
         case "clear": {
            storage.clearEventWaypoints();
            ctx.sendSuccess("Все ивент-метки удалены");
            break;
         }
         case "clearall": {
            storage.clearAll();
            ctx.sendSuccess("Все метки удалены");
            break;
         }
         case "list":
         case "ls": {
            if (storage.isEmpty()) {
               ctx.sendInfo("Ивент-меток нет");
               return;
            }
            ctx.sendSuccess("Ивент-метки (" + storage.size() + "):");
            for (Map.Entry<String, EventWaypointStorage.Entry> e : storage.getWaypoints().entrySet()) {
               EventWaypointStorage.Entry w = e.getValue();
               String rar = (w.getRarity() == null || w.getRarity().isEmpty()) ? "" : " [" + w.getRarity() + "]";
               ctx.sendInfo("§f" + e.getKey() + rar + " §7| §f" + w.getX() + ", " + w.getY() + ", " + w.getZ());
            }
            break;
         }
         default:
            ctx.sendInfo("Использование: " + usage());
            break;
      }
   }
}
