package vesence.hmi.patricles;

import net.minecraft.util.Identifier;

public class Texture {
    public Identifier of(String namespace, String path) {
        return Identifier.of((String)namespace, (String)path);
    }
}

