package vesence.module.impl.visuals.custompet;

import net.minecraft.util.ResourceLocation;

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
    private static final ResourceLocation MODEL = ResourceLocation.of("vesence", "custom_pet");
    private static final ResourceLocation ANIMATIONS = ResourceLocation.of("vesence", "custom_pet");
    private static final ResourceLocation TEXTURE = ResourceLocation.of("vesence", "textures/entity/custom_pet.png");

    @Override
    public ResourceLocation getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CustomPetEntity animatable) {
        return ANIMATIONS;
    }
}
