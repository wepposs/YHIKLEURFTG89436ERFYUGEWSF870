package vesence.module.impl.visuals.particular;

import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.math.noise.SimplexNoiseSampler;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.util.math.random.Random;

public class FireflyParticle extends BillboardParticle {
    private final SimplexNoiseSampler noise;
    private int ageOffset;
    private int ticksUntilNextSwitch = 40;
    private boolean lit;

    protected FireflyParticle(ClientLevel world, double x, double y, double z, SpriteSet provider) {
        super(world, x, y, z, provider.getSprite(world.getRandom()));
        this.setSprite(provider.getSprite(this.random));
        this.collidesWithWorld = false;
        this.alpha = 0.0F;
        this.velocityX = 0.0D;
        this.velocityY = 0.0D;
        this.velocityZ = 0.0D;
        this.maxAge = 200;
        this.scale = 0.25F;
        this.noise = new SimplexNoiseSampler(new CheckedRandom(this.random.nextLong()));

        this.red = 187.0F / 255.0F;
        this.green = 1.0F;
        this.blue = 107.0F / 255.0F;
        if (world.random.nextInt(10) == 1) {
            this.red = 107.0F / 255.0F;
            this.green = 250.0F / 255.0F;
            this.blue = 1.0F;
            if (world.random.nextInt(10) == 1) {
                this.red = 1.0F;
                this.green = 124.0F / 255.0F;
                this.blue = 107.0F / 255.0F;
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

        this.alpha = this.lit && (this.maxAge - this.tickCount) > 3 ? Math.min(1.0F, this.alpha + 0.33F) : Math.max(0.0F, this.alpha - 0.33F);

        double broadSpeed = 0.1D;
        double broadNoise = 1.0D / 300.0D;
        this.velocityX = this.noise.sample(this.tickCount * broadNoise, this.tickCount * broadNoise) * broadSpeed;
        this.velocityY = this.noise.sample((this.tickCount + this.ageOffset) * broadNoise - 50.0D, (this.tickCount + this.ageOffset) * broadNoise + 100.0D) * broadSpeed * 0.5D;
        this.velocityZ = this.noise.sample(this.tickCount * broadNoise + 100.0D, this.tickCount * broadNoise - 50.0D) * broadSpeed;

        double fineSpeed = 1.0D / (10.0D + Math.sin(Math.PI + this.tickCount / 30.0D) * 2.0D);
        double fineNoise = 1.0D / 30.0D;
        this.velocityX += this.noise.sample(this.tickCount * fineNoise, this.tickCount * fineNoise) * fineSpeed * 0.5D;
        this.velocityY += this.noise.sample((this.tickCount + this.ageOffset) * fineNoise - 50.0D, (this.tickCount + this.ageOffset) * fineNoise + 100.0D) * fineSpeed;
        this.velocityZ += this.noise.sample(this.tickCount * fineNoise + 100.0D, this.tickCount * fineNoise - 50.0D) * fineSpeed * 0.5D;
    }

    @Override
    public int getBrightness(float tint) {
        return 240;
    }

    @Override
    public RenderType getRenderType() {
        return RenderType.PARTICLE_ATLAS_TRANSLUCENT;
    }

    public static class Factory implements ParticleFactory<SimpleParticleType> {
        private final SpriteSet provider;

        public Factory(SpriteSet provider) {
            this.provider = provider;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double velocityX, double velocityY, double velocityZ, Random random) {
            return new FireflyParticle(world, x, y, z, this.provider);
        }
    }
}
