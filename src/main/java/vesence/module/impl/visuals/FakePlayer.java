package vesence.module.impl.visuals;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.util.math.Vec3d;
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

    private OtherClientPlayerEntity fakePlayer;
    private Vec3d spawnPosition;
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
        if (!this.enable || mc.player == null || mc.world == null) {
            return;
        }

        if (fakePlayer == null || fakePlayer.isRemoved() || fakePlayer.getEntityWorld() != mc.world) {
            spawnFakePlayer();
            return;
        }

        holdFakePlayerStill();
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (!this.enable || !event.isSend() || mc.world == null || fakePlayer == null || fakePlayer.isRemoved()) {
            return;
        }

        if (event.getPacket() instanceof PlayerInteractEntityC2SPacket) {
            Entity entity = mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hitResult ? hitResult.getEntity() : null;
            if (entity == fakePlayer) {
                event.cancel();
            }
        }
    }

    private void spawnFakePlayer() {
        if (mc.player == null || mc.world == null) {
            return;
        }

        removeFakePlayer();
        captureSpawnState();

        GameProfile profile = new GameProfile(UUID.randomUUID(), "FakePlayer");

        fakePlayer = new OtherClientPlayerEntity(mc.world, profile);
        fakePlayer.setId(FAKE_PLAYER_ID);
        fakePlayer.copyPositionAndRotation(mc.player);
        fakePlayer.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        fakePlayer.lastX = spawnPosition.x;
        fakePlayer.lastY = spawnPosition.y;
        fakePlayer.lastZ = spawnPosition.z;
        fakePlayer.setYaw(spawnYaw);
        fakePlayer.setPitch(spawnPitch);
        fakePlayer.setHeadYaw(spawnHeadYaw);
        fakePlayer.setBodyYaw(spawnBodyYaw);
        fakePlayer.setHealth(mc.player.getHealth());
        fakePlayer.setAbsorptionAmount(mc.player.getAbsorptionAmount());
        fakePlayer.setOnGround(mc.player.isOnGround());
        fakePlayer.setPose(mc.player.getPose());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            fakePlayer.equipStack(slot, mc.player.getEquippedStack(slot).copy());
        }

        mc.world.addEntity(fakePlayer);
        holdFakePlayerStill();
    }

    private void captureSpawnState() {
        spawnPosition = mc.player.getEntityPos();
        spawnYaw = mc.player.getYaw();
        spawnPitch = mc.player.getPitch();
        spawnHeadYaw = mc.player.getHeadYaw();
        spawnBodyYaw = mc.player.bodyYaw;
    }

    private void holdFakePlayerStill() {
        if (fakePlayer == null || spawnPosition == null) {
            return;
        }

        fakePlayer.setVelocity(Vec3d.ZERO);
        fakePlayer.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        fakePlayer.lastX = spawnPosition.x;
        fakePlayer.lastY = spawnPosition.y;
        fakePlayer.lastZ = spawnPosition.z;
        fakePlayer.setYaw(spawnYaw);
        fakePlayer.setPitch(spawnPitch);
        fakePlayer.setHeadYaw(spawnHeadYaw);
        fakePlayer.setBodyYaw(spawnBodyYaw);
    }

    private void removeFakePlayer() {
        if (fakePlayer == null) {
            return;
        }

        if (mc.world != null && !fakePlayer.isRemoved()) {
            mc.world.removeEntity(fakePlayer.getId(), Entity.RemovalReason.DISCARDED);
        }
        fakePlayer.discard();
        fakePlayer = null;
    }
}
