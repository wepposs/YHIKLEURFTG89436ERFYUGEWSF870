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
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.consume.UseAction;
import net.minecraft.item.ItemConvertible;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.state.property.Properties;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.MathHelper;
import net.minecraft.block.enums.Attachment;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.CustomModelDataComponent;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.DataComponentTypes;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Порт HMI HeldItemRendererMixin под 1.21.11.
 *
 * В 1.21.6 HMI рендерил первое лицо через VertexConsumerProvider. В 1.21.11
 * рендер первого лица идёт через OrderedRenderCommandQueue (submitBlock / submitItem
 * / submitCustom). Логика поз/анимаций (вызовы Lua-скриптов hand/handRelative/item)
 * сохранена идентично оригиналу; изменён только способ отправки геометрии в рендер.
 */
@Mixin(value = {HeldItemRenderer.class})
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
    private MinecraftClient client;
    @Unique
    private float hmi$mainHandSwingProgress = 0.0f;
    @Unique
    private float hmi$offHandSwingProgress = 0.0f;
    @Unique
    private boolean hmi$swingMHand = false;
    @Unique
    private boolean hmi$swingOHand = false;

    @Shadow
    protected abstract void renderArmHoldingItem(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, float equipProgress, float swingProgress, Arm arm);

    @Shadow
    protected abstract void renderFirstPersonItem(AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light);

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, MatrixStack matrices, OrderedRenderCommandQueue queue, int light);

    @Inject(
            method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
            at = {@At("HEAD")}
    )
    private void hmi$updateDeltaTime(float tickProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, ClientPlayerEntity player, int light, CallbackInfo ci) {
        LuaTestHMI.updateDeltaTime();
    }

    @Unique
    private void hmi$copyAppearanceComponents(ItemStack source, ItemStack target) {
        if (source.contains(DataComponentTypes.ENCHANTMENTS)) {
            target.set(DataComponentTypes.ENCHANTMENTS, (ItemEnchantmentsComponent) source.get(DataComponentTypes.ENCHANTMENTS));
        }
        if (source.contains(DataComponentTypes.ITEM_MODEL)) {
            target.set(DataComponentTypes.ITEM_MODEL, (Identifier) source.get(DataComponentTypes.ITEM_MODEL));
        }
        if (source.contains(DataComponentTypes.CUSTOM_MODEL_DATA)) {
            target.set(DataComponentTypes.CUSTOM_MODEL_DATA, (CustomModelDataComponent) source.get(DataComponentTypes.CUSTOM_MODEL_DATA));
        }
        if (source.contains(DataComponentTypes.CUSTOM_DATA)) {
            target.set(DataComponentTypes.CUSTOM_DATA, (NbtComponent) source.get(DataComponentTypes.CUSTOM_DATA));
        }
    }

    @Unique
    private void hmi$applyArmMatrices(MatrixStack matrices, int light, float equipProgress, float swingProgress, Arm arm) {
        boolean bl = arm != Arm.LEFT;
        float f = bl ? 1.0f : -1.0f;
        float g = MathHelper.sqrt(swingProgress);
        float h = -0.3f * MathHelper.sin(g * (float) Math.PI);
        float i = 0.4f * MathHelper.sin(g * ((float) Math.PI * 2));
        float j = -0.4f * MathHelper.sin(swingProgress * (float) Math.PI);
        matrices.translate(f * (h + 0.64000005f), i + -0.6f + equipProgress * -0.6f, j + -0.71999997f);
        matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_Y.rotationDegrees(f * 45.0f));
        float k = MathHelper.sin(swingProgress * swingProgress * (float) Math.PI);
        float l = MathHelper.sin(g * (float) Math.PI);
        matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_Y.rotationDegrees(f * l * 70.0f));
        matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_Z.rotationDegrees(f * k * -20.0f));
        matrices.translate(f * -1.0f, 3.6f, 3.5f);
        matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_Z.rotationDegrees(f * 120.0f));
        matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_X.rotationDegrees(200.0f));
        matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_Y.rotationDegrees(f * -135.0f));
        matrices.translate(f * 5.6f, 0.0f, 0.0f);
    }

    @Unique
    private void hmi$itemPose(MatrixStack matrices, ItemStack item, boolean bl, float swingProgress, AbstractClientPlayerEntity player, boolean mainHand, Hand hand, float equipProgress, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        matrices.translate(0.5 * (bl ? 1 : -1), -0.15, -0.85);
        matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_X.rotationDegrees(15.0f), 0.5f, 0.5f, 0.5f);
        matrices.scale(0.9f, 0.9f, 0.9f);
        LuaTestHMI.itemScriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : LuaTestHMI.itemAddonsCache) {
            scriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
    }

    @Unique
    private void hmi$mainHandPose(MatrixStack matrices, ItemStack item, boolean bl, float swingProgress, float equipProgress, AbstractClientPlayerEntity player, boolean mainHand, Hand hand, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        int l = bl ? 1 : -1;
        LuaTestHMI.handRelativeScriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : LuaTestHMI.handRelativeAddonsCache) {
            scriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
        if (!item.isEmpty()) {
            matrices.translate(1.5 * l, -0.3, -0.6);
            matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_X.rotationDegrees(15.0f), 0.5f * l, 0.5f, 0.5f);
            matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_Y.rotationDegrees(35 * l), 0.5f * l, 0.5f, 0.5f);
            matrices.multiply((Quaternionfc) RotationAxis.POSITIVE_Z.rotationDegrees(-65 * l), 0.5f * l, 0.5f, 0.5f);
            matrices.scale(0.9f, 0.9f, 0.9f);
        }
    }

    @Unique
    private void hmi$scenePoseMain(MatrixStack matrices, ItemStack item, boolean bl, float swingProgress, float equipProgress, AbstractClientPlayerEntity player, boolean mainHand, Hand hand, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        LuaTestHMI.handScriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        for (LuaScriptCache scriptCache : LuaTestHMI.handAddonsCache) {
            scriptCache.execute(matrices, bl, this.hmi$registry, swingProgress, item, player, hand, mainHand, LuaTestHMI.deltaTime, equipProgress, mainHandSwingProgress, offHandSwingProgress, mainHandSwitchEvent, offHandSwitchEvent, swingMHand, swingOHand, interact, blockBreaking, particles);
        }
        if (!item.isEmpty()) {
            matrices.translate(0.0, -0.35, 0.2);
        }
    }

    @Redirect(
            method = {"renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderFirstPersonItem(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/util/Hand;FLnet/minecraft/item/ItemStack;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V")
    )
    @SuppressWarnings("StringEquality")
    private void hmi$renderOverhaul(HeldItemRenderer instance, AbstractClientPlayerEntity player, float tickProgress, float pitch, Hand hand, float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        // Если модуль "Living Hands" выключен — обычный ванильный рендер руки.
        if (!vesence.module.impl.visuals.LivingHands.isEnabled()) {
            this.renderFirstPersonItem(player, tickProgress, pitch, hand, swingProgress, item, equipProgress, matrices, queue, light);
            return;
        }
        if (player.isUsingSpyglass()) {
            return;
        }
        ((ItemStackAccessor) (Object) item).hMI5_0$setTransform(-1);
        boolean bl = hand == Hand.MAIN_HAND;
        boolean interact = false;
        boolean blockBreaking = false;
        if (bl && player.isUsingItem() && player.getActiveHand() != hand && (player.getOffHandStack().getUseAction() == UseAction.BOW || player.getOffHandStack().getUseAction() == UseAction.CROSSBOW && !CrossbowItem.isCharged(player.getOffHandStack()))) {
            item = Items.AIR.getDefaultStack();
        }
        if (!bl && player.isUsingItem() && player.getActiveHand() != hand && (player.getMainHandStack().getUseAction() == UseAction.BOW || player.getMainHandStack().getUseAction() == UseAction.CROSSBOW && !CrossbowItem.isCharged(player.getMainHandStack()))) {
            item = Items.AIR.getDefaultStack();
        }
        if (bl) {
            this.hmi$mainHandSwitchEvent = item.getItem() != this.hmi$prevMainHand;
        } else {
            this.hmi$offHandSwitchEvent = item.getItem() != this.hmi$prevOffHand;
        }
        matrices.push();
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
        Arm arm = bl ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean bl2 = arm == Arm.RIGHT;
        int l = bl2 ? 1 : -1;
        this.hmi$scenePoseMain(matrices, item, bl2, swingProgress, equipProgress, player, bl, hand, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
        matrices.push();
        this.hmi$mainHandPose(matrices, item, bl2, swingProgress, equipProgress, player, bl, hand, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
        int combinedLight = LightmapTextureManager.applyEmission(light, Block.getBlockFromItem(item.getItem()).getDefaultState().getLuminance());
        if (player.isInvisible()) {
            this.hmi$applyArmMatrices(matrices, combinedLight, 0.0f, 0.0f, arm);
        } else {
            this.renderArmHoldingItem(matrices, queue, combinedLight, 0.0f, 0.0f, arm);
        }
        matrices.pop();
        matrices.push();
        boolean renderAsBlock = !(Block.getBlockFromItem(item.getItem()) == Blocks.AIR
                || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.INSIDE_STEP_SOUND_BLOCKS)
                || Block.getBlockFromItem(item.getItem()).getDefaultState().isIn(BlockTags.CROPS)
                || item.getUseAction() == UseAction.EAT
                || item.isOf(Items.REDSTONE)
                || item.isIn(ItemTags.BANNERS)
                || item.isIn(ItemTags.SKULLS)
                || !LuaTestHMI.renderAsBlock.getOrDefault(item.getItem().toString(), true));
        if (renderAsBlock) {
            swingProgress = 0.0f;
            BlockState blockState = Block.getBlockFromItem(item.getItem()).getDefaultState();
            this.hmi$itemPose(matrices, item, bl2, swingProgress, player, bl, hand, equipProgress, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
            matrices.translate(0.22 * l, 0.25, 0.2);
            if (item.isOf(Items.LEVER) || blockState.isIn(BlockTags.BUTTONS)) {
                blockState = blockState.with(Properties.BLOCK_FACE, BlockFace.FLOOR);
            }
            if (item.getName().toString().toLowerCase().contains("torch") || item.isOf(Items.LANTERN) || item.isOf(Items.SOUL_LANTERN) || blockState.isIn(BlockTags.ALL_HANGING_SIGNS)) {
                matrices.translate(-0.05 * l, 0.0, 0.0);
                matrices.scale(1.75f, 1.75f, 1.75f);
            } else {
                matrices.translate(-0.25 * l, -0.05, 0.0);
            }
            matrices.push();
            matrices.scale(0.3f, 0.3f, 0.3f);
            matrices.translate(-0.9 * l, -0.45, -0.7);
            matrices.pop();
            if (!bl2) {
                matrices.translate(-0.3f, 0.0f, 0.0f);
            }
            matrices.scale(0.3f, 0.3f, 0.3f);
            matrices.translate(-0.9 * l, -0.45, -0.7);
            if (item.isOf(Items.BELL)) {
                // В 1.21.11 рендер BlockEntity первого лица требует CameraRenderState из пайплайна;
                // вместо отдельного BlockEntity рендерим колокол как обычный подвешенный блок через очередь.
                queue.submitBlock(matrices, Blocks.BELL.getDefaultState().with(Properties.ATTACHMENT, Attachment.CEILING), light, OverlayTexture.DEFAULT_UV, 0);
            } else {
                if (blockState.contains(Properties.DOUBLE_BLOCK_HALF)) {
                    matrices.push();
                    matrices.translate(0.0f, 1.0f, 0.0f);
                    queue.submitBlock(matrices, blockState.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), light, OverlayTexture.DEFAULT_UV, 0);
                    matrices.pop();
                }
                queue.submitBlock(matrices, blockState, light, OverlayTexture.DEFAULT_UV, 0);
            }
            matrices.push();
            if (!bl2) {
                matrices.translate(1.0f, 0.0f, 0.0f);
            }
            ParticleRenderManager.draw(this.hmi$particles, matrices, queue, "ITEM", hand, light, player);
            matrices.pop();
        } else {
            this.hmi$itemPose(matrices, item, bl2, swingProgress, player, bl, hand, equipProgress, this.hmi$mainHandSwingProgress, this.hmi$offHandSwingProgress, this.hmi$mainHandSwitchEvent, this.hmi$offHandSwitchEvent, this.hmi$swingMHand, this.hmi$swingOHand, interact, blockBreaking, this.hmi$particles);
            if (item.getUseAction() == UseAction.BLOCK || item.getUseAction() == UseAction.BRUSH) {
                ItemStack renderStack = new ItemStack((ItemConvertible) item.getItem(), item.getCount());
                this.hmi$copyAppearanceComponents(item, renderStack);
                this.renderItem((LivingEntity) player, renderStack, bl2 ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, matrices, queue, light);
            } else {
                this.renderItem((LivingEntity) player, item, bl2 ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND, matrices, queue, light);
            }
            matrices.push();
            ParticleRenderManager.draw(this.hmi$particles, matrices, queue, "ITEM", hand, light, player);
            matrices.pop();
        }
        matrices.pop();
        matrices.pop();
        if (bl) {
            this.hmi$prevMainHand = item.getItem();
        } else {
            this.hmi$prevOffHand = item.getItem();
        }
        ParticleRenderManager.draw(this.hmi$particles, matrices, queue, "SCREEN", hand, light, player);
    }
}
