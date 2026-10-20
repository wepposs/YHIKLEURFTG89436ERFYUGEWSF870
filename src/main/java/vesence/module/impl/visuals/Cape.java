package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.Identifier;
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

    public static final Identifier STATIC_CAPE =
            Identifier.fromNamespaceAndPath("vesence", "textures/cape/cape.png");

    public static final Identifier STATIC_ELYTRA =
            Identifier.fromNamespaceAndPath("vesence", "textures/cape/cape.png");

    public Cape() {
        // Настроек нет специально:
        // при ПКМ по Cape в GUI ничего не должно происходить.
    }

    /**
     * Возвращает статичную текстуру плаща.]
     */
    public Identifier getCapeTexture() {
        return STATIC_CAPE;
    }

    /**
     * Возвращает текстуру элитры.
     */
    public Identifier getElytraTexture() {
        return STATIC_ELYTRA;
    }
}