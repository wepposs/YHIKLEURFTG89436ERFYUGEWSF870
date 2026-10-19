package vesence.access;

import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;

public interface ILivingEntityRendererAccess {
    void vesence$invokeSetupTransforms(LivingEntityRenderState state, MatrixStack matrices, float bodyYaw, float scale);
    void vesence$invokeScale(LivingEntityRenderState state, MatrixStack matrices);
}
