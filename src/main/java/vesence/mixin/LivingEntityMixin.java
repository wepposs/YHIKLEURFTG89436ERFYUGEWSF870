package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.render.SwingDurationEvent;
import vesence.event.player.EventJump;
import vesence.event.player.EventOnTravelPost;

@Environment(EnvType.CLIENT)
@Mixin({ LivingEntity.class })
public abstract class LivingEntityMixin {
   @Unique
   private final Minecraft client = Minecraft.getInstance();
   @Unique
   private static int jumpTicks = 0;

   @Shadow
   public abstract boolean hasEffect(Holder<MobEffect> var1);

   @Shadow
   @Nullable
   public abstract MobEffectInstance getEffect(Holder<MobEffect> var1);

   @Inject(method = { "jumpFromGround" }, at = { @At("HEAD") })
   public void jumpYo(CallbackInfo ci) {
      LivingEntity self = (LivingEntity) (Object) this;
      if (self instanceof LocalPlayer) {
         EventManager.call(new EventJump());
      }
   }

    @Inject(method = { "getModifiedSwingDuration" }, at = { @At("HEAD") }, cancellable = true)
    private void swingProgressHook(net.minecraft.world.item.component.SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
        if (!Vesence.isModInitialized()) return;

        Minecraft localClient = Minecraft.getInstance();
        if (localClient == null || localClient.player == null) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (self != localClient.player) return;

        SwingDurationEvent event = new SwingDurationEvent();
        EventManager.call(event);

        if (event.isCancelled()) {
            int duration = (int) Math.max(1.0F, event.getAnimation() * 6.0F);
            cir.setReturnValue(duration);
        }
    }

    // 26.3: LivingEntity#tickMovement отсутствует, а модификатор jumpTicks был no-op
    // (возвращал исходное значение), поэтому удалён без потери поведения.

    @Inject(method = { "push" }, at = { @At("HEAD") }, cancellable = true)
    private void onPushAwayFrom(Entity entity, CallbackInfo ci) {
        if (!Vesence.isModInitialized()) return;

        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof LocalPlayer)) return;

        vesence.module.impl.movement.NoPush noPush = vesence.module.impl.movement.NoPush.getInstance();
        if (noPush == null || !noPush.enable) return;

        if (entity instanceof Player && noPush.shouldIgnorePlayers()) {
            ci.cancel();
            return;
        }

        if (entity instanceof net.minecraft.world.entity.projectile.FishingHook && noPush.shouldIgnoreFishing()) {
            ci.cancel();
        }
    }

    /**
     * Пост-обработка движения планирующего игрока.
     *
     * <p>В 1.21.x это был хук на {@code LivingEntity#calcGlidingVelocity}, где событие
     * {@link EventOnTravelPost} могло переписать скорость. В 26.3 такого метода нет —
     * скорость планирования считается внутри {@code travelInAir}, — поэтому хук
     * перенесён на TAIL {@code travel}: те же множители (0.99/0.98/0.99) применяются
     * к итоговой дельте, а событие по-прежнему может её заменить.
     */
    @Inject(method = "travel", at = @At("TAIL"))
    private void onTravelTail(Vec3 movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isFallFlying() || !(self instanceof LocalPlayer)) {
            return;
        }
        if (!Vesence.isModInitialized()) {
            return;
        }

        // EventOnTravelPost пока не имеет ни одного подписчика — хук
        // сохранён, чтобы восстановить поведение модов на планирование без переписывания
        // миксина. Множители взяты из прежнего calcGlidingVelocity.
        EventOnTravelPost event = new EventOnTravelPost(
                self.getDeltaMovement().multiply(0.9900000095367432, 0.9800000190734863, 0.9900000095367432)
        );
        EventManager.call(event);
        self.setDeltaMovement(event.getOldVelocity());
    }
}
