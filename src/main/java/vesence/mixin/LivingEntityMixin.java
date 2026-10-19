package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
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
   private final MinecraftClient client = MinecraftClient.getInstance();
   @Unique
   private static int jumpTicks = 0;

   @Shadow
   public abstract boolean hasStatusEffect(RegistryEntry<StatusEffect> var1);

   @Shadow
   @Nullable
   public abstract StatusEffectInstance getStatusEffect(RegistryEntry<StatusEffect> var1);

   @Inject(method = { "jump" }, at = { @At("HEAD") })
   public void jumpYo(CallbackInfo ci) {
      LivingEntity self = (LivingEntity) (Object) this;
      if (self instanceof ClientPlayerEntity) {
         EventManager.call(new EventJump());
      }
   }

    @Inject(method = { "getHandSwingDuration" }, at = { @At("HEAD") }, cancellable = true)
    private void swingProgressHook(CallbackInfoReturnable<Integer> cir) {
        if (!Vesence.isModInitialized()) return;

        MinecraftClient localClient = MinecraftClient.getInstance();
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
        if (!(self instanceof ClientPlayerEntity)) return;

        vesence.module.impl.movement.NoPush noPush = vesence.module.impl.movement.NoPush.getInstance();
        if (noPush == null || !noPush.enable) return;

        if (entity instanceof PlayerEntity && noPush.shouldIgnorePlayers()) {
            ci.cancel();
            return;
        }

        if (entity instanceof net.minecraft.entity.projectile.FishingBobberEntity && noPush.shouldIgnoreFishing()) {
            ci.cancel();
        }
    }

    @Unique
    private Vec3d calcGlidingVelocity(Vec3d oldVelocity) {
        return oldVelocity;
    }

    @Inject(method = "calcGlidingVelocity", at = @At("RETURN"), cancellable = true)
    private void onCalcGlidingVelocity(Vec3d oldVelocity, CallbackInfoReturnable<Vec3d> cir) {
        if (!Vesence.isModInitialized()) return;

        Vec3d result = cir.getReturnValue();
        EventOnTravelPost event = new EventOnTravelPost(
                result.multiply(0.9900000095367432, 0.9800000190734863, 0.9900000095367432)
        );
        EventManager.call(event);
        cir.setReturnValue(event.getOldVelocity());
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void onTravelTail(Vec3d movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isGliding() && self instanceof ClientPlayerEntity) {
            Vec3d v = self.getVelocity();
            v = calcGlidingVelocity(v);
            self.setVelocity(v);
        }
    }
}
