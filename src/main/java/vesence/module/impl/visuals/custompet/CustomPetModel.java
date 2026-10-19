package vesence.module.impl.visuals.custompet;

import net.minecraft.util.Identifier;

import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/**
 * GeoModel питомца. Перенесено из RelevantPremiumpp4 (GeckoLib 4.8 -> GeckoLib 5.4).
 *
 * В GeckoLib 5.x getModelResource/getTextureResource принимают {@link GeoRenderState}.
 * Скрытие костей под вариант (зонты/шляпы/удочки) выполняется в
 * {@link CustomPetRenderer#adjustModelBonesForRender}, поэтому модель/текстура едины.
 */
public class CustomPetModel extends GeoModel<CustomPetEntity> {
    private static final Identifier MODEL = Identifier.of("vesence", "custom_pet");
    private static final Identifier ANIMATIONS = Identifier.of("vesence", "custom_pet");
    private static final Identifier TEXTURE = Identifier.of("vesence", "textures/entity/custom_pet.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(CustomPetEntity animatable) {
        return ANIMATIONS;
    }
}
