package vesence.hmi.classes;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;

public class Sound {
    private SoundEvent sound;

    public void play(float volume, float pitch) {
        Minecraft.getInstance().player.playSound(this.sound, volume, pitch);
    }
}

