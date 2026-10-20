package vesence.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.misc.NameProtect;
import vesence.module.impl.visuals.Cape;
import vesence.module.impl.visuals.NoRender;

import java.util.Optional;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerEntityMixin {

    /** Deluxe NoRender: «Эффекты FOV» — множитель FOV всегда 1 (спринт/зелья не меняют FOV). */
    @Inject(method = "getFieldOfViewModifier", at = @At("HEAD"), cancellable = true)
    private void vesence$noFovEffects(boolean firstPerson, float fovScale, CallbackInfoReturnable<Float> cir) {
        if (NoRender.isElementActive("Эффекты FOV")) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void vesence$injectCape(CallbackInfoReturnable<PlayerSkin> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        boolean isSelf = (self == mc.player);

        PlayerSkin result = cir.getReturnValue();
        if (result == null) return;

        boolean changed = false;

        // Streamer Mode «Дефолтные скины»
        NameProtect np = NameProtect.getInstance();
        if (np != null && np.shouldForceDefaultSkins()) {
            PlayerSkin def = DefaultPlayerSkin.get(self.getUUID());
            if (def != null) {
                result = def;
                changed = true;
            }
        }

        // Кастомный плащ — только для себя
        if (isSelf && Vesence.get != null && Vesence.get.manager != null) {
            Cape cape = Vesence.get.manager.get(Cape.class);
            if (cape != null && cape.enable) {
                Identifier capeTexture = cape.getCapeTexture();
                Identifier elytraTexture = cape.getElytraTexture();

                ClientAsset.ResourceTexture capeInfo =
                        new ClientAsset.ResourceTexture(capeTexture, capeTexture);
                ClientAsset.ResourceTexture elytraInfo =
                        new ClientAsset.ResourceTexture(elytraTexture, elytraTexture);

                PlayerSkin.Patch patch = PlayerSkin.Patch.create(
                        Optional.empty(),
                        Optional.of(capeInfo),
                        Optional.of(elytraInfo),
                        Optional.empty()
                );
                result = result.with(patch);
                changed = true;
            }
        }

        if (changed) {
            cir.setReturnValue(result);
        }
    }
}