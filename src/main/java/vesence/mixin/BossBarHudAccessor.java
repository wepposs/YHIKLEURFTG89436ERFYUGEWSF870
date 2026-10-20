package vesence.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Доступ к карте активных боссбаров клиента (BossHealthOverlay.bossBars) для проверки
 * «Проверка BossEvent» в ElytraSwap («elytra helper»). В 1.21.11 значение карты —
 * net.minecraft.world.BossEvent (совпадает с @Shadow в BossHealthOverlayMixin).
 */
@Mixin(BossHealthOverlay.class)
public interface BossBarHudAccessor {
   @Accessor("events")
   Map<UUID, BossEvent> getBossBars();
}
