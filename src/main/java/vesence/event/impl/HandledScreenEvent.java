package vesence.event.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.screen.slot.Slot;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class HandledScreenEvent extends Event {
   private final DrawContext drawContext;
   private final Slot slotHover;
   private final int backgroundWidth;
   private final int backgroundHeight;
   private final int screenX;
   private final int screenY;

   public HandledScreenEvent(DrawContext drawContext, Slot slotHover, int backgroundWidth, int backgroundHeight, int screenX, int screenY) {
      this.drawContext = drawContext;
      this.slotHover = slotHover;
      this.backgroundWidth = backgroundWidth;
      this.backgroundHeight = backgroundHeight;
      this.screenX = screenX;
      this.screenY = screenY;
   }

   public DrawContext getDrawContext() { return drawContext; }
   public Slot getSlotHover() { return slotHover; }
   public int getBackgroundWidth() { return backgroundWidth; }
   public int getBackgroundHeight() { return backgroundHeight; }
   public int getScreenX() { return screenX; }
   public int getScreenY() { return screenY; }
}
