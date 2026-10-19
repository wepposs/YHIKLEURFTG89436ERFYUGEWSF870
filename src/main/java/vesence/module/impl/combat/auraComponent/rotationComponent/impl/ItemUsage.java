package vesence.module.impl.combat.auraComponent.rotationComponent.impl;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientStatusC2SPacket.Mode;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.dialog.action.Action;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerRespawnS2CPacket;
import net.minecraft.world.InteractionHand;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.module.impl.combat.auraComponent.rotationComponent.Component;
import vesence.utils.other.IMinecraft;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public class ItemUsage extends Component implements IMinecraft {
   public static final ItemUsage INSTANCE = new ItemUsage();
   public boolean useItem;
   public boolean releaseItem = true;

   @EventInit
   public void onPacket(EventPacket e) {
      Packet var10000 = e.getPacket();
      Objects.requireNonNull(var10000);
      Object var2 = var10000;
      switch (var2) {
         case ServerboundPlayerActionPacket player when player.getAction().equals(Action.RELEASE_USE_ITEM):
            this.releaseItem = true;
            break;
         case ClientStatusC2SPacket status when status.getMode().equals(Mode.PERFORM_RESPAWN):
            this.releaseItem = true;
            break;
         case PlayerRespawnS2CPacket respawn:
            this.releaseItem = true;
            break;
         case GameJoinS2CPacket join:
            this.releaseItem = true;
            break;
         default:
      }
   }

   public void useHand(InteractionHand hand) {
      if (this.releaseItem) {
         mc.gameMode.interactItem(mc.player, hand);
         this.releaseItem = false;
      }

      this.useItem = true;
   }

   @Generated
   public void setUseItem(boolean useItem) {
      this.useItem = useItem;
   }

   @Generated
   public void setReleaseItem(boolean releaseItem) {
      this.releaseItem = releaseItem;
   }

   @Generated
   public boolean isUseItem() {
      return this.useItem;
   }

   @Generated
   public boolean isReleaseItem() {
      return this.releaseItem;
   }
}
