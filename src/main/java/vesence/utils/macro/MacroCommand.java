package vesence.utils.macro;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.commands.Command;
import vesence.utils.commands.CommandContext;
import vesence.utils.commands.CommandException;

@Environment(EnvType.CLIENT)
public final class MacroCommand implements Command {

   private static final MacroCommand INSTANCE = new MacroCommand();
   private static final List<String> ALIASES = List.of(".macro", ".m");

   private MacroCommand() {
   }

   public static MacroCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "macro";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
      return ALIASES.get(0) + " <add/remove/list/clear> [key] [command]";
   }

   @Override
   public String description() {
      return "Bind a key to a command";
   }

   @Override
   public void execute(CommandContext context, String arguments) throws CommandException {
      if (arguments == null || arguments.isBlank()) {
         context.sendInfo("Usage: " + usage());
         return;
      }
      String[] parts = arguments.split("\\s+", 2);
      String sub = parts[0].toLowerCase(Locale.ROOT);
      String rest = parts.length > 1 ? parts[1].trim() : "";

      MacroManager mm = MacroManager.getInstance();
      switch (sub) {
         case "add": {
            String[] kv = rest.split("\\s+", 2);
            if (kv.length < 2 || kv[0].isBlank() || kv[1].isBlank()) {
               throw new CommandException("Usage: " + ALIASES.get(0) + " add <key> <command>");
            }
            String key = kv[0];
            String command = kv[1];
            if (mm.add(key, command)) {
               context.sendSuccess("Macro bound: " + key.toUpperCase(Locale.ROOT) + " -> " + command);
            } else {
               throw new CommandException("Invalid key '" + key + "'. Use a single letter/digit or SPACE/ENTER/TAB.");
            }
            break;
         }
         case "remove":
         case "rem":
         case "del":
         case "delete": {
            if (rest.isBlank()) {
               throw new CommandException("Usage: " + ALIASES.get(0) + " remove <key>");
            }
            if (mm.remove(rest)) {
               context.sendSuccess("Macro removed: " + rest.toUpperCase(Locale.ROOT));
            } else {
               throw new CommandException("No macro bound to '" + rest + "'");
            }
            break;
         }
         case "list": {
            Map<Integer, String> macros = mm.getMacros();
            if (macros.isEmpty()) {
               context.sendInfo("No macros bound");
            } else {
               context.sendInfo("Macros (" + macros.size() + "):");
               for (Map.Entry<Integer, String> e : macros.entrySet()) {
                  context.sendInfo(" " + MacroManager.keyName(e.getKey()) + " -> " + e.getValue());
               }
            }
            break;
         }
         case "clear": {
            mm.clear();
            context.sendSuccess("All macros cleared");
            break;
         }
         default:
            throw new CommandException("Unknown command. Use add/remove/list/clear");
      }
   }
}
