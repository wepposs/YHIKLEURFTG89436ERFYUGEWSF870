package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.event.EventManager;
import vesence.event.impl.EventPacket;
import vesence.event.player.ScreenCloseEvent;
import vesence.utils.network.NetworkUtils;

@Environment(EnvType.CLIENT)
@Mixin({Connection.class})
public class ClientConnectionMixin {
   @Inject(
      method = {"genericsFtw(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static <T extends PacketListener> void handlePacketPre(Packet<T> packet, PacketListener listener, CallbackInfo info) {
      if (packet == null) return;
      try {
         EventPacket packetEvent = new EventPacket(packet, EventPacket.Type.RECEIVE);
         EventManager.call(packetEvent);
         if (packetEvent.isCancelled()) {
            info.cancel();
         }
      } catch (Exception e) {
         System.err.println("[weppos] Error in handlePacket event: " + e.getMessage());
      }
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/protocol/Packet;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void sendPre(Packet<?> packet, CallbackInfo info) {
      if (packet == null) return;

      if (NetworkUtils.isSending()) return;
      try {
         EventPacket packetEvent = new EventPacket(packet, EventPacket.Type.SEND);
         EventManager.call(packetEvent);
         if (packetEvent.isCancelled()) {
            info.cancel();
         }

         if (packet instanceof ServerboundContainerClosePacket closePacket) {
            Minecraft mc = Minecraft.getInstance();
            ScreenCloseEvent screenCloseEvent = new ScreenCloseEvent(mc.gui.screen(), closePacket.getContainerId());
            EventManager.call(screenCloseEvent);
            if (screenCloseEvent.isCancelled()) {
               info.cancel();
            }
         }
      } catch (Exception e) {
         System.err.println("[weppos] Error in send event: " + e.getMessage());
      }
   }
}
