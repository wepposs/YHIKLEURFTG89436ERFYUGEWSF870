package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.event.impl.EventChangeWorld;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.impl.visuals.ShaderESP;
import vesence.ui.MainScreen;
import vesence.ui.MainScreenHelper;

@Environment(EnvType.CLIENT)
@Mixin(Minecraft.class)
public abstract class MinecraftClientMixin {

   @Inject(method = "tick", at = @At("TAIL"), require = 0)
   private void publishClientTick(CallbackInfo ci) {
      try {
         vesence.utils.debug.FreezeWatchdog.beat("tick");
         if (Vesence.isModInitialized()) {
            Minecraft client = (Minecraft) (Object) this;
            if (!client.isPaused()) {
               LocalPlayer player = client.player;
               ClientLevel world = client.level;
               if (player != null && world != null) {
                  EventManager.call(new ClientTickEvent(client));
                }
             }
          }
       } catch (Throwable ignored) {
       }
   }

   @Inject(method = "setLevel", at = @At("TAIL"), require = 0)
   public void loadWorld(ClientLevel world, CallbackInfo ci) {
      try {
         MainScreen.stopMenuMusic();
      } catch (Throwable ignored) {
      }
      try {
         EventManager.call(new EventChangeWorld());
      } catch (Throwable ignored) {
      }
   }

   // 26.3: подмена экранов живёт в GuiScreenRedirectMixin (Gui#setScreen) — именно туда
   // попадает TitleScreen из Gui#buildInitialScreens, минуя setScreenAndShow.

   @Inject(method = "resizeGui", at = @At("TAIL"), require = 0)
   private void vesence$onResolutionChanged(CallbackInfo ci) {
      try {
         if (Vesence.isModInitialized()) {
            vesence.renderengine.render.Renderer2D.clearImageTextureCache();
         }
      } catch (Throwable ignored) {
      }
   }

   @Inject(method = "reloadResourcePacks", at = @At("HEAD"), require = 0)
   private void vesence$onReloadResources(CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> cir) {
      try {
         if (Vesence.isModInitialized()) {
            vesence.renderengine.render.Renderer2D.clearImageTextureCache();
         }
      } catch (Throwable ignored) {
      }
   }

   @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true, require = 0, expect = 0)
   private void vesence$shaderEspHasOutline(net.minecraft.world.entity.Entity entity,
                                            CallbackInfoReturnable<Boolean> cir) {
      try {
         ShaderESP esp = ShaderESP.INSTANCE;
         if (esp != null && esp.shouldOutline(entity)) {
            cir.setReturnValue(true);
         }
      } catch (Throwable ignored) {
      }
   }

   /**
    * Подменяет прицел на блок, который в данный момент копает баритон.
    *
    * <p>Ваниль каждый кадр пересчитывает {@code hitResult} лучом из глаз игрока
    * и уже потом, в тике, ломает именно его. Наш поворот головы применяется
    * позже этого пересчёта, поэтому без подмены она бьёт по блоку, который был
    * в прицеле до поворота, — то есть по соседнему. Снаружи это выглядело так:
    * персонаж смотрит на нужный блок, держит атаку, вокруг крошатся случайные
    * блоки, а цель остаётся целой.
    *
    * <p>Вмешиваемся только когда баритон действительно копает: во всех
    * остальных случаях прицел должен быть настоящим.
    */
   @Inject(method = "pick", at = @At("TAIL"), require = 0)
   private void vesence$forcePick(float partialTick, CallbackInfo ci) {
      try {
         vesence.pathing.BlockInteraction.overridePick((Minecraft) (Object) this);
      } catch (Throwable ignored) {
      }
   }
}
