package vesence.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.hud.bar.Bar;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.texture.GlTexture;
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

@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
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

   @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
   private void onRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized() && Vesence.get.manager.get(vesence.module.impl.visuals.Crosshair.class).enable) {
         ci.cancel();
      }
   }

   @Inject(method = "renderScoreboardSidebar", at = @At("HEAD"), cancellable = true)
   private void onRenderScoreboard(CallbackInfo ci) {
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

   @Inject(method = "renderTitleAndSubtitle", at = @At("HEAD"), cancellable = true)
   private void onRenderTitle(CallbackInfo ci) {
      if (Vesence.get.manager.get(NoRender.class).enable && NoRender.elements.get("Тайтлы")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderMainHud", at = @At("HEAD"), cancellable = true)
   private void vesence$renderCustomHotBar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (!BetterMinecraft.shouldReplaceMainHud(client)) {
         return;
      }
      BetterMinecraft.renderCustomHotBar(context, tickCounter);
      ci.cancel();
   }

   @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
   private void vesence$renderCustomHotBarOnly(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (!BetterMinecraft.shouldReplaceHotbarOnly(client)) {
         return;
      }
      BetterMinecraft.renderCustomHotBar(context, tickCounter, false);
      ci.cancel();
   }

   @Inject(method = "renderStatusBars", at = @At("HEAD"))
   private void vesence$pushVanillaValuesOffset(DrawContext context, CallbackInfo ci) {
      if (BetterMinecraft.shouldReplaceHotbarOnly(MinecraftClient.getInstance())) {
         context.getMatrices().pushMatrix();
         context.getMatrices().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      }
   }

   @Inject(method = "renderStatusBars", at = @At("RETURN"))
   private void vesence$popVanillaValuesOffset(DrawContext context, CallbackInfo ci) {
      if (BetterMinecraft.shouldReplaceHotbarOnly(MinecraftClient.getInstance())) {
         context.getMatrices().popMatrix();
      }
   }

   @WrapOperation(
           method = "renderMainHud",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")
   )
   private void vesence$offsetVanillaBar(Bar instance, DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(MinecraftClient.getInstance())) {
         original.call(instance, context, tickCounter);
         return;
      }
      context.getMatrices().pushMatrix();
      context.getMatrices().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(instance, context, tickCounter);
      } finally {
         context.getMatrices().popMatrix();
      }
   }

   @WrapOperation(
           method = "renderMainHud",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderAddons(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V")
   )
   private void vesence$offsetVanillaBarAddons(Bar instance, DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(MinecraftClient.getInstance())) {
         original.call(instance, context, tickCounter);
         return;
      }
      context.getMatrices().pushMatrix();
      context.getMatrices().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(instance, context, tickCounter);
      } finally {
         context.getMatrices().popMatrix();
      }
   }

   @WrapOperation(
           method = "renderMainHud",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V")
   )
   private void vesence$offsetVanillaExperienceLevel(DrawContext context, TextRenderer textRenderer, int level, Operation<Void> original) {
      if (!BetterMinecraft.shouldReplaceHotbarOnly(MinecraftClient.getInstance())) {
         original.call(context, textRenderer, level);
         return;
      }
      context.getMatrices().pushMatrix();
      context.getMatrices().translate(0f, VESENCE_VANILLA_VALUES_Y_OFFSET);
      try {
         original.call(context, textRenderer, level);
      } finally {
         context.getMatrices().popMatrix();
      }
   }

   @Inject(method = "renderMainHud", at = @At("RETURN"))
   private void vesence$renderHeldItemNameOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (BetterMinecraft.shouldRenderCustomHeldItemName(client) && !BetterMinecraft.shouldReplaceMainHud(client)) {
         BetterMinecraft.renderHeldItemNameOverlay(context);
      }
   }

   @Inject(method = "renderHeldItemTooltip", at = @At("HEAD"), cancellable = true)
   private void vesence$cancelVanillaHeldItemTooltip(DrawContext context, CallbackInfo ci) {
      if (BetterMinecraft.shouldRenderCustomHeldItemName(MinecraftClient.getInstance())) {
         ci.cancel();
      }
   }

   @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderVignette(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Виньетка")) {
         ci.cancel();
      }
   }

   /** Deluxe NoRender: «Тыква на голове» — прячем оверлей pumpkinblur. */
   @Inject(method = "renderOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$hidePumpkinOverlay(DrawContext context, net.minecraft.util.Identifier texture, float opacity, CallbackInfo ci) {
      if (NoRender.isElementActive("Тыква на голове") && texture.getPath().contains("pumpkinblur")) {
         ci.cancel();
      }
   }

   /** Deluxe NoRender: «Индикатор атаки» — прячем индикатор кулдауна под прицелом. */
   @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
           method = "renderCrosshair",
           at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;"))
   private Object vesence$hideAttackIndicator(Object original) {
      return NoRender.isElementActive("Индикатор атаки") ? net.minecraft.client.option.AttackIndicator.OFF : original;
   }

   @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderStatusEffectOverlay(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Иконки эффектов")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderNausea(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Тошнота")) {
         ci.cancel();
      }
   }

   @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
   private void vesence$onRenderPortal(CallbackInfo ci) {
      NoRender nr = Vesence.get.manager.get(NoRender.class);
      if (nr != null && nr.enable && NoRender.elements.get("Портал на экране")) {
         ci.cancel();
      }
   }

   @Inject(method = "render", at = @At("HEAD"))
   private void onPreRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.world != null && client.getWindow() != null) {
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
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

   @Inject(method = "render", at = @At("RETURN"))
   private void onRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.world != null && client.getWindow() != null) {
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
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
   private static int vesence$bindMainFramebuffer(MinecraftClient client) {
      long section = vesence.utils.debug.FreezeWatchdog.sectionEnter("framebuffer.attach");
      try {
         Framebuffer mainFramebuffer = client.getFramebuffer();
         if (mainFramebuffer == null) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return 0;
         }
         if (!(mainFramebuffer.getColorAttachment() instanceof GlTexture glColor)) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            return 0;
         }

         int mainFramebufferTextureId = glColor.getGlId();
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
