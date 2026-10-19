package vesence.event.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.screen.slot.SlotActionType;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class ClickSlotEvent extends Event {
   private final int windowId;
   private final int slotId;
   private final int button;
   private final SlotActionType actionType;

   public ClickSlotEvent(int windowId, int slotId, int button, SlotActionType actionType) {
      this.windowId = windowId;
      this.slotId = slotId;
      this.button = button;
      this.actionType = actionType;
   }

   public int getWindowId() { return windowId; }
   public int getSlotId() { return slotId; }
   public int getButton() { return button; }
   public SlotActionType getActionType() { return actionType; }
}
