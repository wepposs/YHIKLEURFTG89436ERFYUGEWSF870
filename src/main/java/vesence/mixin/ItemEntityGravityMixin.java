package vesence.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityGravityMixin {
    private static final double STOP_EPSILON = 0.003;

    @Inject(method = "tick", at = @At("TAIL"))
    private void vesence$stabilizeGroundItems(CallbackInfo ci) {
        ItemEntity item = (ItemEntity) (Object) this;
        if (item.level() == null || !item.level().isClientSide()) {
            return;
        }
        if (item.onGround()) {
            item.setNoGravity(true);
            Vec3 velocity = item.getDeltaMovement();
            double vx = Math.abs(velocity.x) <= STOP_EPSILON ? 0.0 : velocity.x * 0.9;
            double vz = Math.abs(velocity.z) <= STOP_EPSILON ? 0.0 : velocity.z * 0.9;
            double vy = velocity.y > 0.0 || Math.abs(velocity.y) <= 0.06 ? 0.0 : velocity.y * 0.5;
            item.setDeltaMovement(vx, vy, vz);
            return;
        }
        if (item.isNoGravity()) {
            item.setNoGravity(false);
        }
    }
}
