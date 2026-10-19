package vesence.utils.commands.suggestions;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Environment(EnvType.CLIENT)
public abstract class AbstractSubCommandSuggestions implements CommandSuggestionProvider {

   @Environment(EnvType.CLIENT)
   public record SubCommand(String name, String usage, String description, boolean takesArgument) {
   }

   public abstract List<String> commandAliases();

   protected abstract Map<String, SubCommand> subCommands();

   protected abstract String aliasDescription();

   protected abstract List<CommandSuggestions.SuggestionEntry> argumentEntries(String subCommand, int argIndex, String partialArgument);

   @Override
   public List<String> aliases() {
      return commandAliases();
   }

   @Override
   public boolean supportsInput(String input) {
      return findAlias(input) != null || matchesAliasPrefix(input);
   }

   @Override
   public CommandSuggestions.SuggestionSet collect(String input) {
      AliasMatch alias = findAlias(input);
      if (alias == null) {
         return null;
      }

      String argsPortion = input.substring(alias.aliasEnd());
      if (argsPortion.isEmpty()) {
         return CommandSuggestions.of(buildCommandEntries("", 0));
      }

      int leadingSpaces = countLeadingWhitespace(argsPortion);
      String rest = argsPortion.substring(leadingSpaces);
      if (rest.isEmpty()) {
         return CommandSuggestions.of(buildCommandEntries("", leadingSpaces));
      }

      boolean endsWithSpace = Character.isWhitespace(argsPortion.charAt(argsPortion.length() - 1));
      String[] parts = rest.split("\\s+");

      if (parts.length == 1 && !endsWithSpace) {
         return CommandSuggestions.of(buildCommandEntries(parts[0], leadingSpaces));
      }

      String subCommand = resolveCommand(parts[0]);
      if (subCommand == null) {
         return null;
      }
      SubCommand metadata = subCommands().get(subCommand);
      if (metadata == null || !metadata.takesArgument()) {
         return null;
      }

      int argIndex;
      String partial;
      if (endsWithSpace) {
         argIndex = parts.length - 1;
         partial = "";
      } else {
         argIndex = parts.length - 2;
         partial = parts[parts.length - 1];
      }
      if (argIndex < 0) {
         return null;
      }

      List<CommandSuggestions.SuggestionEntry> entries = argumentEntries(subCommand, argIndex, partial);
      return CommandSuggestions.of(entries == null ? List.of() : entries);
   }

   @Override
   public List<CommandSuggestions.SuggestionEntry> collectAliasSuggestions(String input) {
      if (input == null) {
         return List.of();
      } else if (".".equals(input)) {
         return buildAllAliasEntries();
      } else {
         return !input.startsWith(".") ? List.of() : buildPartialAliasEntries(input);
      }
   }

   private List<CommandSuggestions.SuggestionEntry> buildCommandEntries(String partialToken, int leadingSpaces) {
      String normalized = partialToken.toLowerCase(Locale.ROOT);
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();

      for (Map.Entry<String, SubCommand> entry : subCommands().entrySet()) {
         String command = entry.getKey();
         if (normalized.isEmpty() || command.startsWith(normalized)) {
            entries.add(createCommandSuggestion(partialToken, leadingSpaces, command, entry.getValue()));
         }
      }

      return entries;
   }

   private List<CommandSuggestions.SuggestionEntry> buildAllAliasEntries() {
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (String alias : commandAliases()) {
         entries.add(new CommandSuggestions.SuggestionEntry(alias, aliasDescription(), alias.substring(1), false));
      }
      return entries;
   }

   private List<CommandSuggestions.SuggestionEntry> buildPartialAliasEntries(String partialInput) {
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (String alias : commandAliases()) {
         if (alias.startsWith(partialInput)) {
            entries.add(new CommandSuggestions.SuggestionEntry(alias, aliasDescription(), alias.substring(partialInput.length()), false));
         }
      }
      return entries;
   }

   private CommandSuggestions.SuggestionEntry createCommandSuggestion(
      String partialToken, int leadingSpaces, String command, SubCommand metadata
   ) {
      StringBuilder completion = new StringBuilder();
      if (partialToken.isEmpty() && leadingSpaces == 0) {
         completion.append(' ');
      }

      if (partialToken.isEmpty()) {
         completion.append(command);
      } else {
         completion.append(command.substring(partialToken.length()));
      }

      if (metadata.takesArgument()) {
         if (completion.length() == 0 || completion.charAt(completion.length() - 1) != ' ') {
            completion.append(' ');
         }
      }

      return new CommandSuggestions.SuggestionEntry(metadata.usage(), metadata.description(), normalizeCompletionSuffix(completion.toString()), false);
   }

   protected static CommandSuggestions.SuggestionEntry argumentEntry(String value, String description, String partialArgument) {
      String completion = value.length() >= partialArgument.length() ? value.substring(partialArgument.length()) : value;
      return new CommandSuggestions.SuggestionEntry(value, description, normalizeCompletionSuffix(completion), false);
   }

   protected AliasMatch findAlias(String input) {
      if (input == null) {
         return null;
      }
      for (String alias : commandAliases()) {
         if (input.length() >= alias.length() && input.regionMatches(true, 0, alias, 0, alias.length())) {
            if (input.length() == alias.length()) {
               return new AliasMatch(alias.length());
            }
            char next = input.charAt(alias.length());
            if (Character.isWhitespace(next)) {
               return new AliasMatch(alias.length());
            }
         }
      }
      return null;
   }

   protected static int countLeadingWhitespace(String value) {
      int index = 0;
      while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
         index++;
      }
      return index;
   }

   private String resolveCommand(String commandToken) {
      for (String command : subCommands().keySet()) {
         if (command.equalsIgnoreCase(commandToken)) {
            return command;
         }
      }
      return null;
   }

   protected static String normalizeCompletionSuffix(String suffix) {
      if (suffix == null) {
         return null;
      }
      return suffix.isEmpty() ? null : suffix;
   }

   @Override
   public boolean matchesAliasPrefix(String input) {
      if (input != null && input.startsWith(".")) {
         for (String alias : commandAliases()) {
            if (alias.startsWith(input)) {
               return true;
            }
         }
      }
      return false;
   }

   @Environment(EnvType.CLIENT)
   protected record AliasMatch(int aliasEnd) {
   }
}
