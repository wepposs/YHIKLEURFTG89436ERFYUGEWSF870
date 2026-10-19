package vesence.mixin;

import java.util.UUID;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BossBarS2CPacket.class)
public interface BossBarS2CPacketAccessor {
   @Accessor("uuid")
   UUID getUuid();
}
