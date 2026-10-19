package vesence.utils.macro;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.commands.suggestions.AbstractSubCommandSuggestions;
import vesence.utils.commands.suggestions.CommandSuggestions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class MacroCommandSuggestions extends AbstractSubCommandSuggestions {
   private static final MacroCommandSuggestions INSTANCE = new MacroCommandSuggestions();
   private static final Map<String, SubCommand> SUB_COMMANDS;

   private static final List<String> BINDABLE_KEYS;

   private MacroCommandSuggestions() {
   }

   public static MacroCommandSuggestions getInstance() {
      return INSTANCE;
   }

   @Override
   public List<String> commandAliases() {
      return MacroCommand.getInstance().aliases();
   }

   @Override
   protected Map<String, SubCommand> subCommands() {
      return SUB_COMMANDS;
   }

   @Override
   protected String aliasDescription() {
      return "Macro command alias";
   }

   @Override
   protected List<CommandSuggestions.SuggestionEntry> argumentEntries(String subCommand, int argIndex, String partialArgument) {
      if ("remove".equals(subCommand) && argIndex == 0) {
         return buildBoundKeyEntries(partialArgument);
      }
      if ("add".equals(subCommand) && argIndex == 0) {
         return buildKeyEntries(partialArgument);
      }
      return List.of();
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

   private static List<CommandSuggestions.SuggestionEntry> buildBoundKeyEntries(String partialArgument) {
      Map<Integer, String> macros = MacroManager.getInstance().getMacros();
      if (macros.isEmpty()) {
         return List.of();
      }
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      String normalized = partialArgument.toLowerCase(Locale.ROOT);
      List<String> seen = new ArrayList<>();
      for (Map.Entry<Integer, String> macro : macros.entrySet()) {
         String key = MacroManager.keyName(macro.getKey());
         if (key == null || seen.contains(key)) {
            continue;
         }
         seen.add(key);
         if (normalized.isEmpty() || key.toLowerCase(Locale.ROOT).startsWith(normalized)) {
            entries.add(argumentEntry(key, "-> " + macro.getValue(), partialArgument));
         }
      }
      entries.sort((a, b) -> a.primaryText().compareToIgnoreCase(b.primaryText()));
      return entries;
   }

   static {
      Map<String, SubCommand> commands = new LinkedHashMap<>();
      commands.put("add", new SubCommand("add", "add <key> <command>", "Bind a key to a command", true));
      commands.put("remove", new SubCommand("remove", "remove <key>", "Remove a bound macro", true));
      commands.put("list", new SubCommand("list", "list", "Show all bound macros", false));
      commands.put("clear", new SubCommand("clear", "clear", "Clear all macros", false));
      SUB_COMMANDS = Collections.unmodifiableMap(commands);

      List<String> keys = new ArrayList<>();
      for (char c = 'A'; c <= 'Z'; c++) keys.add(String.valueOf(c));
      for (char c = '0'; c <= '9'; c++) keys.add(String.valueOf(c));
      keys.add("SPACE");
      keys.add("ENTER");
      keys.add("TAB");
      keys.add("LSHIFT");
      keys.add("RSHIFT");
      keys.add("LCTRL");
      keys.add("RCTRL");
      BINDABLE_KEYS = Collections.unmodifiableList(keys);
   }
}
