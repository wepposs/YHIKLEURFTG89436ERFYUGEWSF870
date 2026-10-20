package vesence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.render.GlassHandsRenderEvent;
import vesence.event.render.HandAnimationEvent;
import vesence.event.render.HandOffsetEvent;
import vesence.event.render.HandShadowRenderEvent;
import vesence.event.render.ItemTransformEvent;
import vesence.event.render.RenderItemEvent;
import vesence.module.impl.movement.FreecamState;
import vesence.module.impl.visuals.CustomHand;

@Environment(EnvType.CLIENT)
@Mixin({FirstPersonHandsAndItemsRenderer.class})
public abstract class HeldItemRendererMixin {
   @Inject(
           method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"},
           at = {@At("HEAD")},
           cancellable = true
   )
   private void onRenderItemHead(CallbackInfo ci) {
      if (FreecamState.pos != null) {
         ci.cancel();
      }
   }

   @Inject(
           method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"},
           at = {@At("HEAD")}
   )
   private void onRenderItemPre(float tickProgress, PoseStack matrices, SubmitNodeCollector orderedRenderCommandQueue, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState, CallbackInfo ci) {
      if (FreecamState.pos == null && CustomHand.glassShouldRender()) {
         EventManager.call(new GlassHandsRenderEvent(GlassHandsRenderEvent.Phase.PRE, matrices, tickProgress));
      }
      if (FreecamState.pos == null && CustomHand.shadowShouldRender()) {
         EventManager.call(new HandShadowRenderEvent(HandShadowRenderEvent.Phase.PRE, matrices, tickProgress));
      }
   }

   @Inject(
           method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"},
           at = {@At("TAIL")}
   )
   private void onRenderItemPost(float tickProgress, PoseStack matrices, SubmitNodeCollector orderedRenderCommandQueue, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handsState, CallbackInfo ci) {
      if (FreecamState.pos == null && CustomHand.glassShouldRender()) {
         EventManager.call(new GlassHandsRenderEvent(GlassHandsRenderEvent.Phase.POST, matrices, tickProgress));
      }
      if (FreecamState.pos == null && CustomHand.shadowShouldRender()) {
         EventManager.call(new HandShadowRenderEvent(HandShadowRenderEvent.Phase.POST, matrices, tickProgress));
      }
   }

   @Inject(
           method = {"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
           at = {@At("HEAD")}
   )
   private void onRenderFirstPersonItem(
           PlayerRenderState playerState,
           FirstPersonHandsAndItemsRenderState handsState,
           float tickProgress,
           float pitch,
           InteractionHand hand,
           float swingProgress,
           ItemStack stack,
           float equipProgress,
           PoseStack matrices,
           SubmitNodeCollector queue,
           int light,
           CallbackInfo ci
   ) {
      RenderItemEvent renderItemEvent = new RenderItemEvent(matrices, hand);
      EventManager.call(renderItemEvent);
   }

   @Redirect(
           method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"},
           at = @At(
                   value = "FIELD",
                   target = "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;viewYRot:F",
                   opcode = Opcodes.GETFIELD
           )
   )
   private float redirectGetYaw(FirstPersonHandsAndItemsRenderState state) {
      return Minecraft.getInstance().gameRenderer.mainCamera().yRot();
   }

   @Redirect(
           method = {"submitHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;)V"},
           at = @At(
                   value = "FIELD",
                   target = "Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;viewXRot:F",
                   opcode = Opcodes.GETFIELD
           )
   )
   private float redirectGetPitch(FirstPersonHandsAndItemsRenderState state) {
      return Minecraft.getInstance().gameRenderer.mainCamera().xRot();
   }

   @Inject(
           method = {"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                   shift = At.Shift.AFTER
           )}
   )
   private void handOffsetHook(
           PlayerRenderState playerState,
           FirstPersonHandsAndItemsRenderState handsState,
           float tickProgress,
           float pitch,
           InteractionHand hand,
           float swingProgress,
           ItemStack stack,
           float equipProgress,
           PoseStack matrices,
           SubmitNodeCollector queue,
           int light,
           CallbackInfo ci
   ) {
      HandOffsetEvent event = new HandOffsetEvent(matrices, stack, hand);
      EventManager.call(event);

      float scale = event.getScale();
      if (scale != 1.0F) {
         matrices.scale(scale, scale, scale);
      }
   }

   @Inject(
           method = {"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
           )},
           require = 0
   )
   private void itemTransformHook(
           PlayerRenderState playerState,
           FirstPersonHandsAndItemsRenderState handsState,
           float tickProgress,
           float pitch,
           InteractionHand hand,
           float swingProgress,
           ItemStack stack,
           float equipProgress,
           PoseStack matrices,
           SubmitNodeCollector queue,
           int light,
           CallbackInfo ci
   ) {
      EventManager.call(new ItemTransformEvent(matrices, stack, hand));
   }

   @WrapOperation(
           method = {"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"
           )}
   )
   private void handAnimationHook(
           FirstPersonHandsAndItemsRenderer instance,
           float swingProgress,
           PoseStack matrices,
           int armX,
           HumanoidArm arm,
           Operation<Void> original,
           @Local(ordinal = 0, argsOnly = true) PlayerRenderState playerState,
           @Local(ordinal = 0, argsOnly = true) InteractionHand hand
   ) {
      CustomHand customHand = Vesence.get.getManager().get(CustomHand.class);
      // Кастомная анимация свинга уже применена в wrapApplyEquipOffset (единая
      // точка, срабатывающая для предмета в руке при ЛЮБОМ типе свинга). Здесь
      // лишь пропускаем ванильный swingArm (тип WHACK — мечи/топоры), чтобы не
      // наложить ванильный свинг поверх кастомного.
      if (customHand != null
              && customHand.willHandleSwing(hand)
              && !playerState.avatarRenderState.isUsingItem
              && !playerState.avatarRenderState.isAutoSpinAttack) {
         return;
      }
      original.call(instance, swingProgress, matrices, armX, arm);
   }

   @WrapOperation(
           method = {"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"
           )}
   )
   private void wrapApplyEquipOffset(
           FirstPersonHandsAndItemsRenderer instance,
           PoseStack matrices,
           HumanoidArm arm,
           float equipProgress,
           Operation<Void> original,
           @Local(ordinal = 0, argsOnly = true) PlayerRenderState playerState,
           @Local(ordinal = 0, argsOnly = true) InteractionHand hand,
           @Local(ordinal = 2, argsOnly = true) float swingProgress
   ) {
      CustomHand customHand = Vesence.get.getManager().get(CustomHand.class);
      // Ваниль 1.21.x вызывает applyEquipOffset (базовый offset i*0.56,-0.52,-0.72)
      // ОТДЕЛЬНО, прямо перед свингом. Сам свинг зависит от типа предмета:
      //   WHACK -> swingArm(...),  STAB -> Lancing(...),  NONE -> без свинга.
      // Кастомные анимации свинга уже содержат свой полный base-offset (как в
      // референсе). Раньше кастом применялся в хуке swingArm, а applyEquipOffset
      // пропускался. Но для НЕ-WHACK предметов (STAB/NONE — напр. меч в этой сборке)
      // swingArm вообще не вызывается → кастом не применялся, а base-offset всё
      // равно пропускался → предмет оставался без смещения и выглядел маленьким.
      //
      // Теперь применяем кастом ИМЕННО ЗДЕСЬ: applyEquipOffset гарантированно
      // вызывается для предмета в руке при любом типе свинга. Матрица здесь та же,
      // что была бы у swingArm (между ними ваниль ничего не делает), поэтому
      // результат для WHACK идентичен прежнему. Ванильный applyEquipOffset
      // пропускаем (base-offset даёт кастом), а ванильный свинг (swingArm/Lancing)
      // пропускается в соседних хуках. Итог: ванильный размер/позиция предмета +
      // нетронутая кастомная анимация для ВСЕХ предметов.
      if (customHand != null
              && customHand.willHandleSwing(hand)
              && !playerState.avatarRenderState.isUsingItem
              && !playerState.avatarRenderState.isAutoSpinAttack) {
         HandAnimationEvent event = new HandAnimationEvent(matrices, hand, swingProgress);
         EventManager.call(event);
         return;
      }
      if (customHand != null && customHand.isHideVanilla()) {
         equipProgress = 0.0F;
      }
      original.call(instance, matrices, arm, equipProgress);
   }

   @WrapOperation(
           method = {"submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"},
           at = {@At(
                   value = "INVOKE",
                   target = "Lnet/minecraft/client/model/effects/SpearAnimations;firstPersonAttack(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"
           )},
           require = 0
   )
   private void wrapStabSwing(
           float swingProgress,
           PoseStack matrices,
           int armX,
           HumanoidArm arm,
           Operation<Void> original,
           @Local(ordinal = 0, argsOnly = true) PlayerRenderState playerState,
           @Local(ordinal = 0, argsOnly = true) InteractionHand hand
   ) {
      CustomHand customHand = Vesence.get.getManager().get(CustomHand.class);
      // Аналог хука swingArm, но для предметов с типом свинга STAB (копья/шпаги и
      // т.п., которые ваниль анимирует через Lancing вместо swingArm). Кастом уже
      // применён в wrapApplyEquipOffset — пропускаем ванильный STAB, чтобы не
      // наложить его поверх кастомной анимации. require=0: если в этой сборке
      // такого вызова нет — хук просто не применится, ничего не ломая.
      if (customHand != null
              && customHand.willHandleSwing(hand)
              && !playerState.avatarRenderState.isUsingItem
              && !playerState.avatarRenderState.isAutoSpinAttack) {
         return;
      }
      original.call(swingProgress, matrices, armX, arm);
   }
}
