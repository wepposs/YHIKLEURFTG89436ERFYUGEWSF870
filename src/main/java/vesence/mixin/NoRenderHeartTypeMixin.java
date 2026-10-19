package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

/**
 * Порт Relevant HeartTypeNoRenderMixin: перехватывает проверку статус-эффектов
 * при выборе типа сердец, чтобы скрыть жёлтые (яд/голод) и чёрные (визер) сердца,
 * оставляя обычные красные.
 */
@Environment(EnvType.CLIENT)
@Mixin(targets = "net.minecraft.client.gui.hud.Gui$HeartType")
public class NoRenderHeartTypeMixin {
    @Redirect(
            method = "fromPlayerState(Lnet/minecraft/entity/player/Player;)Lnet/minecraft/client/gui/hud/Gui$HeartType;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/Player;hasStatusEffect(Lnet/minecraft/registry/entry/Holder;)Z"
            )
    )
    private static boolean vesence$hasStatusEffect(Player player, Holder<MobEffect> effect) {
        if (Vesence.isModInitialized()) {
            NoRender nr = Vesence.get.manager.get(NoRender.class);
            if (nr != null && nr.enable) {
                if (NoRender.elements.get("Плохие сердца")
                        && (effect.matches(MobEffects.POISON) || effect.matches(MobEffects.HUNGER))) {
                    return false;
                }
                if (NoRender.elements.get("Сердца визера") && effect.matches(MobEffects.WITHER)) {
                    return false;
                }
            }
        }
        return player.hasStatusEffect(effect);
    }
}
