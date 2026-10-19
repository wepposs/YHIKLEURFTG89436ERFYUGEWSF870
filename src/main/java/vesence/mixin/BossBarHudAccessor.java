package vesence.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Доступ к карте активных боссбаров клиента (BossBarHud.bossBars) для проверки
 * «Проверка BossBar» в ElytraSwap («elytra helper»). В 1.21.11 значение карты —
 * net.minecraft.entity.boss.BossBar (совпадает с @Shadow в BossBarHudMixin).
 */
@Mixin(BossBarHud.class)
public interface BossBarHudAccessor {
   @Accessor("bossBars")
   Map<UUID, BossBar> getBossBars();
}
