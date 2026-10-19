package vesence.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import net.minecraft.client.sound.SoundSystem.PlayResult;
import net.minecraft.client.sound.Channel.SourceManager;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.Vesence;
import vesence.module.impl.visuals.NoRender;
import vesence.ui.MainScreen;

@Environment(EnvType.CLIENT)
@Mixin({SoundSystem.class})
public abstract class SoundSystemMixin {
   @Shadow
   private Map<SoundInstance, SourceManager> sources;

   @Shadow
   public abstract void stop(SoundInstance sound);

    @Inject(
        method = {"play"},
        at = {@At("RETURN")}
    )
    private void onSoundPlay(SoundInstance sound, CallbackInfoReturnable<PlayResult> cir) {

    }

    /** Deluxe NoRender: заглушить выбранные категории звуков до старта. */
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void vesence$muteNoRenderSounds(SoundInstance sound, CallbackInfoReturnable<PlayResult> cir) {
        if (NoRender.shouldMute(sound)) {
            cir.setReturnValue(PlayResult.NOT_STARTED);
        }
    }

    /** Deluxe NoRender: останавливаем заглушенные звуки, уже играющие в каналах. */
    @Inject(method = "tick(Z)V", at = @At("HEAD"))
    private void vesence$stopMutedPlayingSounds(boolean paused, CallbackInfo ci) {
        try {
            if (sources.isEmpty()) return;
            for (SoundInstance playing : new java.util.ArrayList<>(sources.keySet())) {
                if (NoRender.shouldMute(playing)) {
                    stop(playing);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Пока играет собственная музыка меню (music1.ogg через SoundUtil), ванильную
     * музыку (категория MUSIC) глушим на всех экранах меню, чтобы не было наложения
     * двух треков. Как только заходим в мир (музыка меню остановлена) — вернётся
     * обычная игровая музыка.
     */
    @Inject(
        method = "getAdjustedVolume(Lnet/minecraft/client/sound/SoundInstance;)F",
        at = @At("RETURN"),
        cancellable = true
    )
    private void vesence$muteMenuMusic(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        try {
            if (MainScreen.isMenuMusicActive()
                    && sound != null && sound.getCategory() == SoundCategory.MUSIC) {
                cir.setReturnValue(0.0F);
            }
        } catch (Throwable ignored) {
        }
    }
}
