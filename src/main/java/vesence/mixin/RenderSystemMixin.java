package vesence.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.TracyFrameCapture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;

@Environment(EnvType.CLIENT)
@Mixin({Minecraft.class})
public class RenderSystemMixin {
   @Inject(
      method = {"renderFrame"},
      at = {@At("HEAD")}
   )
   private static void vesence$onRenderFrame(boolean tick, CallbackInfo ci) {
      Vesence.onRender();
   }
}
