package vesence.hmi.script_wrappers;

import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;

public class S {
    public void playSound(String id, double volume) {
        MinecraftClient.getInstance().player.playSound(SoundEvent.of((Identifier)Identifier.ofVanilla((String)id)), (float)volume, 1.0f);
    }
}

