package vesence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.render.EventScreen;
import vesence.event.render.EventScreenPre;
import vesence.module.impl.misc.BetterMinecraft;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.NoRender;
import vesence.renderengine.providers.GlState;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.text.FontRegistry;

// TODO(port26): net.minecraft.client.gui.Gui no longer renders the HUD in 26.3 — all render*
// methods moved to net.minecraft.client.gui.Hud as extract* methods (extract render state pipeline).
@Environment(EnvType.CLIENT)
@Mixin(net.minecraft.client.gui.Hud.class)
public abstract class InGameHudMixin {

   @org.spongepowered.asm.mixin.Unique
   private static final float VESENCE_VANILLA_VALUES_Y_OFFSET = -10f;

   @org.spongepowered.asm.mixin.Unique
   private static int vesence$cachedFbo = 0;

   @org.spongepowered.asm.mixin.Unique
   private static long vesence$fboStatusFrameId = Long.MIN_VALUE;

   @org.spongepowered.asm.mixin.Unique
   private static int vesence$fboStatusTextureId = 0;

   @org.spongepowered.asm.mixin.Unique
   private static boolean vesence$fboStatusComplete = false;

   @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
   private void onRenderCrosshair(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized() && Vesence.get.manager.get(vesence.module.impl.visuals.Crosshair.class).enable) {
         ci.cancel();
      }
   }

   @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
   private void onRenderScoreboard(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Таблица")) {
         ci.cancel();
         return;
      }
      // Кастомный скорборд (ScoreboardElement) активен — прячем ванильный сайдбар,
      // его рисует HUD-элемент. Порт Interface.isScoreboardHudVisible() из relevant.
      if (vesence.module.impl.visuals.elements.ScoreboardElement.shouldSuppressVanilla()) {
         ci.cancel();
      }
   }

   @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
   private void onRenderTitle(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Тайтлы")) {
         ci.cancel();
      }
   }

   // БЫЛО renderMainHud: полная замена главного HUD (хотбар + статус-бары).
   @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
   private void vesence$renderCustomHotBar(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (!BetterMinecraft.shouldReplaceMainHud(client)) {
         return;
      }
      BetterMinecraft.renderCustomHotBar(context, tickCounter);
      ci.cancel();
   }

   @Inject(method = "extractItemHotbar", at = @At("HEAD"), cancellable = true)
   private void vesence$renderCustomHotBarOnly(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (!BetterMinecraft.shouldReplaceHotbarOnly(client)) {
         return;
      }
      BetterMinecraft.renderCustomHotBar(context, tickCounter, false);
      ci.cancel();
   }

   // БЫЛО renderStatusBars push/pop: сдвиг ванильных полос вверх, когда заменён только хотбар.
   // В 26.3 полосы рисуются из extractHotbarAndDecorations отдельными вызовами — оборачиваем их.
   @WrapOperation(
           method = "extractHotbarAndDecorations",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractPlayerHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
   )
   private void vesence$offsetPlayerHealth(net.minecraft.client.gui.Hud instance, GuiGraphicsExtractor context, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(Minecraft.getInstance())) {
         original.call(instance, context);
         return;
      }
      context.pose().pushMatrix();
      context.pose().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(instance, context);
      } finally {
         context.pose().popMatrix();
      }
   }

   @WrapOperation(
           method = "extractHotbarAndDecorations",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractVehicleHealth(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V")
   )
   private void vesence$offsetVehicleHealth(net.minecraft.client.gui.Hud instance, GuiGraphicsExtractor context, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(Minecraft.getInstance())) {
         original.call(instance, context);
         return;
      }
      context.pose().pushMatrix();
      context.pose().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(instance, context);
      } finally {
         context.pose().popMatrix();
      }
   }

   @WrapOperation(
           method = "extractHotbarAndDecorations",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
   )
   private void vesence$offsetVanillaBar(net.minecraft.client.gui.contextualbar.ContextualBar instance, GuiGraphicsExtractor context, DeltaTracker tickCounter, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(Minecraft.getInstance())) {
         original.call(instance, context, tickCounter);
         return;
      }
      context.pose().pushMatrix();
      context.pose().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(instance, context, tickCounter);
      } finally {
         context.pose().popMatrix();
      }
   }

   @WrapOperation(
           method = "extractHotbarAndDecorations",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V")
   )
   private void vesence$offsetVanillaBarAddons(net.minecraft.client.gui.contextualbar.ContextualBar instance, GuiGraphicsExtractor context, DeltaTracker tickCounter, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(Minecraft.getInstance())) {
         original.call(instance, context, tickCounter);
         return;
      }
      context.pose().pushMatrix();
      context.pose().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(instance, context, tickCounter);
      } finally {
         context.pose().popMatrix();
      }
   }

   @WrapOperation(
           method = "extractHotbarAndDecorations",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V")
   )
   private void vesence$offsetVanillaExperienceLevel(GuiGraphicsExtractor context, Font font, int level, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(Minecraft.getInstance())) {
         original.call(context, font, level);
         return;
      }
      context.pose().pushMatrix();
      context.pose().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(context, font, level);
      } finally {
         context.pose().popMatrix();
      }
   }

   @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
   private void vesence$renderHeldItemNameOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      Minecraft client = Minecraft.getInstance();
      if (BetterMinecraft.shouldRenderCustomHeldItemName(client) && !BetterMinecraft.shouldReplaceMainHud(client)) {
         BetterMinecraft.renderHeldItemNameOverlay(context);
      }
   }

   @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
   private void vesence$cancelVanillaHeldItemTooltip(GuiGraphicsExtractor context, CallbackInfo ci) {
      if (BetterMinecraft.shouldRenderCustomHeldItemName(Minecraft.getInstance())) {
         ci.cancel();
      }
   }

   @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderVignette(GuiGraphicsExtractor context, net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Виньетка")) {
         ci.cancel();
      }
   }

   /** Deluxe NoRender: «Тыква на голове» — прячем оверлей pumpkinblur. */
   @Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$hidePumpkinOverlay(GuiGraphicsExtractor context, net.minecraft.resources.Identifier texture, float opacity, CallbackInfo ci) {
      if (NoRender.isElementActive("Тыква на голове") && texture.getPath().contains("pumpkinblur")) {
         ci.cancel();
      }
   }

   /** Deluxe NoRender: «Индикатор атаки» — прячем индикатор кулдауна под прицелом. */
   @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
           method = "extractCrosshair",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
   private Object vesence$hideAttackIndicator(Object original) {
      return NoRender.isElementActive("Индикатор атаки") ? net.minecraft.client.AttackIndicatorStatus.OFF : original;
   }

   @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderStatusEffectOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Иконки эффектов")) {
         ci.cancel();
      }
   }

   @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderNausea(GuiGraphicsExtractor context, float nauseaLevel, CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Тошнота")) {
         ci.cancel();
      }
   }

   @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderPortal(GuiGraphicsExtractor context, float portalAlpha, CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Портал на экране")) {
         ci.cancel();
      }
   }

   @Inject(method = "extractRenderState", at = @At("HEAD"))
   private void onPreRenderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.player != null && client.level != null && client.getWindow() != null) {
            int width = client.getWindow().getScreenWidth();
            int height = client.getWindow().getScreenHeight();
            if (width > 0 && height > 0) {
               GlState.Snapshot snapshot = GlState.push();

               int tempFbo = vesence$bindMainFramebuffer(client);

               long section = vesence.utils.debug.FreezeWatchdog.sectionEnter("hud.pre");
               try {
                  Vesence.ensureRendererInitialized();
                  Renderer2D renderer = Vesence.getRenderer();
                  if (renderer != null) {
                     renderer.begin(width, height);
                     try {
                        EventManager.call(new EventScreenPre(client, renderer, FontRegistry.SF_MEDIUM, width, height, context));
                     } finally {
                        renderer.end();
                     }
                  }
               } catch (Exception e) {
                  System.err.println("[weppos] Pre-HUD render error: " + e.getMessage());
               } finally {
                  vesence.utils.debug.FreezeWatchdog.sectionExit("hud.pre", section);
                  vesence$detachMainFramebuffer(tempFbo);
                  GlState.pop(snapshot);
               }
            }
         }
      }
   }

   @Inject(method = "extractRenderState", at = @At("RETURN"))
   private void onRenderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         Minecraft client = Minecraft.getInstance();
         if (client != null && client.player != null && client.level != null && client.getWindow() != null) {
            int width = client.getWindow().getScreenWidth();
            int height = client.getWindow().getScreenHeight();
            if (width > 0 && height > 0) {
               GlState.Snapshot snapshot = GlState.push();

               int tempFbo = vesence$bindMainFramebuffer(client);

               long section = vesence.utils.debug.FreezeWatchdog.sectionEnter("hud.post");
               try {
                  Vesence.ensureRendererInitialized();
                  Renderer2D renderer = Vesence.getRenderer();
                  if (renderer != null) {
                     renderer.begin(width, height);
                     try {
                        EventManager.call(new EventScreen(client, renderer, FontRegistry.SF_MEDIUM, width, height, context));
                     } finally {
                        renderer.end();
                     }
                  }
               } catch (Exception e) {
                  System.err.println("[weppos] HUD render error: " + e.getMessage());
               } finally {
                  vesence.utils.debug.FreezeWatchdog.sectionExit("hud.post", section);
                  vesence$detachMainFramebuffer(tempFbo);
                  GlState.pop(snapshot);
               }
            }
         }
      }
   }

   @org.spongepowered.asm.mixin.Unique
   private static int vesence$bindMainFramebuffer(Minecraft client) {
      long section = vesence.utils.debug.FreezeWatchdog.sectionEnter("framebuffer.attach");
      try {
         RenderTarget mainFramebuffer = client.gameRenderer.mainRenderTarget();
         if (mainFramebuffer == null) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return 0;
         }
         if (!(mainFramebuffer.getColorTexture() instanceof GlTexture glColor)) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return 0;
         }

         int mainFramebufferTextureId = glColor.glId();
         if (vesence$cachedFbo == 0) {
            vesence$cachedFbo = GL30.glGenFramebuffers();
         }
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, vesence$cachedFbo);
         GL30.glFramebufferTexture2D(
            GL30.GL_FRAMEBUFFER,
            GL30.GL_COLOR_ATTACHMENT0,
            GL11.GL_TEXTURE_2D,
            mainFramebufferTextureId,
            0
         );
         GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);

         long frameId = Vesence.getRenderFrameId();
         if (vesence$fboStatusFrameId != frameId || vesence$fboStatusTextureId != mainFramebufferTextureId) {
            vesence$fboStatusFrameId = frameId;
            vesence$fboStatusTextureId = mainFramebufferTextureId;
            vesence$fboStatusComplete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
         }
         if (!vesence$fboStatusComplete) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return 0;
         }
         return vesence$cachedFbo;
      } finally {
         vesence.utils.debug.FreezeWatchdog.sectionExit("framebuffer.attach", section);
      }
   }

   @org.spongepowered.asm.mixin.Unique
   private static void vesence$detachMainFramebuffer(int tempFbo) {
      if (tempFbo != 0) {
         GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tempFbo);
         GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, 0, 0);
      }
   }
}
