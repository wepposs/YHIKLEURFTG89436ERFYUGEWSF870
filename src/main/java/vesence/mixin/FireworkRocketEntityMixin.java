package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.player.EventElytraBoost;

@Environment(EnvType.CLIENT)
@Mixin(FireworkRocketEntity.class)
public class FireworkRocketEntityMixin {

    @ModifyVariable(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"),
            ordinal = 0
    )
    private Vec3 modifyFireworkBoost(Vec3 original) {
        if (!Vesence.isModInitialized()) {
            return original;
        }

        EventElytraBoost event = new EventElytraBoost(original);
        EventManager.call(event);
        return event.getBoost();
    }
}
