package vesence.utils.bind;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.commands.Command;
import vesence.utils.commands.CommandContext;
import vesence.utils.commands.CommandException;
import vesence.utils.render.utils.KeyUtil;

import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public final class BindCommand implements Command {
   private static final BindCommand INSTANCE = new BindCommand();
   private static final List<String> ALIASES = List.of(".bind", ".b");

   private BindCommand() {
   }

   public static BindCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "bind";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
      return ALIASES.get(0) + " <add/remove/list/clear> [module] [key]";
   }

   @Override
   public String description() {
      return "Bind a key to a module";
   }

   @Override
   public void execute(CommandContext context, String arguments) throws CommandException {
      if (Vesence.get == null || Vesence.get.manager == null) {
         throw new CommandException("Module system is not ready yet");
      }
      if (arguments == null || arguments.isBlank()) {
         context.sendInfo("Usage: " + usage());
         return;
      }

      String[] parts = arguments.split("\\s+");
      String sub = parts[0].toLowerCase(Locale.ROOT);

      switch (sub) {
         case "add":
            handleAdd(context, parts);
            break;
         case "remove":
         case "rem":
         case "del":
         case "delete":
            handleRemove(context, parts);
            break;
         case "list":
            handleList(context);
            break;
         case "clear":
            handleClear(context);
            break;
         default:
            throw new CommandException("Unknown command. Use add/remove/list/clear");
      }
   }

   private void handleAdd(CommandContext context, String[] parts) throws CommandException {
      if (parts.length < 3) {
         throw new CommandException("Usage: " + ALIASES.get(0) + " add <module> <key>");
      }
      String keyToken = parts[parts.length - 1];
      String moduleName = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length - 1));

      Module module = findModule(moduleName);
      if (module == null) {
         throw new CommandException("Module '" + moduleName + "' not found");
      }

      int key = resolveKey(keyToken);
      if (key == Integer.MIN_VALUE) {
         throw new CommandException("Invalid key '" + keyToken + "'");
      }

      module.bind = key;
      if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
      context.sendSuccess("Bound " + module.name + " -> " + KeyUtil.getKey(key));
   }

   private void handleRemove(CommandContext context, String[] parts) throws CommandException {
      if (parts.length < 2) {
         throw new CommandException("Usage: " + ALIASES.get(0) + " remove <module>");
      }
      String moduleName = String.join(" ", java.util.Arrays.copyOfRange(parts, 1, parts.length));
      Module module = findModule(moduleName);
      if (module == null) {
         throw new CommandException("Module '" + moduleName + "' not found");
      }
      if (module.bind == -1) {
         throw new CommandException("Module '" + module.name + "' has no bind");
      }
      module.bind = -1;
      if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
      context.sendSuccess("Removed bind from " + module.name);
   }

   private void handleList(CommandContext context) {
      MutableText builder = Text.literal("Binds: ");
      boolean any = false;
      List<Module> modules = Vesence.get.manager.getModules();
      for (int i = 0; i < modules.size(); i++) {
         Module module = modules.get(i);
         if (module.bind == -1) continue;
         if (any) builder = builder.append(Text.literal(" | "));
         MutableText entry = Text.literal(module.name + " [" + KeyUtil.getKey(module.bind) + "]")
            .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Renderer2D.ColorUtil.getClientColor())));
         builder = builder.append(entry);
         any = true;
      }
      if (!any) {
         context.sendInfo("No modules are bound");
      } else {
         context.sendInfo(builder);
      }
   }

   private void handleClear(CommandContext context) {
      int count = 0;
      for (Module module : Vesence.get.manager.getModules()) {
         if (module.bind != -1) {
            module.bind = -1;
            count++;
         }
      }
      if (Vesence.get.configManager != null) Vesence.get.configManager.autoSave();
      context.sendSuccess("Cleared " + count + " bind(s)");
   }

   private static Module findModule(String name) {
      if (name == null || name.isBlank()) return null;
      String normalized = name.trim();
      List<Module> modules = Vesence.get.manager.getModules();

      for (Module module : modules) {
         if (module.name.equalsIgnoreCase(normalized)) return module;
      }

      String compact = normalized.replace(" ", "");
      for (Module module : modules) {
         if (module.name.replace(" ", "").equalsIgnoreCase(compact)) return module;
      }
      return null;
   }

   private static int resolveKey(String token) {
      if (token == null || token.isBlank()) return Integer.MIN_VALUE;
      String up = token.trim().toUpperCase(Locale.ROOT);
      if ("NONE".equals(up) || "NULL".equals(up)) return -1;
      Integer mapped = KeyUtil.keyMap.get(up);
      return mapped != null ? mapped : Integer.MIN_VALUE;
   }
}
