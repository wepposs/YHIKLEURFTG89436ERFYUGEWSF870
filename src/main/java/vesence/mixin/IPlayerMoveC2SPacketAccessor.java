package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Аксессор к полям исходящего {@link ServerboundMovePlayerPacket}. Позволяет
 * подменять y/onGround в уже собранном пакете движения ДО его отправки —
 * используется {@link vesence.module.impl.misc.GrimBypass} для ground-spoof /
 * criticals-pulse / nofall. Порт из relevant.
 *
 * Оставлены только реально используемые поля (y, onGround). В relevant
 * аксессор также объявлял x/z/horizontalCollision, но GrimBypass их не менял
 * (handleVelocity — no-op), а лишние @Accessor к возможно отсутствующим полям
 * ломают применение всего миксина в рантайме — поэтому они не переносятся.
 */
@Environment(EnvType.CLIENT)
@Mixin({ ServerboundMovePlayerPacket.class })
public interface IPlayerMoveC2SPacketAccessor {
   @Accessor("y")
   double getY();

   @Mutable
   @Accessor("y")
   void setY(double y);

   @Accessor("onGround")
   boolean getOnGround();

   @Mutable
   @Accessor("onGround")
   void setOnGround(boolean onGround);
}
