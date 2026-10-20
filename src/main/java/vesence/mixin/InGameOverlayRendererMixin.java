package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.CameraClip;
import vesence.module.impl.visuals.NoRender;

// TODO(port26): renderFireOverlay/renderUnderwaterOverlay/renderInWallOverlay/getInWallBlockState
// removed in 26.3 — screen effects are submitted via private submit* methods driven by PlayerRenderState.
@Environment(EnvType.CLIENT)
@Mixin(ScreenEffectRenderer.class)
public class InGameOverlayRendererMixin {

    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(com.mojang.blaze3d.vertex.PoseStack matrices,
                                            net.minecraft.client.renderer.SubmitNodeCollector collector,
                                            net.minecraft.client.renderer.texture.TextureAtlasSprite sprite,
                                            CallbackInfo ci) {
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr == null || !nr.enable) return;
        if (NoRender.elements.get("Огонь на экране")) {
            ci.cancel();
            return;
        }
        // Лава на экране: пламя рисуется при погружении в лаву — режем его.
        net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
        if (NoRender.elements.get("Лава на экране")
                && client.player != null && client.player.isInLava()) {
            ci.cancel();
        }
    }

    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void vesence$onRenderUnderwaterOverlay(net.minecraft.client.renderer.state.level.PlayerRenderState.WaterOverlay waterOverlay,
                                                          com.mojang.blaze3d.vertex.PoseStack matrices,
                                                          net.minecraft.client.renderer.SubmitNodeCollector collector,
                                                          CallbackInfo ci) {
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Вода на экране")) {
            ci.cancel();
        }
    }

    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void vesence$onRenderInWallOverlay(net.minecraft.resources.Identifier texture,
                                                      float u0, float v0, float u1, float v1,
                                                      com.mojang.blaze3d.vertex.PoseStack matrices,
                                                      net.minecraft.client.renderer.SubmitNodeCollector collector,
                                                      int light,
                                                      CallbackInfo ci) {
        // CameraClip: не показываем оверлей блока при клипе камеры (было getInWallBlockState -> null).
        if (CameraClip.isActive()) {
            ci.cancel();
            return;
        }
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Оверлей в блоке")) {
            ci.cancel();
        }
    }

    // TODO(port26): ScreenEffectRenderer#getInWallBlockState removed in 26.3 (in-wall state is
    // extracted into PlayerRenderState instead); CameraClip coverage moved to submitBlockSprite above.
    // @Inject(method = "getInWallBlockState", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
    // private static void vesence$noInWallOverlay(CallbackInfoReturnable<BlockState> cir) { ... }
}
