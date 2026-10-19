package vesence.hmi.patricles;

import vesence.hmi.patricles.Particle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;

public class ParticleManager {
    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, ResourceLocation texture, String space, InteractionHand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha, ticker));
    }

    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, ResourceLocation texture, String space, InteractionHand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha));
    }

    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, ResourceLocation texture, List<ResourceLocation> keyframes, String space, InteractionHand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        if (particles.stream().noneMatch(particle -> particle.lifecycleType == "KEYFRAME" && particle.hand == hand)) {
            particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, keyframes, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha, ticker));
        }
    }

    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, ResourceLocation texture, String space, InteractionHand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker, PoseStack p_matrices) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha, ticker, p_matrices));
    }
}

