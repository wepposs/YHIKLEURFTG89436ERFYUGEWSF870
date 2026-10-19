package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(ItemStack.class)
public class NoRenderItemStackGlintMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void vesence$hideGlint(CallbackInfoReturnable<Boolean> cir) {
        if (!Vesence.isModInitialized()) return;
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Блеск зачарования")) {
            cir.setReturnValue(false);
        }
    }
}
