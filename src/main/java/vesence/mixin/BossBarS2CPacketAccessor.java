package vesence.mixin;

import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundBossEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundBossEventPacket.class)
public interface BossBarS2CPacketAccessor {
   @Accessor("id")
   UUID getUuid();
}
