package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.CameraClip;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(CallbackInfo ci) {
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr == null || !nr.enable) return;
        if (NoRender.elements.get("Огонь на экране")) {
            ci.cancel();
            return;
        }
        // Лава на экране: пламя рисуется при погружении в лаву — режем его.
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        if (NoRender.elements.get("Лава на экране")
                && client.player != null && client.player.isInLava()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void vesence$onRenderUnderwaterOverlay(CallbackInfo ci) {
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Вода на экране")) {
            ci.cancel();
        }
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void vesence$onRenderInWallOverlay(CallbackInfo ci) {
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Оверлей в блоке")) {
            ci.cancel();
        }
    }

    @Inject(method = "getInWallBlockState", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    private static void vesence$noInWallOverlay(CallbackInfoReturnable<BlockState> cir) {
        if (CameraClip.isActive()) {
            cir.setReturnValue(null);
        }
    }
}
