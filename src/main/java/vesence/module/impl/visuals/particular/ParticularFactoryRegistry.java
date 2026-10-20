package vesence.module.impl.visuals.particular;

import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import vesence.module.impl.visuals.particular.leaves.BigLeafParticle;
import vesence.module.impl.visuals.particular.leaves.ConiferLeafParticle;
import vesence.module.impl.visuals.particular.leaves.LeafParticle;
import vesence.module.impl.visuals.particular.leaves.SpinningLeafParticle;
import vesence.module.impl.visuals.particular.splashes.WaterSplashEmitterParticle;
import vesence.module.impl.visuals.particular.splashes.WaterSplashFoamParticle;
import vesence.module.impl.visuals.particular.splashes.WaterSplashParticle;
import vesence.module.impl.visuals.particular.splashes.WaterSplashRingParticle;

public final class ParticularFactoryRegistry {
    private ParticularFactoryRegistry() {
    }

    public static void register() {
        ParticularParticles.bootstrap();

        ParticleProviderRegistry registry = ParticleProviderRegistry.getInstance();

        // Leaves
        registry.register(ParticularParticles.OAK_LEAF, LeafParticle.Factory::new);
        registry.register(ParticularParticles.BIRCH_LEAF, LeafParticle.Factory::new);
        registry.register(ParticularParticles.SPRUCE_LEAF, ConiferLeafParticle.Factory::new);
        registry.register(ParticularParticles.JUNGLE_LEAF, BigLeafParticle.Factory::new);
        registry.register(ParticularParticles.ACACIA_LEAF, SpinningLeafParticle.Factory::new);
        registry.register(ParticularParticles.DARK_OAK_LEAF, BigLeafParticle.Factory::new);

        // Effects
        registry.register(ParticularParticles.WATER_RIPPLE, WaterRippleParticle.Factory::new);
        registry.register(ParticularParticles.ENDER_BUBBLE, EnderBubbleParticle.Factory::new);
        registry.register(ParticularParticles.ENDER_BUBBLE_POP, EnderBubbleParticle.Factory::new);
        registry.register(ParticularParticles.CAVE_DUST, CaveDustParticle.Factory::new);
        registry.register(ParticularParticles.FIREFLY, FireflyParticle.Factory::new);
        registry.register(ParticularParticles.WATERFALL_SPRAY, WaterfallSprayParticle.Factory::new);
        registry.register(ParticularParticles.CASCADE, CascadeParticle.Factory::new);

        // Splashes
        registry.register(ParticularParticles.WATER_SPLASH_EMITTER, new WaterSplashEmitterParticle.Factory());
        registry.register(ParticularParticles.WATER_SPLASH, WaterSplashParticle.Factory::new);
        registry.register(ParticularParticles.WATER_SPLASH_FOAM, WaterSplashFoamParticle.Factory::new);
        registry.register(ParticularParticles.WATER_SPLASH_RING, WaterSplashRingParticle.Factory::new);
    }
}
