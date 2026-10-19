package vesence.hmi.classes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;

public class Sound {
    private SoundEvent sound;

    public void play(float volume, float pitch) {
        MinecraftClient.getInstance().player.playSound(this.sound, volume, pitch);
    }
}

