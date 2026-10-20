package vesence.mixin;

import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngine.PlayResult;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.sounds.SoundSource;
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
@Mixin({SoundEngine.class})
public abstract class SoundSystemMixin {
   @Shadow
   private Map<SoundInstance, ChannelHandle> instanceToChannel;

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
            if (instanceToChannel.isEmpty()) return;
            for (SoundInstance playing : new java.util.ArrayList<>(instanceToChannel.keySet())) {
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
        method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F",
        at = @At("RETURN"),
        cancellable = true
    )
    private void vesence$muteMenuMusic(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        try {
            if (MainScreen.isMenuMusicActive()
                    && sound != null && sound.getSource() == SoundSource.MUSIC) {
                cir.setReturnValue(0.0F);
            }
        } catch (Throwable ignored) {
        }
    }
}
