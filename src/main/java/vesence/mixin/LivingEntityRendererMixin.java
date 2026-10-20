package vesence.mixin;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.ARGB;
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
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void vesence$headLayer(LivingEntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState playerState) || !Vesence.isModInitialized()) return;
        Attributes attributes = Vesence.get.manager.get(Attributes.class);
        if (attributes == null || !attributes.isChinaHatActive()) return;
        // 26.3: AvatarRenderer.extractRenderState заполняет state.id = entity.getId(),
        // поэтому ищем игрока по id — как в исходной версии (world.getEntityById).
        // Прежний обход с поиском по скину брал ПЕРВОГО игрока с таким же скином,
        // из-за чего шляпа рисовалась не тому игроку.
        Player player = null;
        net.minecraft.client.Minecraft mcInst = net.minecraft.client.Minecraft.getInstance();
        if (mcInst.level != null) {
            net.minecraft.world.entity.Entity entity = mcInst.level.getEntity(playerState.id);
            if (entity instanceof Player p) {
                player = p;
            }
        }
        if (player == null) return;
        attributes.renderChinaHatOnHead(player, matrices, ((LivingEntityRenderer<?, ?, ?>)(Object)this).getModel(), queue);
    }

    @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/ARGB;multiply(II)I"))
    private int onColorMix(int j, int mixColor) {
        if (j == 0x26FFFFFF && Vesence.isModInitialized()) {
            SeeInvisibles module = Vesence.get.manager.get(SeeInvisibles.class);
            if (module != null && module.enable) j = (module.alpha.get().intValue() << 24) | 0xFFFFFF;
        }
        // 26.3: ColorHelper.mix(II) переименован в ARGB.multiply(II) с той же
        // семантикой (покомпонентное умножение, -1 = тождество). alphaBlend здесь
        // использовать нельзя — это другая операция, она меняла цвет всех сущностей.
        return ARGB.multiply(j, mixColor);
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void onHasLabel(LivingEntity entity, double distance, CallbackInfoReturnable<Boolean> cir) {
        if (Vesence.get == null || Vesence.get.manager == null) return;
        Nametags nametags = Vesence.get.manager.get(Nametags.class);
        if (nametags != null && nametags.enable && nametags.hidesVanillaLabel(entity)) cir.setReturnValue(false);
    }
}
