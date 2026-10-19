package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.player.EventOnMovePost;

@Environment(EnvType.CLIENT)
@Mixin(Entity.class)
public abstract class EntityMixin {

    // require = 0 → если метод не найдётся, Mixin не рушит игру, просто пропустит
    @Inject(method = "isInvisibleTo", at = @At("HEAD"), cancellable = true, require = 0)
    private void onIsInvisibleTo(PlayerEntity player, CallbackInfoReturnable<Boolean> cir) {
        try {
            if (!Vesence.isModInitialized()) return;
            var module = Vesence.get.manager.get(vesence.module.impl.visuals.SeeInvisibles.class);
            if (module == null || !module.enable) return;

            Entity self = (Entity) (Object) this;
            if (self instanceof PlayerEntity
                    && self != MinecraftClient.getInstance().player) {
                cir.setReturnValue(false);
            }
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "getTargetingMargin", at = @At("RETURN"), cancellable = true, require = 0)
    private void client$getTargetingMargin(CallbackInfoReturnable<Float> cir) {
        // пусто — тут ничего не делалось и раньше
    }

    @Inject(method = "updateVelocity", at = @At("TAIL"), require = 0)
    private void onUpdateVelocity(float speed, Vec3d movementInput, CallbackInfo ci) {
        try {
            Entity self = (Entity) (Object) this;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null && self.getId() == mc.player.getId()) {
                EventManager.call(new EventOnMovePost(speed, movementInput));
            }
        } catch (Throwable ignored) {
        }
    }
}