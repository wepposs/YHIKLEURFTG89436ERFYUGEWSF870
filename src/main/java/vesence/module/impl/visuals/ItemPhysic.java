package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

@IModule(name = "Item Physic", description = "Придает предметам физику", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ItemPhysic extends Module {
    public ItemPhysic() {
        super();
    }
}
