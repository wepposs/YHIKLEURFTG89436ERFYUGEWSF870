package vesence.module.impl.visuals;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.OtherClientPlayerEntity;
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

    private OtherClientPlayerEntity fakePlayer;
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

        if (fakePlayer == null || fakePlayer.isRemoved() || fakePlayer.getEntityWorld() != mc.level) {
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
            Entity entity = mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hitResult ? hitResult.getEntity() : null;
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

        fakePlayer = new OtherClientPlayerEntity(mc.level, profile);
        fakePlayer.setId(FAKE_PLAYER_ID);
        fakePlayer.copyPositionAndRotation(mc.player);
        fakePlayer.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        fakePlayer.lastX = spawnPosition.x;
        fakePlayer.lastY = spawnPosition.y;
        fakePlayer.lastZ = spawnPosition.z;
        fakePlayer.setYRot(spawnYaw);
        fakePlayer.setXRot(spawnPitch);
        fakePlayer.setHeadYaw(spawnHeadYaw);
        fakePlayer.setBodyYaw(spawnBodyYaw);
        fakePlayer.setHealth(mc.player.getHealth());
        fakePlayer.setAbsorptionAmount(mc.player.getAbsorptionAmount());
        fakePlayer.setOnGround(mc.player.onGround());
        fakePlayer.setPose(mc.player.getPose());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            fakePlayer.equipStack(slot, mc.player.getEquippedStack(slot).copy());
        }

        mc.level.addEntity(fakePlayer);
        holdFakePlayerStill();
    }

    private void captureSpawnState() {
        spawnPosition = mc.player.getEntityPos();
        spawnYaw = mc.player.getYRot();
        spawnPitch = mc.player.getXRot();
        spawnHeadYaw = mc.player.getHeadYaw();
        spawnBodyYaw = mc.player.bodyYaw;
    }

    private void holdFakePlayerStill() {
        if (fakePlayer == null || spawnPosition == null) {
            return;
        }

        fakePlayer.setDeltaMovement(Vec3.ZERO);
        fakePlayer.setPosition(spawnPosition.x, spawnPosition.y, spawnPosition.z);
        fakePlayer.lastX = spawnPosition.x;
        fakePlayer.lastY = spawnPosition.y;
        fakePlayer.lastZ = spawnPosition.z;
        fakePlayer.setYRot(spawnYaw);
        fakePlayer.setXRot(spawnPitch);
        fakePlayer.setHeadYaw(spawnHeadYaw);
        fakePlayer.setBodyYaw(spawnBodyYaw);
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
