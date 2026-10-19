package vesence.utils.bind;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Module;
import vesence.utils.commands.suggestions.AbstractSubCommandSuggestions;
import vesence.utils.commands.suggestions.CommandSuggestions;
import vesence.utils.render.utils.KeyUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class BindCommandSuggestions extends AbstractSubCommandSuggestions {
   private static final BindCommandSuggestions INSTANCE = new BindCommandSuggestions();
   private static final Map<String, SubCommand> SUB_COMMANDS;
   private static final List<String> BINDABLE_KEYS;

   private BindCommandSuggestions() {
   }

   public static BindCommandSuggestions getInstance() {
      return INSTANCE;
   }

   @Override
   public List<String> commandAliases() {
      return BindCommand.getInstance().aliases();
   }

   @Override
   protected Map<String, SubCommand> subCommands() {
      return SUB_COMMANDS;
   }

   @Override
   protected String aliasDescription() {
      return "Bind command alias";
   }

   @Override
   protected List<CommandSuggestions.SuggestionEntry> argumentEntries(String subCommand, int argIndex, String partialArgument) {
      if ("add".equals(subCommand)) {
         if (argIndex == 0) return buildModuleEntries(partialArgument, false);
         if (argIndex == 1) return buildKeyEntries(partialArgument);
         return List.of();
      }
      if ("remove".equals(subCommand) && argIndex == 0) {
         return buildModuleEntries(partialArgument, true);
      }
      return List.of();
   }

   private static List<CommandSuggestions.SuggestionEntry> buildModuleEntries(String partialArgument, boolean boundOnly) {
      if (Vesence.get == null || Vesence.get.manager == null) {
         return List.of();
      }
      String normalized = partialArgument.toLowerCase(Locale.ROOT).replace(" ", "");
      List<Module> modules = Vesence.get.manager.getModules();
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (Module module : modules) {
         if (module.hiddenFromGui) continue;
         if (boundOnly && module.bind == -1) continue;
         String token = module.name.replace(" ", "");
         if (normalized.isEmpty() || token.toLowerCase(Locale.ROOT).startsWith(normalized)) {
            String desc = module.bind == -1 ? module.category.getName() : "[" + KeyUtil.getKey(module.bind) + "]";
            entries.add(argumentEntry(token, desc, partialArgument));
         }
      }
      entries.sort((a, b) -> a.primaryText().compareToIgnoreCase(b.primaryText()));
      return entries;
   }

   private static List<CommandSuggestions.SuggestionEntry> buildKeyEntries(String partialArgument) {
      String normalized = partialArgument.toUpperCase(Locale.ROOT);
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (String key : BINDABLE_KEYS) {
         if (normalized.isEmpty() || key.startsWith(normalized)) {
            entries.add(argumentEntry(key, "Key", partialArgument));
         }
      }
      return entries;
   }

   static {
      Map<String, SubCommand> commands = new LinkedHashMap<>();
      commands.put("add", new SubCommand("add", "add <module> <key>", "Bind a key to a module", true));
      commands.put("remove", new SubCommand("remove", "remove <module>", "Remove a module bind", true));
      commands.put("list", new SubCommand("list", "list", "Show all module binds", false));
      commands.put("clear", new SubCommand("clear", "clear", "Clear all module binds", false));
      SUB_COMMANDS = Collections.unmodifiableMap(commands);

      List<String> keys = new ArrayList<>();
      for (char c = 'A'; c <= 'Z'; c++) keys.add(String.valueOf(c));
      for (char c = '0'; c <= '9'; c++) keys.add(String.valueOf(c));
      for (int i = 1; i <= 12; i++) keys.add("F" + i);
      keys.add("SPACE");
      keys.add("LSHIFT");
      keys.add("RSHIFT");
      keys.add("LCTRL");
      keys.add("RCTRL");
      keys.add("LALT");
      keys.add("RALT");
      keys.add("TAB");
      keys.add("GRAVE");
      keys.add("NONE");
      BINDABLE_KEYS = Collections.unmodifiableList(keys);
   }
}
