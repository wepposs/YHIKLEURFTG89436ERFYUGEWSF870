package vesence.utils.commands.suggestions;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;
import java.util.Locale;

@Environment(EnvType.CLIENT)
public interface CommandSuggestionProvider {
   List<String> aliases();

   boolean supportsInput(String var1);

   CommandSuggestions.SuggestionSet collect(String var1);

   List<CommandSuggestions.SuggestionEntry> collectAliasSuggestions(String var1);

   default boolean matchesAliasPrefix(String input) {
      if (input != null && !input.isEmpty()) {
         String normalized = input.toLowerCase(Locale.ROOT);

         for (String alias : this.aliases()) {
            if (alias.toLowerCase(Locale.ROOT).startsWith(normalized)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
