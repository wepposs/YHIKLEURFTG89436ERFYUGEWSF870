package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.Vesence;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

@IModule(name = "CameraClip", description = "Камера видит сквозь блоки и стены", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class CameraClip extends Module {

    private static CameraClip instance;

    public CameraClip() {
        instance = this;
    }

    public static boolean isActive() {
        return instance != null && instance.enable;
    }
}
