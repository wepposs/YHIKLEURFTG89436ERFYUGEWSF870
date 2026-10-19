package vesence.access;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;

public interface ILivingEntityRendererAccess {
    void vesence$invokeSetupTransforms(LivingEntityRenderState state, PoseStack matrices, float bodyYaw, float scale);
    void vesence$invokeScale(LivingEntityRenderState state, PoseStack matrices);
}
