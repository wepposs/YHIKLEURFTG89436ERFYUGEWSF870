package vesence.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
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
@Mixin(PlayerEntityModel.class)
public abstract class PlayerEntityModelMixin {

    @Inject(method = "setAngles(Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;)V", at = @At("TAIL"))
    private void vesence$applyNarutoRunPose(PlayerEntityRenderState state, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null || client.world == null) return;
        if (!Vesence.isModInitialized() || Vesence.get == null || Vesence.get.manager == null) return;

        Attributes attributes = Vesence.get.manager.get(Attributes.class);
        if (attributes == null || !attributes.isNarutoActive()) return;

        PlayerEntity self = client.player;
        // Только локальный игрок и только третье лицо.
        if (state.id != self.getId()) return;
        if (client.options.getPerspective().isFirstPerson()) return;
        if (self.isSwimming() || self.isGliding() || self.isUsingItem() || self.isSneaking()) return;

        float speed = (float) self.getVelocity().horizontalLength();
        float run = MathHelper.clamp(speed * 10.0F, 0.0F, 1.0F);
        if (run < 0.10F) return;

        float blend = MathHelper.clamp((run - 0.10F) / 0.40F, 0.0F, 1.0F);
        blend = blend * blend * (3.0F - 2.0F * blend);

        float tickDelta = client.getRenderTickCounter().getTickProgress(false);
        float tt = (self.age + tickDelta) * 0.6F;
        float swing = MathHelper.cos(tt) * 0.14F * blend;

        PlayerEntityModel model = (PlayerEntityModel) (Object) this;

        // Руки назад.
        model.rightArm.pitch = MathHelper.lerp(blend, model.rightArm.pitch, 1.25F + swing);
        model.leftArm.pitch = MathHelper.lerp(blend, model.leftArm.pitch, 1.25F + swing);
        model.rightArm.roll = MathHelper.lerp(blend, model.rightArm.roll, 0.45F);
        model.leftArm.roll = MathHelper.lerp(blend, model.leftArm.roll, -0.45F);
        model.rightArm.yaw = model.rightArm.yaw + 0.08F * blend;
        model.leftArm.yaw = model.leftArm.yaw - 0.08F * blend;

        // Голова слегка вниз.
        model.head.pitch = model.head.pitch - 0.12F * blend;
    }
}
