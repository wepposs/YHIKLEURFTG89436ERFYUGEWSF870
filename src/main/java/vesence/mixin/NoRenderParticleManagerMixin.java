package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;

@Environment(EnvType.CLIENT)
@Mixin(ParticleManager.class)
public class NoRenderParticleManagerMixin {
    @Inject(method = "addEmitter(Lnet/minecraft/entity/Entity;Lnet/minecraft/particle/ParticleEffect;I)V", at = @At("HEAD"), cancellable = true)
    private void onAddEmitter(net.minecraft.entity.Entity entity, net.minecraft.particle.ParticleEffect parameters, int maxAge, CallbackInfo ci) {
        if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Партиклы тотема") && parameters.getType() == net.minecraft.particle.ParticleTypes.TOTEM_OF_UNDYING) {
            ci.cancel();
        }
    }

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void vesence$onAddParticle(ParticleEffect parameters, double x, double y, double z,
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

