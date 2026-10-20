package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({LivingEntity.class})
public interface LivingEntityAccessor {
    @Accessor("noJumpDelay")
    int getJumpingCooldown();

    @Accessor("noJumpDelay")
    void setJumpingCooldown(int value);
}
