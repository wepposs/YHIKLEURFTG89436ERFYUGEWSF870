package vesence.module.impl.visuals;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

import java.util.UUID;

@IModule(name = "FakePlayer", description = "Спавнит локального фейкового игрока", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class FakePlayer extends Module {

    private static final int FAKE_PLAYER_ID = -21000;

    private RemotePlayer fakePlayer;
    private Vec3 spawnPosition;
    private float spawnYaw;
    private float spawnPitch;
    private float spawnHeadYaw;
    private float spawnBodyYaw;

    @Override
    public void onEnable() {
        super.onEnable();
        spawnFakePlayer();
    }

    @Override
    public void onDisable() {
        removeFakePlayer();
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable || mc.player == null || mc.level == null) {
            return;
        }

        if (fakePlayer == null || fakePlayer.isRemoved() || fakePlayer.level() != mc.level) {
            spawnFakePlayer();
            return;
        }

        holdFakePlayerStill();
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (!this.enable || !event.isSend() || mc.level == null || fakePlayer == null || fakePlayer.isRemoved()) {
            return;
        }

        if (event.getPacket() instanceof ServerboundInteractPacket) {
            Entity entity = mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult hitResult ? hitResult.getEntity() : null;
            if (entity == fakePlayer) {
                event.cancel();
            }
        }
    }

    private void spawnFakePlayer() {
        if (mc.player == null || mc.level == null) {
            return;
        }

        removeFakePlayer();
        captureSpawnState();

        GameProfile profile = new GameProfile(UUID.randomUUID(), "FakePlayer");

        fakePlayer = new RemotePlayer(mc.level, profile);
        fakePlayer.setId(FAKE_PLAYER_ID);
        fakePlayer.setPos(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        fakePlayer.setYRot(mc.player.getYRot());
        fakePlayer.setXRot(mc.player.getXRot());
        fakePlayer.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        fakePlayer.xo = spawnPosition.x;
        fakePlayer.yo = spawnPosition.y;
        fakePlayer.zo = spawnPosition.z;
        fakePlayer.setYRot(spawnYaw);
        fakePlayer.setXRot(spawnPitch);
        fakePlayer.setYHeadRot(spawnHeadYaw);
        fakePlayer.setYBodyRot(spawnBodyYaw);
        fakePlayer.setHealth(mc.player.getHealth());
        fakePlayer.setAbsorptionAmount(mc.player.getAbsorptionAmount());
        fakePlayer.setOnGround(mc.player.onGround());
        fakePlayer.setPose(mc.player.getPose());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            fakePlayer.setItemSlot(slot, mc.player.getItemBySlot(slot).copy());
        }

        mc.level.addEntity(fakePlayer);
        holdFakePlayerStill();
    }

    private void captureSpawnState() {
        spawnPosition = mc.player.position();
        spawnYaw = mc.player.getYRot();
        spawnPitch = mc.player.getXRot();
        spawnHeadYaw = mc.player.getYHeadRot();
        spawnBodyYaw = mc.player.yBodyRot;
    }

    private void holdFakePlayerStill() {
        if (fakePlayer == null || spawnPosition == null) {
            return;
        }

        fakePlayer.setDeltaMovement(Vec3.ZERO);
        fakePlayer.setPos(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        fakePlayer.xo = spawnPosition.x;
        fakePlayer.yo = spawnPosition.y;
        fakePlayer.zo = spawnPosition.z;
        fakePlayer.setYRot(spawnYaw);
        fakePlayer.setXRot(spawnPitch);
        fakePlayer.setYHeadRot(spawnHeadYaw);
        fakePlayer.setYBodyRot(spawnBodyYaw);
    }

    private void removeFakePlayer() {
        if (fakePlayer == null) {
            return;
        }

        if (mc.level != null && !fakePlayer.isRemoved()) {
            mc.level.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayer.discard();
        fakePlayer = null;
    }
}
