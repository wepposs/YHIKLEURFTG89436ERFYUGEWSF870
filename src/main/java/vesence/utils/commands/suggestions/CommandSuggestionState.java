package vesence.utils.commands.suggestions;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class CommandSuggestionState {
   public static final int MAX_VISIBLE = 10;

   private List<CommandSuggestions.SuggestionEntry> entries = List.of();
   private int selectionIndex = 0;
   private int scrollOffset = 0;
   private String lastInput = null;
   private CommandSuggestionOverlay.Layout layout = null;

   public void refresh(String input) {
      if (input == null) {
         clear();
         return;
      }
      if (input.equals(lastInput)) {
         return;
      }
      lastInput = input;

      List<CommandSuggestions.SuggestionEntry> newEntries = List.of();
      if (CommandSuggestionManager.supportsInput(input)) {
         CommandSuggestions.SuggestionSet set = CommandSuggestionManager.collect(input);
         if (set != null && !set.isEmpty()) {
            newEntries = set.entries();
         }
      }
      this.entries = newEntries;
      this.selectionIndex = 0;
      this.scrollOffset = 0;
      this.layout = null;
   }

   public void clear() {
      entries = List.of();
      selectionIndex = 0;
      scrollOffset = 0;
      lastInput = null;
      layout = null;
   }

   public boolean isActive() {
      return !entries.isEmpty();
   }

   public List<CommandSuggestions.SuggestionEntry> getEntries() {
      return entries;
   }

   public int getSelectionIndex() {
      return selectionIndex;
   }

   public int getScrollOffset() {
      return scrollOffset;
   }

   public void setLayout(CommandSuggestionOverlay.Layout layout) {
      this.layout = layout;
   }

   public CommandSuggestionOverlay.Layout getLayout() {
      return layout;
   }

   public void move(int delta) {
      if (entries.isEmpty()) {
         return;
      }
      int size = entries.size();
      selectionIndex = ((selectionIndex + delta) % size + size) % size;
      ensureVisible();
   }

   public void setSelectionIndex(int index) {
      if (index < 0 || index >= entries.size()) {
         return;
      }
      selectionIndex = index;
      ensureVisible();
   }

   public void scroll(int delta) {
      if (entries.size() <= MAX_VISIBLE) {
         return;
      }
      int max = entries.size() - MAX_VISIBLE;
      scrollOffset = Math.max(0, Math.min(max, scrollOffset + delta));
   }

   private void ensureVisible() {
      if (selectionIndex < scrollOffset) {
         scrollOffset = selectionIndex;
      } else if (selectionIndex >= scrollOffset + MAX_VISIBLE) {
         scrollOffset = selectionIndex - MAX_VISIBLE + 1;
      }
   }

   public CommandSuggestions.SuggestionEntry getSelectedEntry() {
      if (entries.isEmpty()) {
         return null;
      }
      int index = Math.max(0, Math.min(entries.size() - 1, selectionIndex));
      return entries.get(index);
   }
}
