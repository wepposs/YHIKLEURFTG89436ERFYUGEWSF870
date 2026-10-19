package vesence.hmi.patricles;

import vesence.hmi.patricles.Particle;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.math.MatrixStack;

public class ParticleManager {
    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha, ticker));
    }

    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha));
    }

    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, List<Identifier> keyframes, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        if (particles.stream().noneMatch(particle -> particle.lifecycleType == "KEYFRAME" && particle.hand == hand)) {
            particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, keyframes, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha, ticker));
        }
    }

    public void addParticle(ArrayList<Particle> particles, boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker, MatrixStack p_matrices) {
        particleRenderType = particleRenderType.intern();
        lifecycleType = lifecycleType.intern();
        space = space.intern();
        particles.add(new Particle(gravity, x, y, z, dx, dy, dz, rx, ry, rz, drx, dry, drz, maxScale, texture, space, hand, lifecycleType, particleRenderType, particleLifetime, alpha, ticker, p_matrices));
    }
}

