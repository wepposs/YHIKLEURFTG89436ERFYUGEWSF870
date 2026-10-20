package vesence.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import vesence.access.ILivingEntityRendererAccess;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererAccessor implements ILivingEntityRendererAccess {

    @Shadow
    protected abstract void setupRotations(LivingEntityRenderState state, PoseStack matrices, float bodyYaw, float scale);

    @Shadow
    protected abstract void scale(LivingEntityRenderState state, PoseStack matrices);

    @Override
    public void vesence$invokeSetupTransforms(LivingEntityRenderState state, PoseStack matrices, float bodyYaw, float scale) {
        this.setupRotations(state, matrices, bodyYaw, scale);
    }

    @Override
    public void vesence$invokeScale(LivingEntityRenderState state, PoseStack matrices) {
        this.scale(state, matrices);
    }
}
