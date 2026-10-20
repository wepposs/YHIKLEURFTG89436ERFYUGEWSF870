package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(ParticleEngine.class)
public class NoRenderParticleManagerMixin {
    @Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V", at = @At("HEAD"), cancellable = true)
    private void onAddEmitter(net.minecraft.world.entity.Entity entity, net.minecraft.core.particles.ParticleOptions parameters, int maxAge, CallbackInfo ci) {
        if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Партиклы тотема") && parameters.getType() == net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING) {
            ci.cancel();
        }
    }

    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void vesence$onAddParticle(ParticleOptions parameters, double x, double y, double z,
                                       double vx, double vy, double vz, CallbackInfoReturnable<Particle> cir) {
        if (parameters == null) return;
        // Все частицы
        if (NoRender.isElementActive("Частицы")) {
            cir.setReturnValue(null);
            return;
        }
        // Только частицы удара (крит/sweep)
        if (NoRender.isElementActive("Частицы удара")) {
            ParticleType<?> type = parameters.getType();
            if (type == ParticleTypes.CRIT || type == ParticleTypes.ENCHANTED_HIT || type == ParticleTypes.SWEEP_ATTACK) {
                cir.setReturnValue(null);
            }
        }
    }
}

