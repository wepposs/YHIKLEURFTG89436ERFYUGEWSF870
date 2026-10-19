package vesence.module.impl.visuals.custompet;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

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
    public static final Identifier ID = Identifier.of("vesence", "custom_pet");

    public static final RegistryKey<EntityType<?>> KEY =
            RegistryKey.of(RegistryKeys.ENTITY_TYPE, ID);

    public static final EntityType<CustomPetEntity> CUSTOM_PET =
            EntityType.Builder.<CustomPetEntity>create(CustomPetEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
                    .maxTrackingRange(10)
                    .disableSummon()
                    .disableSaving()
                    .build(KEY);

    private CustomPetRegistry() {
    }

    /** Регистрирует тип сущности и атрибуты. Вызывать в инициализации мода. */
    public static void register() {
        Registry.register(Registries.ENTITY_TYPE, ID, CUSTOM_PET);
        FabricDefaultAttributeRegistry.register(CUSTOM_PET, FrogEntity.createFrogAttributes());
    }
}
