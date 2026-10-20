package vesence.hmi.mixin;

import vesence.hmi.LuaTestHMI;
import vesence.hmi.access.ItemStackAccessor;
import vesence.hmi.access.LivingEntityAccessor;
import vesence.hmi.patricles.Particle;
import vesence.hmi.patricles.ParticleRenderManager;
import vesence.hmi.resource_controller.LuaScriptCache;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.player.AbstractClientPlayer;
import com.mojang.math.Axis;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.component.DataComponents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Порт HMI HeldItemRendererMixin под 26.3.
 *
 * В 1.21.6 HMI рендерил первое лицо через MultiBufferSource. В 26.3
 * HeldItemRenderer заменён на FirstPersonHandsAndItemsRenderer, а рендер идёт
 * через SubmitNodeCollector (submitArmWithItem / submitItem / submitBlockModel).
 * Логика поз/анимаций (вызовы Lua-скриптов hand/handRelative/item)
 * сохранена идентично оригиналу; изменён только способ отправки геометрии в рендер.
 */
@Mixin(value = {FirstPersonHandsAndItemsRenderer.class})
public abstract class HeldItemRendererMixin {
    @Unique
    private final ArrayList<Particle> hmi$particles = new ArrayList<>();
    @Unique
    private final HashMap<String, Object> hmi$registry = new HashMap<>();
    @Unique
    boolean hmi$mainHandSwitchEvent = false;
    @Unique
    boolean hmi$offHandSwitchEvent = false;
    @Unique
    Item hmi$prevMainHand = Items.AIR;
    @Unique
    Item hmi$prevOffHand = Items.AIR;
    @Shadow
    @Final
    private Minecraft minecraft;
    @Unique
    private float hmi$mainHandSwingProgress = 0.0f;
    @Unique
    private float hmi$offHandSwingProgress = 0.0f;
    @Unique
    private boolean hmi$swingMHand = false;
    @Unique
    private boolean hmi$swingOHand = false;
    @Unique
    private BlockModelResolver hmi$blockModelResolver;

    @Shadow
    private void renderPlayerArm(PoseStack matrices, SubmitNodeCollector queue, int light, float equipProgress, float swingProgress, HumanoidArm arm, PlayerRenderState playerState) {
        throw new AssertionError();
    }

    @Shadow
    private void submitArmWithItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light) {
        throw new AssertionError();
    }

    @Inject(
            method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"},
            at = {@At("HEAD")}
    )
    private void hmi$updateDeltaTime(float tickProgress, PoseStack matrices, SubmitNodeCollector queue, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState, CallbackInfo ci) {
        LuaTestHMI.updateDeltaTime();
    }

    @Unique
    private void hmi$copyAppearanceComponents(ItemStack source, ItemStack target) {
        if (source.hasNonDefault(DataComponents.ENCHANTMENTS)) {
            target.set(DataComponents.ENCHANTMENTS, (ItemEnchantments) source.get(DataComponents.ENCHANTMENTS));
        }
        if (source.hasNonDefault(DataComponents.ITEM_MODEL)) {
            target.set(DataComponents.ITEM_MODEL, (Identifier) source.get(DataComponents.ITEM_MODEL));
        }
        if (source.hasNonDefault(DataComponents.CUSTOM_MODEL_DATA)) {
            target.set(DataComponents.CUSTOM_MODEL_DATA, (CustomModelData) source.get(DataComponents.CUSTOM_MODEL_DATA));
        }
        if (source.hasNonDefault(DataComponents.CUSTOM_DATA)) {
            target.set(DataComponents.CUSTOM_DATA, (CustomData) source.get(DataComponents.CUSTOM_DATA));
        }
    }

    @Unique
    private void hmi$applyArmMatrices(PoseStack matrices, int light, float equipProgress, float swingProgress, HumanoidArm arm) {
        boolean bl = arm != HumanoidArm.LEFT;
        float f = bl ? 1.0f : -1.0f;
        float g = Mth.sqrt(swingProgress);
        float h = -0.3f * Mth.sin(g * (float) Math.PI);
        float i = 0.4f * Mth.sin(g * ((float) Math.PI * 2));
        float j = -0.4f * Mth.sin(swingProgress * (float) Math.PI);
        matrices.translate(f * (h + 0.64000005f), i + -0.6f + equipProgress * -0.6f, j + -0.71999997f);
        matrices.rotate(Axis.YP.rotationDegrees(f * 45.0f));
        float k = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float l = Mth.sin(g * (float) Math.PI);
        matrices.rotate(Axis.YP.rotationDegrees(f * l * 70.0f));
        matrices.rotate(Axis.ZP.rotationDegrees(f * k * -20.0f));
        matrices.translate(f * -1.0f, 3.6f, 3.5f);
        matrices.rotate(Axis.ZP.rotationDegrees(f * 120.0f));
        matrices.rotate(Axis.XP.rotationDegrees(200.0f));
        matrices.rotate(Axis.YP.rotationDegrees(f * -135.0f));
        matrices.translate(f * 5.6f, 0.0f, 0.0f);
    }

    @Unique
    private void hmi$itemPose(PoseStack matrices, ItemStack item, boolean bl, float swingProgress, AbstractClientPlayer player, boolean mainHand, InteractionHand hand, float equipProgress, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        matrices.translate(0.5 * (bl ? 1 : -1), -0.15, -0.85);
        matrices.rotateAround(Axis.XP.rotationDegrees(15.0f), 0.5f, 0.5f, 0.5f);
        matrices.scale(0.9f, 0.9f, 0.9f);
        LuaTestHMI.itemScriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : LuaTestHMI.itemAddonsCache) {
            scriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
    }

    @Unique
    private void hmi$mainHandPose(PoseStack matrices, ItemStack item, boolean bl, float swingProgress, float equipProgress, AbstractClientPlayer player, boolean mainHand, InteractionHand hand, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        int l = bl ? 1 : -1;
        LuaTestHMI.handRelativeScriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : LuaTestHMI.handRelativeAddonsCache) {
            scriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
        if (!item.isEmpty()) {
            matrices.translate(1.5 * l, -0.3, -0.6);
            matrices.rotateAround(Axis.XP.rotationDegrees(15.0f), 0.5f * l, 0.5f, 0.5f);
            matrices.rotateAround(Axis.YP.rotationDegrees(35 * l), 0.5f * l, 0.5f, 0.5f);
            matrices.rotateAround(Axis.ZP.rotationDegrees(-65 * l), 0.5f * l, 0.5f, 0.5f);
            matrices.scale(0.9f, 0.9f, 0.9f);
        }
    }

    @Unique
    private void hmi$scenePoseMain(PoseStack matrices, ItemStack item, boolean bl, float swingProgress, float equipProgress, AbstractClientPlayer player, boolean mainHand, InteractionHand hand, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        LuaTestHMI.handScriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : LuaTestHMI.handAddonsCache) {
            scriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
        if (!item.isEmpty()) {
            matrices.translate(0.0, -0.35, 0.2);
        }
    }

    @Unique
    private void hmi$renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, PoseStack matrices, SubmitNodeCollector queue, int light) {
        // TODO(port26): was HeldItemRenderer.renderItem(entity, stack, context, matrices, queue, light)
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(state, stack, context, entity);
        state.submit(matrices, queue, light, OverlayTexture.NO_OVERLAY, 0);
    }

    @Unique
    private void hmi$submitBlock(PoseStack matrices, BlockState blockState, int light, SubmitNodeCollector queue) {
        // TODO(port26): was OrderedRenderCommandQueue.submitBlock(matrices, blockState, light, overlay, outlineColor)
        if (this.hmi$blockModelResolver == null) {
            this.hmi$blockModelResolver = new BlockModelResolver(this.minecraft.getModelManager());
        }
        BlockModelRenderState state = new BlockModelRenderState();
        this.hmi$blockModelResolver.update(state, blockState, BlockDisplayContext.create());
        state.submit(matrices, queue, light, OverlayTexture.NO_OVERLAY, 0);
    }

    @Redirect(
            method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V")
    )
    @SuppressWarnings("StringEquality")
    private void hmi$renderOverhaul(FirstPersonHandsAndItemsRenderer instance, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector queue, int light) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        // Если модуль "Living Hands" выключен или игрока нет — обычный ванильный рендер руки.
        if (!vesence.module.impl.visuals.LivingHands.isEnabled() || player == null) {
            this.submitArmWithItem(playerState, handsState, tickProgress, pitch, hand, swingProgress, item, equipProgress, matrices, queue, light);
            return;
        }
        if (player.isScoping()) {
            return;
        }
        ((ItemStackAccessor) (Object) item).hMI5_0$setTransform(-1);
        boolean bl = hand == InteractionHand.MAIN_HAND;
        boolean interact = false;
        boolean blockBreaking = false;
        if (bl && player.isUsingItem() && player.getUsedItemHand() != hand && (player.getOffhandItem().getUseAnimation() == ItemUseAnimation.BOW || player.getOffhandItem().getUseAnimation() == ItemUseAnimation.CROSSBOW && !CrossbowItem.isCharged(player.getOffhandItem()))) {
            item = Items.AIR.getDefaultInstance();
        }
        if (!bl && player.isUsingItem() && player.getUsedItemHand() != hand && (player.getMainHandItem().getUseAnimation() == ItemUseAnimation.BOW || player.getMainHandItem().getUseAnimation() == ItemUseAnimation.CROSSBOW && !CrossbowItem.isCharged(player.getMainHandItem()))) {
            item = Items.AIR.getDefaultInstance();
        }
        if (bl) {
            this.hmi$mainHandSwitchEvent = item.getItem() != this.hmi$prevMainHand;
        } else {
            this.hmi$offHandSwitchEvent = item.getItem() != this.hmi$prevOffHand;
        }
        matrices.pushPose();
        if (player instanceof LivingEntityAccessor) {
            LivingEntityAccessor accessor = (LivingEntityAccessor) player;
            this.hmi$mainHandSwingProgress = accessor.hMI5_0$getMainHandSwingProgress(tickProgress);
            this.hmi$offHandSwingProgress = accessor.hMI5_0$getOffHandSwingProgress(tickProgress);
            swingProgress = bl ? this.hmi$mainHandSwingProgress : this.hmi$offHandSwingProgress;
            this.hmi$swingMHand = accessor.hMI5_0$getMHandEvent();
            this.hmi$swingOHand = accessor.hMI5_0$getOHandEvent();
            interact = bl ? accessor.hMI5_0$getMInteract() : accessor.hMI5_0$getOInteract();
            blockBreaking = accessor.hMI5_0$getBlockBreak();
        }
        HumanoidArm arm = bl ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean bl2 = arm == HumanoidArm.RIGHT;
        int l = bl2 ? 1 : -1;
        this.hmi$scenePoseMain(matrices, item, bl2, swingProgress, equipProgress, player, bl, hand, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
        matrices.pushPose();
        this.hmi$mainHandPose(matrices, item, bl2, swingProgress, equipProgress, player, bl, hand, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
        int combinedLight = LightCoordsUtil.lightCoordsWithEmission(light, Block.byItem(item.getItem()).defaultBlockState().getLightEmission());
        if (player.isInvisible()) {
            this.hmi$applyArmMatrices(matrices, combinedLight, 0.0f, 0.0f, arm);
        } else {
            this.renderPlayerArm(matrices, queue, combinedLight, 0.0f, 0.0f, arm, playerState);
        }
        matrices.popPose();
        matrices.pushPose();
        boolean renderAsBlock = !(Block.byItem(item.getItem()) == Blocks.AIR
                || Block.byItem(item.getItem()).defaultBlockState().is(BlockTags.INSIDE_STEP_SOUND_BLOCKS)
                || Block.byItem(item.getItem()).defaultBlockState().is(BlockTags.CROPS)
                || item.getUseAnimation() == ItemUseAnimation.EAT
                || item.is(Items.REDSTONE)
                || item.is(ItemTags.BANNERS)
                || item.is(ItemTags.SKULLS)
                || !LuaTestHMI.renderAsBlock.getOrDefault(item.getItem().toString(), true));
        if (renderAsBlock) {
            swingProgress = 0.0f;
            BlockState blockState = Block.byItem(item.getItem()).defaultBlockState();
            this.hmi$itemPose(matrices, item, bl2, swingProgress, player, bl, hand, equipProgress, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
            matrices.translate(0.22 * l, 0.25, 0.2);
            if (item.is(Items.LEVER) || blockState.is(BlockTags.BUTTONS)) {
                blockState = blockState.setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR);
            }
            if (item.getHoverName().toString().toLowerCase().contains("torch") || item.is(Items.LANTERN) || item.is(Items.SOUL_LANTERN) || blockState.is(BlockTags.ALL_HANGING_SIGNS)) {
                matrices.translate(-0.05 * l, 0.0, 0.0);
                matrices.scale(1.75f, 1.75f, 1.75f);
            } else {
                matrices.translate(-0.25 * l, -0.05, 0.0);
            }
            matrices.pushPose();
            matrices.scale(0.3f, 0.3f, 0.3f);
            matrices.translate(-0.9 * l, -0.45, -0.7);
            matrices.popPose();
            if (!bl2) {
                matrices.translate(-0.3f, 0.0f, 0.0f);
            }
            matrices.scale(0.3f, 0.3f, 0.3f);
            matrices.translate(-0.9 * l, -0.45, -0.7);
            if (item.is(Items.BELL)) {
                // В 26.3 рендер BlockEntity первого лица требует CameraRenderState из пайплайна;
                // вместо отдельного BlockEntity рендерим колокол как обычный подвешенный блок через очередь.
                this.hmi$submitBlock(matrices, Blocks.BELL.defaultBlockState().setValue(BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING), light, queue);
            } else {
                if (blockState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
                    matrices.pushPose();
                    matrices.translate(0.0f, 1.0f, 0.0f);
                    this.hmi$submitBlock(matrices, blockState.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), light, queue);
                    matrices.popPose();
                }
                this.hmi$submitBlock(matrices, blockState, light, queue);
            }
            matrices.pushPose();
            if (!bl2) {
                matrices.translate(1.0f, 0.0f, 0.0f);
            }
            ParticleRenderManager.draw(this.hmi$particles, matrices, queue, "ITEM", hand, light, player);
            matrices.popPose();
        } else {
            this.hmi$itemPose(matrices, item, bl2, swingProgress, player, bl, hand, equipProgress, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
            if (item.getUseAnimation() == ItemUseAnimation.BLOCK || item.getUseAnimation() == ItemUseAnimation.BRUSH) {
                ItemStack renderStack = new ItemStack((ItemLike) item.getItem(), item.getCount());
                this.hmi$copyAppearanceComponents(item, renderStack);
                this.hmi$renderItem((LivingEntity) player, renderStack, bl2 ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, matrices, queue, light);
            } else {
                this.hmi$renderItem((LivingEntity) player, item, bl2 ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, matrices, queue, light);
            }
            matrices.pushPose();
            ParticleRenderManager.draw(this.hmi$particles, matrices, queue, "ITEM", hand, light, player);
            matrices.popPose();
        }
        matrices.popPose();
        matrices.popPose();
        if (bl) {
            this.hmi$prevMainHand = item.getItem();
        } else {
            this.hmi$prevOffHand = item.getItem();
        }
        ParticleRenderManager.draw(this.hmi$particles, matrices, queue, "SCREEN", hand, light, player);
    }
}
