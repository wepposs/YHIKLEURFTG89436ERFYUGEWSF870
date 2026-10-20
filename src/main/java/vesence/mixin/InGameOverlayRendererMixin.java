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

// Порт 26.3: старые renderFireOverlay/renderUnderwaterOverlay/renderInWallOverlay заменены
// на submit* методы, которые управляются PlayerRenderState. Хуки перенесены 1:1:
//   renderFireOverlay       -> submitFire
//   renderUnderwaterOverlay -> submitWater
//   renderInWallOverlay     -> submitBlockSprite
//   getInWallBlockState     -> нет аналога (состояние блока извлекается в PlayerRenderState),
//                              поэтому логика CameraClip перенесена в submitBlockSprite.
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

    // Порт 26.3: GameRenderer#showFloatingItem удалён; активация тотема/предмета теперь
    // рисуется здесь. Восстановлен переключатель NoRender «Анимация тотема».
    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void vesence$onRenderItemActivation(net.minecraft.client.renderer.state.level.PlayerRenderState playerState,
                                                com.mojang.blaze3d.vertex.PoseStack matrices,
                                                float partialTick,
                                                net.minecraft.client.renderer.SubmitNodeCollector collector,
                                                CallbackInfo ci) {
        NoRender nr = Vesence.get.manager.get(NoRender.class);
        if (nr != null && nr.enable && NoRender.elements.get("Анимация тотема")) {
            ci.cancel();
        }
    }

    // getInWallBlockState в 26.3 отсутствует: состояние блока для оверлея «в стене»
    // извлекается в PlayerRenderState, отдельного метода-запроса нет. Покрытие
    // CameraClip (раньше возвращали null из этого метода) перенесено в submitBlockSprite.
}
