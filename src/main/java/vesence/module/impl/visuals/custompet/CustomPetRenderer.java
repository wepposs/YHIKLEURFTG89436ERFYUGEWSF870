package vesence.module.impl.visuals.custompet;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;

import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.base.BoneSnapshots;
import software.bernie.geckolib.renderer.base.RenderPassInfo;

/**
 * Рендерер питомца. Перенесено из RelevantPremiumpp4 (GeckoLib 4.8 -> GeckoLib 5.4).
 *
 * В GeckoLib 5.x GeoEntityRenderer — двухпараметрический generic {@code <T, R>}, render-state
 * формируется через {@link #updateRenderState}. Радиус тени — {@code shadowRadius}.
 *
 * Скрытие костей под вариант (зонты/шляпы/удочки/полёт) делается в
 * {@link #adjustModelBonesForRender} через {@link BoneSnapshots#get}.skipRender — это замена
 * removed-в-5.x метода {@code GeoModel.setCustomAnimations} из 4.8. Без этого все кости (лилия,
 * зонты, шляпы) рендерились разом — отсюда баг «всегда в полёте» и одинаковые скины.
 *
 * Локальная сущность одна, поэтому текущее состояние читается из статических полей,
 * заполняемых в {@link #updateRenderState}.
 */
public class CustomPetRenderer extends GeoEntityRenderer<CustomPetEntity, LivingEntityRenderState> {

    /** Полный список костей-аксессуаров, которыми управляем (всё, что не относится к телу/лапам). */
    private static final Set<String> MANAGED_BONES = Set.of(
            "body_default", "body_merchant", "leaf", "gardener_hat", "watering_can", "sourcerer_hat",
            "accessories", "fishing_rod", "fishing_rod_2", "fishing_rod_3", "guitar", "flute", "bongo",
            "bass", "umbrella", "umbrella2", "umbrella3", "fisherman_umbrella", "fisherman_umbrella2",
            "fisherman_umbrella3", "pride");

    // Снимок состояния последней сущности (одна локальная сущность — гонок нет).
    private static volatile CustomPetVariant currentVariant = CustomPetVariant.NITWIT;
    private static volatile boolean currentUmbrella = false;
    private static volatile boolean currentAirborne = false;

    public CustomPetRenderer(EntityRendererFactory.Context context) {
        super(context, new CustomPetModel());
        this.shadowRadius = 0.35f;
    }

    @Override
    public void updateRenderState(CustomPetEntity entity, LivingEntityRenderState renderState, float partialTick) {
        currentVariant = entity.getPetVariant();
        currentUmbrella = entity.shouldUseUmbrella();
        currentAirborne = entity.isAirborneMode();
        // Пользовательский размер питомца (слайдер в модуле Pet). withScale задаёт
        // scaleWidth/scaleHeight, которые GeckoLib применяет в scaleModelForRender.
        float scale = entity.getPetScale();
        this.withScale(scale);
        this.shadowRadius = 0.35f * scale;
        super.updateRenderState(entity, renderState, partialTick);
    }

    @Override
    public float getMotionAnimThreshold(CustomPetEntity animatable) {
        return 5.0E-4f;
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<LivingEntityRenderState> renderPassInfo, BoneSnapshots snapshots) {
        super.adjustModelBonesForRender(renderPassInfo, snapshots);

        CustomPetVariant variant = currentVariant;
        boolean inRain = currentUmbrella;
        boolean airborne = currentAirborne;

        // Набор костей, которые ДОЛЖНЫ быть видимыми для текущего варианта/состояния.
        Set<String> desired = new HashSet<>();
        desired.add(variant.usesMerchantBody() ? "body_merchant" : "body_default");
        if (variant.usesMerchantLeaf() && !inRain) {
            desired.add("leaf");
        }
        if (variant.usesGardenerGear()) {
            desired.add("gardener_hat");
            desired.add("watering_can");
        }
        if (variant.usesSorcererHat()) {
            desired.add("sourcerer_hat");
        }
        if (variant.usesFishermanGear()) {
            desired.add("accessories");
            desired.add("fishing_rod");
            desired.add("fishing_rod_2");
            desired.add("fishing_rod_3");
        }
        if (airborne) {
            desired.add(variant.usesFishermanGear() ? "fisherman_umbrella2" : "umbrella2");
        } else if (inRain) {
            desired.add(variant.usesFishermanGear() ? "fisherman_umbrella3" : "umbrella3");
        }

        // Скрываем все управляемые кости, кроме нужных.
        for (String bone : MANAGED_BONES) {
            boolean hidden = !desired.contains(bone);
            final boolean skip = hidden;
            snapshots.ifPresent(bone, snap -> snap.skipRender(skip).skipChildrenRender(skip));
        }
    }
}
