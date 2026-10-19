package vesence.utils.commands.suggestions;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class CommandSuggestions {
   private CommandSuggestions() {
   }

   public static SuggestionSet of(List<SuggestionEntry> entries) {
      if (entries != null && !entries.isEmpty()) {
         List<SuggestionEntry> immutableEntries = List.copyOf(entries);
         String suffix = immutableEntries.get(0).completionSuffix();
         return new SuggestionSet(suffix, immutableEntries);
      } else {
         return null;
      }
   }

   @Environment(EnvType.CLIENT)
   public record SuggestionEntry(String primaryText, String description, String completionSuffix, boolean accent) {
      public SuggestionEntry(String primaryText, String description, String completionSuffix, boolean accent) {
         primaryText = Objects.requireNonNull(primaryText, "primaryText");
         description = description == null ? "" : description;
         this.primaryText = primaryText;
         this.description = description;
         this.completionSuffix = completionSuffix;
         this.accent = accent;
      }
   }

   @Environment(EnvType.CLIENT)
   public record SuggestionSet(String suggestionSuffix, List<SuggestionEntry> entries) {
      public SuggestionSet(String suggestionSuffix, List<SuggestionEntry> entries) {
         Objects.requireNonNull(entries, "entries");
         this.suggestionSuffix = suggestionSuffix;
         this.entries = entries;
      }

      public boolean isEmpty() {
         return this.entries.isEmpty();
      }
   }
}
