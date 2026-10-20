package vesence.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.visuals.Attributes;

/**
 * Применяет позу «Наруто бег» из модуля Attributes: при беге локального игрока
 * руки отводятся назад, голова слегка наклоняется. Применяется только к
 * локальному игроку в третьем лице.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void vesence$applyNarutoRunPose(AvatarRenderState state, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.level == null) return;
        if (!Vesence.isModInitialized() || Vesence.get == null || Vesence.get.manager == null) return;

        Attributes attributes = Vesence.get.manager.get(Attributes.class);
        if (attributes == null || !attributes.isNarutoActive()) return;

        net.minecraft.client.player.AbstractClientPlayer self = client.player;
        // Только локальный игрок и только третье лицо.
        if (state.skin != self.getSkin()) return; // TODO(port26): render states carry no entity id in 26.3; match by skin
        if (client.options.getCameraType().isFirstPerson()) return;
        if (self.isSwimming() || self.isFallFlying() || self.isUsingItem() || self.isShiftKeyDown()) return;

        float speed = (float) self.getDeltaMovement().horizontalDistance();
        float run = Mth.clamp(speed * 10.0F, 0.0F, 1.0F);
        if (run < 0.10F) return;

        float blend = Mth.clamp((run - 0.10F) / 0.40F, 0.0F, 1.0F);
        blend = blend * blend * (3.0F - 2.0F * blend);

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float tt = (self.tickCount + tickDelta) * 0.6F;
        float swing = Mth.cos(tt) * 0.14F * blend;

        PlayerModel model = (PlayerModel) (Object) this;

        // Руки назад.
        model.rightArm.xRot = Mth.lerp(blend, model.rightArm.xRot, 1.25F + swing);
        model.leftArm.xRot = Mth.lerp(blend, model.leftArm.xRot, 1.25F + swing);
        model.rightArm.zRot = Mth.lerp(blend, model.rightArm.zRot, 0.45F);
        model.leftArm.zRot = Mth.lerp(blend, model.leftArm.zRot, -0.45F);
        model.rightArm.yRot = model.rightArm.yRot + 0.08F * blend;
        model.leftArm.yRot = model.leftArm.yRot - 0.08F * blend;

        // Голова слегка вниз.
        model.head.xRot = model.head.xRot - 0.12F * blend;
    }
}
