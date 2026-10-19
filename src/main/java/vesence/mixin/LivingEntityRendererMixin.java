package vesence.mixin;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.Attributes;
import vesence.module.impl.visuals.Nametags;
import vesence.module.impl.visuals.SeeInvisibles;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;pop()V"))
    private void vesence$headLayer(LivingEntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState) || !Vesence.isModInitialized()) return;
        Attributes attributes = Vesence.get.manager.get(Attributes.class);
        if (attributes == null || !attributes.isChinaHatActive()) return;
        PlayerEntity player = null;
        if (net.minecraft.client.MinecraftClient.getInstance().world != null) {
            net.minecraft.entity.Entity entity = net.minecraft.client.MinecraftClient.getInstance().world.getEntityById(playerState.id);
            if (entity instanceof PlayerEntity p) player = p;
        }
        if (player == null) return;
        attributes.renderChinaHatOnHead(player, matrices, ((LivingEntityRenderer<?, ?, ?>)(Object)this).getModel(), queue);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ColorHelper;mix(II)I"))
    private int onColorMix(int j, int mixColor) {
        if (j == 0x26FFFFFF && Vesence.isModInitialized()) {
            SeeInvisibles module = Vesence.get.manager.get(SeeInvisibles.class);
            if (module != null && module.enable) j = (module.alpha.get().intValue() << 24) | 0xFFFFFF;
        }
        return ColorHelper.mix(j, mixColor);
    }

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void onHasLabel(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (Vesence.get == null || Vesence.get.manager == null) return;
        Nametags nametags = Vesence.get.manager.get(Nametags.class);
        if (nametags != null && nametags.enable && nametags.hidesVanillaLabel(entity)) cir.setReturnValue(false);
    }
}
