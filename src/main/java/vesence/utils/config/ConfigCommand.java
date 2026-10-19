package vesence.utils.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Colors;
import vesence.Vesence;
import vesence.module.impl.visuals.Hud;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.cfg.Config;
import vesence.utils.cfg.ConfigManager;
import vesence.utils.commands.Command;
import vesence.utils.commands.CommandContext;
import vesence.utils.commands.CommandException;

import java.util.*;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public final class ConfigCommand implements Command {
   private static final ConfigCommand INSTANCE = new ConfigCommand();
   private static final List<String> COMMAND_ALIASES = List.of(".cfg", ".c", ".config", ".cgf", ".fig");
   private static final Map<String, CommandMetadata> COMMAND_DEFINITIONS;
   private static final List<String> SUB_COMMANDS;
   private static final String SUPPORTED_COMMANDS = "save/load/list/delete";

   private ConfigCommand() {
   }

   public static ConfigCommand getInstance() {
      return INSTANCE;
   }

   public List<String> getCommandAliases() {
      return COMMAND_ALIASES;
   }

   public List<String> getSubCommands() {
      return SUB_COMMANDS;
   }

   public Map<String, CommandMetadata> getCommandMetadata() {
      return COMMAND_DEFINITIONS;
   }

   @Override
   public String name() {
      return "config";
   }

   @Override
   public List<String> aliases() {
      return COMMAND_ALIASES;
   }

   @Override
   public String usage() {
      return COMMAND_ALIASES.get(0) + " <save/load/list/delete>";
   }

   @Override
   public String description() {
      return "Manage client configuration profiles";
   }

   @Override
   public void execute(CommandContext context, String arguments) throws CommandException {
      if (Vesence.get.configManager == null) {
         throw new CommandException("Configuration system is not ready yet");
      } else if (arguments != null && !arguments.isBlank()) {
         String[] parts = arguments.split("\\s+", 2);
         String subCommand = parts[0].toLowerCase(Locale.ROOT);
         String remainder = parts.length > 1 ? parts[1].trim() : "";
         switch (subCommand) {
            case "save":
               this.handleSave(context, remainder);
               break;
            case "load":
               this.handleLoad(context, remainder);
               break;
            case "list":
               this.handleList(context);
               break;
            case "delete":
               this.handleDelete(context, remainder);
               break;
            case "dir":
            case "folder":
            case "open":
               this.handleDir(context);
               break;
            case "reset":
               this.handleReset(context);
               break;
            default:
               throw new CommandException("Unknown command. Use save/load/list/delete/dir/reset");
         }
      } else {
         context.sendInfo(
            "Usage: "
               + COMMAND_DEFINITIONS.values().stream().map(metadata -> COMMAND_ALIASES.get(0) + " " + metadata.usage()).collect(Collectors.joining(", "))
         );
      }
   }

   private void handleSave(CommandContext context, String name) throws CommandException {
      if (name != null && !name.isBlank()) {
         ConfigManager configManager = Vesence.get.configManager;
         if (configManager.saveConfig(name)) {
            context.sendSuccess("Config '" + name + "' saved");
         } else {
            throw new CommandException("Failed to save config '" + name + "'");
         }
      } else {
         throw new CommandException("Specify config name");
      }
   }

   private void handleLoad(CommandContext context, String name) throws CommandException {
      if (name != null && !name.isBlank()) {
         ConfigManager configManager = Vesence.get.configManager;
         if (configManager.loadConfig(name)) {
            context.sendSuccess("Config '" + name + "' loaded");
         } else {
            throw new CommandException("Config '" + name + "' not found or failed to load");
         }
      } else {
         throw new CommandException("Specify config name to load");
      }
   }

   private void handleList(CommandContext context) {
      ConfigManager configManager = Vesence.get.configManager;
      List<Config> configs = configManager.getContents();
      if (configs.isEmpty()) {
         context.sendInfo("No available configs");
      } else {
         MutableText builder = Text.literal("Available configs: ");

         for (int i = 0; i < configs.size(); i++) {
            String name = configs.get(i).getName();
            MutableText nameText = Text.literal(name);
            nameText = nameText.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Renderer2D.ColorUtil.getClientColor())));
            builder = builder.append(nameText);
            if (i < configs.size() - 1) {
               builder = builder.append(Text.literal(" | "));
            }
         }

         context.sendInfo(builder);
      }
   }

   private void handleDelete(CommandContext context, String name) throws CommandException {
      if (name != null && !name.isBlank()) {
         ConfigManager configManager = Vesence.get.configManager;
         if (configManager.deleteConfig(name)) {
            context.sendSuccess("Config '" + name + "' deleted");
         } else {
            throw new CommandException("Config '" + name + "' not found or failed to delete");
         }
      } else {
         throw new CommandException("Specify config name to delete");
      }
   }

   private void handleDir(CommandContext context) throws CommandException {
      java.io.File dir = ConfigManager.configDirectory;
      try {
         if (!dir.exists()) {
            dir.mkdirs();
         }
         boolean opened = false;
         if (java.awt.Desktop.isDesktopSupported()) {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
               desktop.open(dir);
               opened = true;
            }
         }
         if (!opened) {

            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (os.contains("win")) {
               new ProcessBuilder("explorer.exe", dir.getAbsolutePath()).start();
               opened = true;
            }
         }
         if (opened) {
            context.sendSuccess("Opened config directory");
         } else {
            context.sendInfo("Config directory: " + dir.getAbsolutePath());
         }
      } catch (Exception e) {
         context.sendInfo("Config directory: " + dir.getAbsolutePath());
      }
   }

   private void handleReset(CommandContext context) {
      int disabled = 0;
      for (vesence.module.api.Module module : Vesence.get.manager.module) {
         try {
            if (module.enable) {
               module.toggle();
               disabled++;
            }
            resetModuleSettings(module);

            module.bind = -1;
            resetBindSettings(module);
         } catch (Exception ignored) {
         }
      }

      try {
         vesence.utils.cfg.DraggableManager.getInstance().resetHudElements();
      } catch (Exception ignored) {
      }
      if (Vesence.get.configManager != null) {
         Vesence.get.configManager.autoSave();
      }
      context.sendSuccess("Reset complete (" + disabled + " modules disabled, settings & HUD reset)");
   }

   private void resetModuleSettings(vesence.module.api.Module module) {
      for (vesence.module.api.setting.Setting<?> set : module.getSettings()) {
         if (set instanceof vesence.module.api.setting.impl.BooleanSetting bs) {
            bs.set(bs.getDefault());
         } else if (set instanceof vesence.module.api.setting.impl.SliderSetting ss) {
            ss.current = ss.getDefault();
         } else if (set instanceof vesence.module.api.setting.impl.ModeSetting ms) {
            ms.currentMode = ms.getDefault();
         } else if (set instanceof vesence.module.api.setting.impl.MultiBooleanSetting mbs) {
            for (vesence.module.api.setting.impl.BooleanSetting b : mbs.settings) {
               b.set(b.getDefault());
            }
         }
      }
   }

   private void resetBindSettings(vesence.module.api.Module module) {
      for (vesence.module.api.setting.Setting<?> set : module.getSettings()) {
         if (set instanceof vesence.module.api.setting.impl.BindSettings bs) {
            bs.key = -1;
            bs.active = false;
         }
      }
   }

   static {
      Map<String, CommandMetadata> commands = new LinkedHashMap<>();
      commands.put("save", new CommandMetadata("save <name>", ArgumentType.NEW_CONFIG_NAME, "Save current config"));
      commands.put(
         "load", new CommandMetadata("load <name>", ArgumentType.EXISTING_CONFIG_NAME, "Load a config and apply its settings")
      );
      commands.put("list", new CommandMetadata("list", ArgumentType.NONE, "Show all available configs"));
      commands.put("delete", new CommandMetadata("delete <name>", ArgumentType.EXISTING_CONFIG_NAME, "Delete a saved config"));
      commands.put("dir", new CommandMetadata("dir", ArgumentType.NONE, "Open the config folder in explorer"));
      commands.put("reset", new CommandMetadata("reset", ArgumentType.NONE, "Disable all modules and reset settings & HUD"));
      COMMAND_DEFINITIONS = Collections.unmodifiableMap(commands);
      SUB_COMMANDS = List.copyOf(COMMAND_DEFINITIONS.keySet());
   }

   @Environment(EnvType.CLIENT)
   public static enum ArgumentType {
      NONE,
      NEW_CONFIG_NAME,
      EXISTING_CONFIG_NAME;
   }

   @Environment(EnvType.CLIENT)
   public record CommandMetadata(String usage, ArgumentType argumentType, String description) {
   }
}
