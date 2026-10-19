package vesence.hmi.patricles;

import net.minecraft.util.ResourceLocation;

public class Texture {
    public ResourceLocation of(String namespace, String path) {
        return ResourceLocation.of((String)namespace, (String)path);
    }
}

