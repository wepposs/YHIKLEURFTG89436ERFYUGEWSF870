package vesence.hmi.mixin;

import vesence.hmi.access.LivingEntityAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Minecraft.class})
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public LocalPlayer player;
    @Shadow
    @Nullable
    public ClientLevel level;
    @Shadow
    @Final
    public GameRenderer gameRenderer;
    @Shadow
    @Nullable
    public MultiPlayerGameMode gameMode;
    @Shadow
    @Nullable
    public HitResult hitResult;
    @Shadow
    @Final
    private static Logger LOGGER;

    /**
     * Детекция крита для РУЧНЫХ атак (левый клик). Атаки Aura идут через
     * {@link vesence.mixin.ClientPlayerInteractionManagerMixin#onAttackEntity}
     * (attackEntity), а здесь дополнительно обрабатывается ручной путь и, что важно,
     * СБРОС крит-флага при промахе по воздуху/блоку (attackEntity в этом случае не
     * вызывается, иначе крит «залипал» бы на следующем взмахе). Кулдаун в HEAD startAttack
     * ещё не сброшен, поэтому условие крита совпадает с ванильным.
     */
    @Inject(method={"startAttack"}, at={@At("HEAD")})
    public void hmi$detectCrit(CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.world.entity.Entity target = null;
        if (this.hitResult != null && this.hitResult.getType() == HitResult.Type.ENTITY) {
            target = ((EntityHitResult) this.hitResult).getEntity();
        }
        vesence.module.impl.visuals.LivingHands.notifyAttackCrit(
                vesence.hmi.CritDetector.isCriticalAttack(this.player, target));
    }

    @Inject(method={"startAttack"}, at={@At(value="INVOKE", target="Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z")})
    public void doAttackMix(CallbackInfoReturnable<Boolean> cir) {
        LocalPlayer localPlayer = this.player;
        if (localPlayer instanceof LivingEntityAccessor) {
            LivingEntityAccessor mixin = (LivingEntityAccessor)localPlayer;
            mixin.hMI5_0$resetMainHandSwing(false);
        }
    }

    @Redirect(method={"startUseItem"}, at=@At(value="INVOKE", target="Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z"))
    private boolean doItemUse(LocalPlayer instance, InteractionHand hand, SwingAnimation animation, boolean updateOthers) {
        if (instance instanceof LivingEntityAccessor) {
            LivingEntityAccessor accessor = (LivingEntityAccessor)instance;
            if (hand == InteractionHand.MAIN_HAND) {
                accessor.hMI5_0$resetMainHandSwing(true);
            } else {
                accessor.hMI5_0$resetOffHandSwing(true);
            }
        }
        return instance.swing(hand, animation, updateOthers);
    }
}
