package vesence.mixin;

import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import vesence.access.ILivingEntityRendererAccess;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererAccessor implements ILivingEntityRendererAccess {

    @Shadow
    protected abstract void setupTransforms(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float scale);

    @Shadow
    protected abstract void scale(LivingEntityRenderState state, MatrixStack matrices);

    @Override
    public void vesence$invokeSetupTransforms(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float scale) {
        this.setupTransforms(state, matrices, bodyYaw, scale);
    }

    @Override
    public void vesence$invokeScale(LivingEntityRenderState state, MatrixStack matrices) {
        this.scale(state, matrices);
    }
}
