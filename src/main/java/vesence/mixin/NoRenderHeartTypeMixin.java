package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.entry.RegistryEntry;
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
@Mixin(targets = "net.minecraft.client.gui.hud.InGameHud$HeartType")
public class NoRenderHeartTypeMixin {
    @Redirect(
            method = "fromPlayerState(Lnet/minecraft/entity/player/PlayerEntity;)Lnet/minecraft/client/gui/hud/InGameHud$HeartType;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerEntity;hasStatusEffect(Lnet/minecraft/registry/entry/RegistryEntry;)Z"
            )
    )
    private static boolean vesence$hasStatusEffect(PlayerEntity player, RegistryEntry<StatusEffect> effect) {
        if (Vesence.isModInitialized()) {
            NoRender nr = Vesence.get.manager.get(NoRender.class);
            if (nr != null && nr.enable) {
                if (NoRender.elements.get("Плохие сердца")
                        && (effect.matches(StatusEffects.POISON) || effect.matches(StatusEffects.HUNGER))) {
                    return false;
                }
                if (NoRender.elements.get("Сердца визера") && effect.matches(StatusEffects.WITHER)) {
                    return false;
                }
            }
        }
        return player.hasStatusEffect(effect);
    }
}
