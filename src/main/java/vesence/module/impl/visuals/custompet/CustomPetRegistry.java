package vesence.module.impl.visuals.custompet;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;

/**
 * Регистрация выделенного {@link EntityType} для кастомного питомца.
 *
 * Используем отдельный тип (а не {@code EntityType.FROG}), потому что в системе рендера
 * 1.21.11 рендерер выбирается по {@code EntityRenderState}/типу сущности на этапе отрисовки,
 * и переиспользование FROG приводило бы к вызову ванильного FrogEntityRenderer (краш каста
 * render-state). Свой тип в карте рендереров корректно резолвится на {@link CustomPetRenderer}.
 *
 * Сущность чисто клиентская: создаётся локально и добавляется в мир через {@code addEntity},
 * сервер о ней не знает. Регистрация типа нужна только чтобы dispatcher знал её рендерер.
 */
public final class CustomPetRegistry {
    public static final Identifier ID = Identifier.fromNamespaceAndPath("vesence", "custom_pet");

    public static final ResourceKey<EntityType<?>> KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, ID);

    public static final EntityType<CustomPetEntity> CUSTOM_PET =
            EntityType.Builder.<CustomPetEntity>of(CustomPetEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .noSummon()
                    .noSave()
                    .build(KEY);

    private CustomPetRegistry() {
    }

    /** Регистрирует тип сущности и атрибуты. Вызывать в инициализации мода. */
    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, ID, CUSTOM_PET);
        FabricDefaultAttributeRegistry.register(CUSTOM_PET, Frog.createAttributes());
    }
}
