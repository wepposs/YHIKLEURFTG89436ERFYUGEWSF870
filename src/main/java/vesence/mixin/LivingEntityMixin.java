package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.entity.effect.StatusEffectUtil;
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
   public abstract boolean hasStatusEffect(Holder<MobEffect> var1);

   @Shadow
   @Nullable
   public abstract MobEffectInstance getStatusEffect(Holder<MobEffect> var1);

   @Inject(method = { "jump" }, at = { @At("HEAD") })
   public void jumpYo(CallbackInfo ci) {
      LivingEntity self = (LivingEntity) (Object) this;
      if (self instanceof LocalPlayer) {
         EventManager.call(new EventJump());
      }
   }

    @Inject(method = { "getHandSwingDuration" }, at = { @At("HEAD") }, cancellable = true)
    private void swingProgressHook(CallbackInfoReturnable<Integer> cir) {
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

    @ModifyConstant(method = { "tickMovement" }, constant = { @Constant(intValue = 10) })
    private int modifyJumpTicks(int original) {
        return original;
    }

    @Inject(method = { "pushAwayFrom" }, at = { @At("HEAD") }, cancellable = true)
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

        if (entity instanceof net.minecraft.entity.projectile.FishingBobberEntity && noPush.shouldIgnoreFishing()) {
            ci.cancel();
        }
    }

    @Unique
    private Vec3 calcGlidingVelocity(Vec3 oldVelocity) {
        return oldVelocity;
    }

    @Inject(method = "calcGlidingVelocity", at = @At("RETURN"), cancellable = true)
    private void onCalcGlidingVelocity(Vec3 oldVelocity, CallbackInfoReturnable<Vec3> cir) {
        if (!Vesence.isModInitialized()) return;

        Vec3 result = cir.getReturnValue();
        EventOnTravelPost event = new EventOnTravelPost(
                result.multiply(0.9900000095367432, 0.9800000190734863, 0.9900000095367432)
        );
        EventManager.call(event);
        cir.setReturnValue(event.getOldVelocity());
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void onTravelTail(Vec3 movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isGliding() && self instanceof LocalPlayer) {
            Vec3 v = self.getDeltaMovement();
            v = calcGlidingVelocity(v);
            self.setDeltaMovement(v);
        }
    }
}
