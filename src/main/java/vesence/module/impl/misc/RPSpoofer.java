package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.c2s.common.ResourcePackStatusC2SPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackRemoveS2CPacket;
import net.minecraft.network.packet.s2c.common.ResourcePackSendS2CPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.utils.network.NetworkUtils;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

@IModule(name = "RPSpoofer", description = "Позволяет играть без серверного ресурспака", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class RPSpoofer extends Module {

    private final Queue<QueuedStatus> pendingStatuses = new ArrayDeque<>();

    @EventInit
    public void onPacket(EventPacket event) {
        if (!this.enable || event.isSend() || mc.getNetworkHandler() == null) {
            return;
        }

        if (event.getPacket() instanceof ResourcePackSendS2CPacket packet) {
            event.cancel();
            scheduleSpoof(packet.id());
        } else if (event.getPacket() instanceof ResourcePackRemoveS2CPacket) {
            event.cancel();
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable || mc.getNetworkHandler() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        while (!pendingStatuses.isEmpty() && pendingStatuses.peek().sendAt <= now) {
            QueuedStatus queued = pendingStatuses.poll();
            if (queued != null) {
                NetworkUtils.sendSilentPacket(
                        new ResourcePackStatusC2SPacket(queued.id, queued.status)
                );
            }
        }
    }

    private void scheduleSpoof(UUID id) {
        if (id == null) return;

        long now = System.currentTimeMillis();
        pendingStatuses.removeIf(status -> status.id.equals(id));

        NetworkUtils.sendSilentPacket(
                new ResourcePackStatusC2SPacket(id, ResourcePackStatusC2SPacket.Status.ACCEPTED)
        );

        pendingStatuses.add(new QueuedStatus(id, ResourcePackStatusC2SPacket.Status.DOWNLOADED, now + 150L));
        pendingStatuses.add(new QueuedStatus(id, ResourcePackStatusC2SPacket.Status.SUCCESSFULLY_LOADED, now + 400L));
    }

    @Override
    public void onDisable() {
        pendingStatuses.clear();
        super.onDisable();
    }

    private record QueuedStatus(UUID id, ResourcePackStatusC2SPacket.Status status, long sendAt) {
    }
}