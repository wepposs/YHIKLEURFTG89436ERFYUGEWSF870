package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.ResourceLocation;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

@Environment(EnvType.CLIENT)
@IModule(
        name = "Cape",
        description = "Кастомный плащ",
        category = Category.VISUALS
)
public class Cape extends Module {

    public static final ResourceLocation STATIC_CAPE =
            ResourceLocation.of("vesence", "textures/cape/cape.png");

    public static final ResourceLocation STATIC_ELYTRA =
            ResourceLocation.of("vesence", "textures/cape/cape.png");

    public Cape() {
        // Настроек нет специально:
        // при ПКМ по Cape в GUI ничего не должно происходить.
    }

    /**
     * Возвращает статичную текстуру плаща.]
     */
    public ResourceLocation getCapeTexture() {
        return STATIC_CAPE;
    }

    /**
     * Возвращает текстуру элитры.
     */
    public ResourceLocation getElytraTexture() {
        return STATIC_ELYTRA;
    }
}