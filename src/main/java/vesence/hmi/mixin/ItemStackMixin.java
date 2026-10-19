package vesence.hmi.mixin;

import vesence.hmi.access.ItemStackAccessor;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value={ItemStack.class})
public class ItemStackMixin
implements ItemStackAccessor {
    @Unique
    private int transform = -1;
    @Unique
    private int swingSpeed = 10;
    @Unique
    private int shouldRenderAsBlock = -1;

    public void hMI5_0$setTransform(boolean value) {
        this.transform = value ? 1 : 0;
    }

    public void hMI5_0$setTransform(int value) {
        this.transform = value;
    }

    public int hMI5_0$getTransform() {
        return this.transform;
    }

    public void hMI5_0$setSwingSpeed(int value) {
        this.swingSpeed = value;
    }

    public int hMI5_0$getSwingSpeed() {
        return this.swingSpeed;
    }

    public void hMI5_0$setRenderAsBlock(boolean value) {
        this.shouldRenderAsBlock = value ? 1 : 0;
    }

    public int hMI5_0$getRenderAsBlock() {
        return 0;
    }
}

