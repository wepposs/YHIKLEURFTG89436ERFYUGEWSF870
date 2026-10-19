package vesence.module.impl.visuals.particular;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.impl.EventChangeWorld;
import vesence.event.impl.EventUpdate;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.impl.visuals.particular.splashes.WaterCrownRenderer;

@IModule(name = "Particular", description = "Port moda Particular bez nastroek", category = Category.VISUALS)
@Environment(EnvType.CLIENT)
public class Particular extends Module {
    @Override
    public void onEnable() {
        ParticularParticles.bootstrap();
        super.onEnable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        ParticularEffects.tick();
    }

    @EventInit
    public void onWorldChange(EventChangeWorld event) {
        ParticularEffects.clear();
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (this.enable) {
            WaterCrownRenderer.render(event);
        }
    }

    @Override
    public void onDisable() {
        ParticularEffects.clear();
        super.onDisable();
    }
}
