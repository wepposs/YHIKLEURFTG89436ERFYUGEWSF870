package vesence.hmi.script_wrappers;

import net.minecraft.util.ResourceLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;

public class S {
    public void playSound(String id, double volume) {
        Minecraft.getInstance().player.playSound(SoundEvent.of((ResourceLocation)ResourceLocation.ofVanilla((String)id)), (float)volume, 1.0f);
    }
}

