package vesence.hmi.mixin;

import vesence.hmi.access.LivingEntityAccessor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={MinecraftClient.class})
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public ClientPlayerEntity player;
    @Shadow
    @Nullable
    public ClientWorld world;
    @Shadow
    @Final
    public GameRenderer gameRenderer;
    @Shadow
    @Nullable
    public ClientPlayerInteractionManager interactionManager;
    @Shadow
    private int itemUseCooldown;
    @Shadow
    @Nullable
    public HitResult crosshairTarget;
    @Shadow
    @Final
    private static Logger LOGGER;

    /**
     * Детекция крита для РУЧНЫХ атак (левый клик). Атаки Aura идут через
     * {@link vesence.mixin.ClientPlayerInteractionManagerMixin#onAttackEntity}
     * (attackEntity), а здесь дополнительно обрабатывается ручной путь и, что важно,
     * СБРОС крит-флага при промахе по воздуху/блоку (attackEntity в этом случае не
     * вызывается, иначе крит «залипал» бы на следующем взмахе). Кулдаун в HEAD doAttack
     * ещё не сброшен, поэтому условие крита совпадает с ванильным.
     */
    @Inject(method={"doAttack"}, at={@At("HEAD")})
    public void hmi$detectCrit(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.entity.Entity target = null;
        if (this.crosshairTarget != null && this.crosshairTarget.getType() == HitResult.Type.ENTITY) {
            target = ((EntityHitResult) this.crosshairTarget).getEntity();
        }
        vesence.module.impl.visuals.LivingHands.notifyAttackCrit(
                vesence.hmi.CritDetector.isCriticalAttack(this.player, target));
    }

    @Inject(method={"doAttack"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V")})
    public void doAttackMix(CallbackInfoReturnable<Boolean> cir) {
        ClientPlayerEntity class_7462 = this.player;
        if (class_7462 instanceof LivingEntityAccessor) {
            LivingEntityAccessor mixin = (LivingEntityAccessor)class_7462;
            mixin.hMI5_0$resetMainHandSwing(false);
        }
    }

    @Redirect(method={"doItemUse"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/network/ClientPlayerEntity;swingHand(Lnet/minecraft/util/Hand;)V"))
    private void doItemUse(ClientPlayerEntity instance, Hand hand) {
        if (instance instanceof LivingEntityAccessor) {
            LivingEntityAccessor accessor = (LivingEntityAccessor)instance;
            if (hand == Hand.MAIN_HAND) {
                accessor.hMI5_0$resetMainHandSwing(true);
            } else {
                accessor.hMI5_0$resetOffHandSwing(true);
            }
        }
        instance.swingHand(hand);
    }
}

