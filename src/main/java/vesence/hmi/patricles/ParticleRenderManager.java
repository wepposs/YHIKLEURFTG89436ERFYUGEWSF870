package vesence.hmi.patricles;

import java.util.ArrayList;
import java.util.Iterator;
import net.minecraft.util.Hand;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class ParticleRenderManager {
    @SuppressWarnings("StringEquality")
    public static void draw(ArrayList<Particle> particles, MatrixStack matrices, OrderedRenderCommandQueue queue, String space, Hand hand, int light, AbstractClientPlayerEntity player) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            if (particle.dead) {
                iterator.remove();
                continue;
            }
            if (particle.space != space || particle.hand != hand) continue;
            if (particle.p_matrices != null) {
                particle.render(particle.p_matrices, queue, light, player);
            } else {
                particle.render(matrices, queue, light, player);
            }
            particle.tick();
        }
    }
}
