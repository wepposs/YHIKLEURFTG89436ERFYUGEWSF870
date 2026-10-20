package vesence.hmi.patricles;

import net.minecraft.resources.Identifier;

public class Texture {
    public Identifier of(String namespace, String path) {
        return Identifier.fromNamespaceAndPath((String)namespace, (String)path);
    }
}

