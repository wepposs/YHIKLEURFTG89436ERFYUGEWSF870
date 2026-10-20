package vesence.mixin;

import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(BossHealthOverlay.class)
public class BossBarHudMixin {
   @Shadow private Map<UUID, BossEvent> events;

   @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
   private void onRender(GuiGraphicsExtractor context, CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Линия босса")) {
         ci.cancel();
      }
   }

   @Inject(method = "update", at = @At("HEAD"))
   private void onHandlePacket(ClientboundBossEventPacket packet, CallbackInfo ci) {
      BossBarS2CPacketAccessor accessor = (BossBarS2CPacketAccessor) (Object) packet;
      UUID id = accessor.getUuid();
      if (id != null && !this.events.containsKey(id) && !this.events.isEmpty()) {
         BossEvent existing = this.events.values().iterator().next();
         this.events.put(id, existing);
      }
   }
}
