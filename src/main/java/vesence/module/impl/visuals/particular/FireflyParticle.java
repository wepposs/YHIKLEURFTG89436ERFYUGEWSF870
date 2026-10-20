package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import net.minecraft.util.RandomSource;

public class FireflyParticle extends SingleQuadParticle {
    private final SimplexNoise noise;
    private int ageOffset;
    private int ticksUntilNextSwitch = 40;
    private boolean lit;

    protected FireflyParticle(ClientLevel level, double x, double y, double z, SpriteSet provider) {
        super(level, x, y, z, provider.get(level.getRandom()));
        this.setSprite(provider.get(this.random));
        this.hasPhysics = false;
        this.alpha = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = 200;
        this.quadSize = 0.25F;
        this.noise = new SimplexNoise(new net.minecraft.world.level.levelgen.LegacyRandomSource(this.random.nextLong()));

        this.rCol = 187.0F / 255.0F;
        this.gCol = 1.0F;
        this.bCol = 107.0F / 255.0F;
        if (level.getRandom().nextInt(10) == 1) {
            this.rCol = 107.0F / 255.0F;
            this.gCol = 250.0F / 255.0F;
            this.bCol = 1.0F;
            if (level.getRandom().nextInt(10) == 1) {
                this.rCol = 1.0F;
                this.gCol = 124.0F / 255.0F;
                this.bCol = 107.0F / 255.0F;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.ageOffset += 1;
        if (--this.ticksUntilNextSwitch <= 0) {
            if (this.lit) {
                this.lit = false;
                this.ticksUntilNextSwitch = (20 * 2) + this.random.nextInt((20 * 4) - (20 * 2) + 1);
            } else {
                this.lit = true;
                this.ticksUntilNextSwitch = 10 + this.random.nextInt(20 - 10 + 1);
            }
        }

        this.alpha = this.lit && (this.lifetime - this.age) > 3 ? Math.min(1.0F, this.alpha + 0.33F) : Math.max(0.0F, this.alpha - 0.33F);

        double broadSpeed = 0.1D;
        double broadNoise = 1.0D / 300.0D;
        this.xd = this.noise.get(this.age * broadNoise, this.age * broadNoise) * broadSpeed;
        this.yd = this.noise.get((this.age + this.ageOffset) * broadNoise - 50.0D, (this.age + this.ageOffset) * broadNoise + 100.0D) * broadSpeed * 0.5D;
        this.zd = this.noise.get(this.age * broadNoise + 100.0D, this.age * broadNoise - 50.0D) * broadSpeed;

        double fineSpeed = 1.0D / (10.0D + Math.sin(Math.PI + this.age / 30.0D) * 2.0D);
        double fineNoise = 1.0D / 30.0D;
        this.xd += this.noise.get(this.age * fineNoise, this.age * fineNoise) * fineSpeed * 0.5D;
        this.yd += this.noise.get((this.age + this.ageOffset) * fineNoise - 50.0D, (this.age + this.ageOffset) * fineNoise + 100.0D) * fineSpeed;
        this.zd += this.noise.get(this.age * fineNoise + 100.0D, this.age * fineNoise - 50.0D) * fineSpeed * 0.5D;
    }

    @Override
    public int getLightCoords(float tint) {
        return 240;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }

    public static class Factory implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd, RandomSource random) {
            return new FireflyParticle(level, x, y, z, this.provider);
        }
    }
}
