package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPopPacket;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
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
        if (!this.enable || event.isSend() || mc.getConnection() == null) {
            return;
        }

        if (event.getPacket() instanceof ClientboundResourcePackPushPacket packet) {
            event.cancel();
            scheduleSpoof(packet.id());
        } else if (event.getPacket() instanceof ClientboundResourcePackPopPacket) {
            event.cancel();
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable || mc.getConnection() == null) {
            return;
        }

        long now = System.currentTimeMillis();
        while (!pendingStatuses.isEmpty() && ((java.util.ArrayDeque<QueuedStatus>) pendingStatuses).peekLast().sendAt <= now) {
            QueuedStatus queued = pendingStatuses.poll();
            if (queued != null) {
                NetworkUtils.sendSilentPacket(
                        new ServerboundResourcePackPacket(queued.id, queued.status)
                );
            }
        }
    }

    private void scheduleSpoof(UUID id) {
        if (id == null) return;

        long now = System.currentTimeMillis();
        pendingStatuses.removeIf(status -> status.id.equals(id));

        NetworkUtils.sendSilentPacket(
                new ServerboundResourcePackPacket(id, ServerboundResourcePackPacket.Action.ACCEPTED)
        );

        pendingStatuses.add(new QueuedStatus(id, ServerboundResourcePackPacket.Action.DOWNLOADED, now + 150L));
        pendingStatuses.add(new QueuedStatus(id, ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED, now + 400L));
    }

    @Override
    public void onDisable() {
        pendingStatuses.clear();
        super.onDisable();
    }

    private record QueuedStatus(UUID id, ServerboundResourcePackPacket.Action status, long sendAt) {
    }
}