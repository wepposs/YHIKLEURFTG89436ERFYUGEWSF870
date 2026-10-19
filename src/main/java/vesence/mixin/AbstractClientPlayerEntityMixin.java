package vesence.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.misc.NameProtect;
import vesence.module.impl.visuals.Cape;
import vesence.module.impl.visuals.NoRender;

import java.util.Optional;

@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    /** Deluxe NoRender: «Эффекты FOV» — множитель FOV всегда 1 (спринт/зелья не меняют FOV). */
    @Inject(method = "getFovMultiplier", at = @At("HEAD"), cancellable = true)
    private void vesence$noFovEffects(boolean firstPerson, float fovScale, CallbackInfoReturnable<Float> cir) {
        if (NoRender.isElementActive("Эффекты FOV")) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void vesence$injectCape(CallbackInfoReturnable<SkinTextures> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        boolean isSelf = (self == mc.player);

        SkinTextures result = cir.getReturnValue();
        if (result == null) return;

        boolean changed = false;

        // Streamer Mode «Дефолтные скины»
        NameProtect np = NameProtect.getInstance();
        if (np != null && np.shouldForceDefaultSkins()) {
            SkinTextures def = DefaultSkinHelper.getSkinTextures(self.getUuid());
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

                AssetInfo.TextureAssetInfo capeInfo =
                        new AssetInfo.TextureAssetInfo(capeTexture, capeTexture);
                AssetInfo.TextureAssetInfo elytraInfo =
                        new AssetInfo.TextureAssetInfo(elytraTexture, elytraTexture);

                SkinTextures.SkinOverride override = SkinTextures.SkinOverride.create(
                        Optional.empty(),
                        Optional.of(capeInfo),
                        Optional.of(elytraInfo),
                        Optional.empty()
                );
                result = result.withOverride(override);
                changed = true;
            }
        }

        if (changed) {
            cir.setReturnValue(result);
        }
    }
}