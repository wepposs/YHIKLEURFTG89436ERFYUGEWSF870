package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.EventMotion;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

/**
 * AirStuck - замораживает игрока в воздухе
 * Портировано из Monoton
 */
@IModule(
        name = "AirStuck",
        description = "Замораживает игрока в воздухе",
        category = Category.MOVEMENT,
        bind = 0
)
@Environment(EnvType.CLIENT)
public class AirStuck extends Module {
    private boolean oldIsFlying;
    private float savedYaw;
    private float savedPitch;
    private float savedBodyYaw;

    @EventInit
    public void onTick(ClientTickEvent event) {
        if (mc.player == null) {
            return;
        }

        // Auto-disable on death
        if (isPlayerDead()) {
            if (mc.player.deathTime < 1) {
                toggle();
            }
        }

        // Enable noclip and freeze movement
        mc.player.noClip = true;
        mc.player.setOnGround(false);
        mc.player.setVelocity(0.0, 0.0, 0.0);
        mc.player.getAbilities().flying = true;

        // Stop sprinting
        if (mc.player.isSprinting()) {
            mc.player.setSprinting(false);
        }

        // Freeze rotation
        mc.player.headYaw = savedYaw;
        mc.player.bodyYaw = savedBodyYaw;
        mc.player.setPitch(savedPitch);
    }

    @EventInit
    public void onMotion(EventMotion event) {
        if (mc.player == null) {
            return;
        }

        // Send position packet every 10 ticks to prevent timeout
        if (mc.player.age % 10 == 0) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(mc.player.isOnGround(), mc.player.horizontalCollision));
        }

        // Cancel motion
        event.cancel();
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (!event.isSend()) {
            return;
        }

        // Cancel movement packets
        if (event.getPacket() instanceof PlayerMoveC2SPacket 
                || event.getPacket() instanceof ClientCommandC2SPacket) {
            event.cancel();
        }

        // Auto-disable on respawn
        if (event.getPacket() instanceof GameJoinS2CPacket) {
            toggle();
        }
    }

    private boolean isPlayerDead() {
        return mc.player != null 
                && mc.player.getHealth() < 1.0F 
                && mc.currentScreen instanceof DeathScreen;
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            oldIsFlying = mc.player.getAbilities().flying;
            
            // Stop all input
            mc.player.input = new Input();
            
            // Save current rotation
            savedYaw = mc.player.getYaw();
            savedPitch = mc.player.getPitch();
            savedBodyYaw = mc.player.bodyYaw;
        }
        super.onEnable();
    }

    @Override
    public void onDisable() {
        if (mc.player != null) {
            // Restore input
            if (mc.player.input.getClass() == Input.class) {
                mc.player.input = new KeyboardInput(mc.options);
            }
            
            // Restore flying state
            mc.player.getAbilities().flying = oldIsFlying;
            
            // Disable noclip
            mc.player.noClip = false;
        }
        super.onDisable();
    }
}
