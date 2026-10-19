package vesence.module.impl.visuals.particular;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Registry;
import net.minecraft.util.ResourceLocation;

public final class ParticularParticles {
    public static final String MOD_ID = "particular";

    public static final SimpleParticleType OAK_LEAF = register("oak_leaf");
    public static final SimpleParticleType BIRCH_LEAF = register("birch_leaf");
    public static final SimpleParticleType SPRUCE_LEAF = register("spruce_leaf");
    public static final SimpleParticleType JUNGLE_LEAF = register("jungle_leaf");
    public static final SimpleParticleType ACACIA_LEAF = register("acacia_leaf");
    public static final SimpleParticleType DARK_OAK_LEAF = register("dark_oak_leaf");

    public static final SimpleParticleType WATER_RIPPLE = register("water_ripple");
    public static final SimpleParticleType ENDER_BUBBLE = register("ender_bubble");
    public static final SimpleParticleType ENDER_BUBBLE_POP = register("ender_bubble_pop");
    public static final SimpleParticleType CAVE_DUST = register("cave_dust");
    public static final SimpleParticleType FIREFLY = register("firefly");
    public static final SimpleParticleType WATERFALL_SPRAY = register("waterfall_spray");

    public static final SimpleParticleType CASCADE = register("cascade", true);
    public static final SimpleParticleType WATER_SPLASH_EMITTER = register("water_splash_emitter", true);
    public static final SimpleParticleType WATER_SPLASH = register("water_splash", true);
    public static final SimpleParticleType WATER_SPLASH_FOAM = register("water_splash_foam", true);
    public static final SimpleParticleType WATER_SPLASH_RING = register("water_splash_ring", true);

    private ParticularParticles() {
    }

    public static void bootstrap() {
    }

    private static SimpleParticleType register(String name) {
        return register(name, false);
    }

    private static SimpleParticleType register(String name, boolean alwaysShow) {
        return Registry.register(BuiltInRegistries.PARTICLE_TYPE, ResourceLocation.of(MOD_ID, name), FabricParticleTypes.simple(alwaysShow));
    }
}
