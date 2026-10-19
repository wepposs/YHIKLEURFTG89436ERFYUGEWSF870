package vesence.utils.commands.suggestions;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.Mth;
import vesence.renderengine.render.Renderer2D;

import java.util.List;
import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class CommandSuggestionOverlay {
   private static final int ROW_HEIGHT = 12;
   private static final int CONTENT_HORIZONTAL_PADDING = 4;
   private static final int DESCRIPTION_SPACING = 8;
   private static final int MIN_MARGIN = 4;
   private static final int TEXT_VERTICAL_OFFSET = 2;
   private static final int ROW_BACKGROUND_COLOR = 0x90000000;
   private static final int BORDER_COLOR = -1879048192;
   private static final int TEXT_COLOR = -2039584;
   private static final int DESCRIPTION_COLOR = -6643546;
   private static final int ACCENT_TEXT_COLOR = -11393;

   private CommandSuggestionOverlay() {
   }

   public static Layout render(
           GuiGraphics context,
           Font font,
           EditBox chatField,
           int screenWidth,
           List<CommandSuggestions.SuggestionEntry> entries,
           int selectionIndex,
           int scrollOffset,
           int maxVisible,
           int mouseX,
           int mouseY
   ) {
      Objects.requireNonNull(context, "context");
      Objects.requireNonNull(font, "font");
      Objects.requireNonNull(chatField, "chatField");
      if (entries != null && !entries.isEmpty()) {
         Layout layout = calculateLayout(font, chatField, screenWidth, entries, scrollOffset, maxVisible);
         if (layout == null) {
            return null;
         } else {
            drawBorders(context, layout, entries.size());
            context.fill(layout.areaX(), layout.areaY(), layout.areaRight(), layout.areaBottom(), 0x90000000);
            int themeColor = Renderer2D.ColorUtil.getClientColor();

            for (int visibleIndex = 0; visibleIndex < layout.visibleCount(); visibleIndex++) {
               int entryIndex = layout.scrollOffset() + visibleIndex;
               if (entryIndex >= entries.size()) {
                  break;
               }

               CommandSuggestions.SuggestionEntry entry = entries.get(entryIndex);
               int rowY = layout.areaY() + visibleIndex * layout.rowHeight();
               boolean isSelected = entryIndex == selectionIndex;
               int primaryColor = isSelected ? themeColor : (entry.accent() ? -11393 : -2039584);
               context.drawTextWithShadow(font, entry.primaryText(), layout.contentX(), rowY + 2, primaryColor);
               if (layout.hasDescription() && !entry.description().isEmpty()) {
                  int descriptionX = layout.contentX() + layout.primaryColumnWidth() + 8;
                  int descriptionColor = isSelected ? themeColor : -6643546;
                  context.drawTextWithShadow(font, entry.description(), descriptionX, rowY + 2, descriptionColor);
               }
            }

            return layout;
         }
      } else {
         return null;
      }
   }

   private static void drawBorders(GuiGraphics context, Layout layout, int totalEntries) {
      if (layout.scrollOffset() > 0) {
         context.fill(layout.areaX(), layout.areaY() - 1, layout.areaRight(), layout.areaY(), -1879048192);
      }

      if (layout.scrollOffset() + layout.visibleCount() < totalEntries) {
         context.fill(layout.areaX(), layout.areaBottom(), layout.areaRight(), layout.areaBottom() + 1, -1879048192);
      }
   }

   private static Layout calculateLayout(
           Font font, EditBox chatField, int screenWidth, List<CommandSuggestions.SuggestionEntry> entries, int scrollOffset, int maxVisible
   ) {
      if (entries.isEmpty()) {
         return null;
      } else {
         int effectiveMaxVisible = Math.max(1, maxVisible);
         int clampedOffset = Mth.clamp(scrollOffset, 0, Math.max(entries.size() - 1, 0));
         int primaryWidth = 0;
         int descriptionWidth = 0;
         boolean hasDescription = false;

         for (CommandSuggestions.SuggestionEntry entry : entries) {
            primaryWidth = Math.max(primaryWidth, font.getWidth(entry.primaryText()));
            if (!entry.description().isEmpty()) {
               hasDescription = true;
               descriptionWidth = Math.max(descriptionWidth, font.getWidth(entry.description()));
            }
         }

         int contentWidth = primaryWidth + (hasDescription ? 8 + descriptionWidth : 0);
         int areaWidth = contentWidth + 8;
         int availableWidth = Math.max(0, screenWidth - 8);
         if (areaWidth > availableWidth) {
            areaWidth = availableWidth;
            if (hasDescription) {
               int maxPrimaryWidth = availableWidth - 8;
               if (maxPrimaryWidth < 0) {
                  maxPrimaryWidth = 0;
               }

               primaryWidth = Math.min(primaryWidth, maxPrimaryWidth);
               descriptionWidth = Math.max(0, availableWidth - 8 - primaryWidth - 8);
            }
         }

         int contentX = Mth.clamp(chatField.getX(), 4, screenWidth - areaWidth - 4) + 4;
         int areaX = contentX - 4;
         int visibleCount = Math.min(entries.size() - clampedOffset, effectiveMaxVisible);
         if (visibleCount <= 0) {
            clampedOffset = Math.max(0, entries.size() - effectiveMaxVisible);
            visibleCount = Math.min(entries.size(), effectiveMaxVisible);
         }

         int areaHeight = visibleCount * 12;
         int areaY = Math.max(4, chatField.getY() - 4 - areaHeight);
         return new Layout(
                 areaX, areaY, areaWidth, 12, visibleCount, primaryWidth, descriptionWidth, contentX, clampedOffset, hasDescription
         );
      }
   }

   @Environment(EnvType.CLIENT)
   public record Layout(
           int areaX,
           int areaY,
           int areaWidth,
           int rowHeight,
           int visibleCount,
           int primaryColumnWidth,
           int descriptionColumnWidth,
           int contentX,
           int scrollOffset,
           boolean hasDescription
   ) {
      public Layout(
              int areaX,
              int areaY,
              int areaWidth,
              int rowHeight,
              int visibleCount,
              int primaryColumnWidth,
              int descriptionColumnWidth,
              int contentX,
              int scrollOffset,
              boolean hasDescription
      ) {
         if (visibleCount < 0) {
            throw new IllegalArgumentException("visibleCount must be non-negative");
         } else {
            this.areaX = areaX;
            this.areaY = areaY;
            this.areaWidth = areaWidth;
            this.rowHeight = rowHeight;
            this.visibleCount = visibleCount;
            this.primaryColumnWidth = primaryColumnWidth;
            this.descriptionColumnWidth = descriptionColumnWidth;
            this.contentX = contentX;
            this.scrollOffset = scrollOffset;
            this.hasDescription = hasDescription;
         }
      }

      public int areaRight() {
         return this.areaX + this.areaWidth;
      }

      public int areaBottom() {
         return this.areaY + this.visibleCount * this.rowHeight;
      }

      public boolean contains(double mouseX, double mouseY) {
         return mouseX >= this.areaX && mouseX <= this.areaRight() && mouseY >= this.areaY && mouseY < this.areaBottom();
      }

      public int entryIndexAt(double mouseX, double mouseY) {
         if (this.contains(mouseX, mouseY) && this.visibleCount != 0) {
            int relative = Mth.clamp((int)((mouseY - this.areaY) / this.rowHeight), 0, this.visibleCount - 1);
            return this.scrollOffset + relative;
         } else {
            return -1;
         }
      }
   }
}