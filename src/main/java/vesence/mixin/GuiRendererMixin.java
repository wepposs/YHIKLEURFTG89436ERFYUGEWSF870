package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.misc.BetterMinecraft;

/**
 * In 1.21.11 every {@link net.minecraft.client.gui.GuiGraphicsExtractor} draw call (item textures, widget
 * textures, screen background blur) is recorded into a {@code GuiRenderState} and only painted to
 * the framebuffer here, when {@link GuiRenderer#render} flushes that state. Our raw-GL renderer
 * draws immediately during the record phase, so it always ends up underneath the vanilla GUI.
 *
 * <p>Hooking the RETURN of the flush lets the custom hotbar stack counts and custom button skins be
 * painted ON TOP of the freshly rendered vanilla GUI content.
 */
@Environment(EnvType.CLIENT)
@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {

   // TODO(port26): GuiRenderer.render()V no longer takes a GpuBufferSlice in 26.3
   @Inject(method = "render()V", at = @At("RETURN"), require = 0, expect = 0)
   private void vesence$renderPostGuiOverlays(CallbackInfo ci) {
      if (Vesence.isModInitialized()) {
         BetterMinecraft.renderPostGuiOverlays();
      }
   }
}
