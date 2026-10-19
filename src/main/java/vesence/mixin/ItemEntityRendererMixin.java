package vesence.mixin;

import java.util.HashMap;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.math.random.Random;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.accessor.IItemEntityRenderState;
import vesence.module.impl.visuals.ItemPhysic;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin extends EntityRenderer<ItemEntity, ItemEntityRenderState> {

    @Shadow
    @Final
    private Random random;

    @Unique
    private static final HashMap<Integer, Integer> vesence$groundHoldMap = new HashMap<>();

    protected ItemEntityRendererMixin(EntityRendererFactory.Context context) {
        super(context);
    }

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/ItemEntity;Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void onUpdateRenderState(ItemEntity entity, ItemEntityRenderState state, float tickDelta, CallbackInfo ci) {
        boolean stableGround = vesence$resolveGroundState(entity, entity.onGround());
        ((IItemEntityRenderState) state).setOnGround(stableGround);
    }

    @Inject(method = "render(Lnet/minecraft/client/render/entity/state/ItemEntityRenderState;Lnet/minecraft/client/util/math/PoseStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/render/state/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onRender(ItemEntityRenderState state, PoseStack matrixStack, OrderedRenderCommandQueue orderedRenderCommandQueue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (Vesence.get != null && Vesence.get.manager != null) {
            ItemPhysic itemPhysic = Vesence.get.manager.get(ItemPhysic.class);
            if (itemPhysic != null && itemPhysic.enable) {
                if (state.itemRenderState.isEmpty()) {
                    ci.cancel();
                    return;
                }
                matrixStack.pushPose();
                AABB box = state.itemRenderState.getModelBoundingBox();
                float f6 = (float) -box.minY;

                matrixStack.translate(0.0f, f6, 0.0f);

                float itemRotation = ItemEntity.getRotation(state.age, state.uniqueOffset);
                boolean onGround = ((IItemEntityRenderState) state).onGround();

                if (onGround) {
                    matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
                    matrixStack.multiply(RotationAxis.POSITIVE_Z.rotation(itemRotation));
                } else {
                    matrixStack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(itemRotation * 300.0f));
                    matrixStack.multiply(RotationAxis.POSITIVE_Y.rotation(itemRotation));
                }

                ItemEntityRenderer.render(matrixStack, orderedRenderCommandQueue, state.light, state, this.random, box);

                matrixStack.popPose();

                super.render(state, matrixStack, orderedRenderCommandQueue, cameraRenderState);

                ci.cancel();
            }
        }
    }

    @Unique
    private static boolean vesence$resolveGroundState(ItemEntity entity, boolean onGround) {
        int entityId = entity.getId();
        if (onGround) {
            vesence$groundHoldMap.put(entityId, 8);
            if (vesence$groundHoldMap.size() > 4096) {
                vesence$groundHoldMap.clear();
            }
            return true;
        }
        Integer holdTicks = vesence$groundHoldMap.get(entityId);
        if (holdTicks == null || holdTicks <= 0) {
            return false;
        }
        Vec3 velocity = entity.getDeltaMovement();
        boolean lowMotion = Math.abs(velocity.x) <= 0.08 && Math.abs(velocity.y) <= 0.08 && Math.abs(velocity.z) <= 0.08;
        if (!lowMotion) {
            vesence$groundHoldMap.remove(entityId);
            return false;
        }
        if (holdTicks == 1) {
            vesence$groundHoldMap.remove(entityId);
        } else {
            vesence$groundHoldMap.put(entityId, holdTicks - 1);
        }
        return true;
    }
}
