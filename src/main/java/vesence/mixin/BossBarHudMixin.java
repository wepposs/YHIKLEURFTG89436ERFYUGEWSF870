package vesence.mixin;

import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(BossBarHud.class)
public class BossBarHudMixin {
   @Shadow private Map<UUID, BossBar> bossBars;

   @Inject(method = "render", at = @At("HEAD"), cancellable = true)
   private void onRender(CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Линия босса")) {
         ci.cancel();
      }
   }

   @Inject(method = "handlePacket", at = @At("HEAD"))
   private void onHandlePacket(BossBarS2CPacket packet, CallbackInfo ci) {
      BossBarS2CPacketAccessor accessor = (BossBarS2CPacketAccessor) (Object) packet;
      UUID id = accessor.getUuid();
      if (id != null && !this.bossBars.containsKey(id) && !this.bossBars.isEmpty()) {
         BossBar existing = this.bossBars.values().iterator().next();
         this.bossBars.put(id, existing);
      }
   }
}
