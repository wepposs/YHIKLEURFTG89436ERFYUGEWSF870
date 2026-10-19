package vesence.hmi.patricles;

import java.util.ArrayList;
import java.util.Iterator;
import net.minecraft.world.InteractionHand;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.player.AbstractClientPlayer;

public class ParticleRenderManager {
    @SuppressWarnings("StringEquality")
    public static void draw(ArrayList<Particle> particles, PoseStack matrices, OrderedRenderCommandQueue queue, String space, InteractionHand hand, int light, AbstractClientPlayer player) {
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
