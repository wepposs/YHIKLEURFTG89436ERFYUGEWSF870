package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
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
                    target = "Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"),
            ordinal = 0
    )
    private Vec3d modifyFireworkBoost(Vec3d original) {
        if (!Vesence.isModInitialized()) {
            return original;
        }

        EventElytraBoost event = new EventElytraBoost(original);
        EventManager.call(event);
        return event.getBoost();
    }
}
