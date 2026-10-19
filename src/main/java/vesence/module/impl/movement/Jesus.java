package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.player.FlyExempt;

@IModule(
        name = "Jesus",
        description = "Walking on water",
        category = Category.MOVEMENT,
        bind = -1
)
@Environment(EnvType.CLIENT)
public class Jesus extends Module {

    private final ModeSetting mode = new ModeSetting(
            "Mode",
            "Dolphin",
            "Dolphin",
            "Bounce",
            "Liquid"
    );

    /*
     * Dolphin: how long we hold the swim-up input after each dive.
     */
    private final SliderSetting dolphinHold = new SliderSetting("Dolphin Hold", 3, 1, 10, 1);

    /*
     * Bounce: upward velocity at the surface.
     */
    private final SliderSetting bounceUp = new SliderSetting("Bounce Up", 0.1, 0.02, 0.3, 0.01);

    /*
     * Bounce: horizontal impulse while legs are in fluid.
     */
    private final SliderSetting bounceHBoost = new SliderSetting("Bounce H-Boost", 0.02, 0.0, 0.1, 0.005);

    private final BooleanSetting debug = new BooleanSetting("Debug", false);
    private final BooleanSetting flyExempt = new BooleanSetting("Fly Exempt", true);

    private int setbackCooldown;
    private int setbackCount;

    /*
     * Dolphin state.
     */
    private int holdTicksLeft;

    /*
     * Air ticks since last fluid contact.
     */
    private int airTicks;

    /*
     * Did we press the jump key ourselves.
     */
    private boolean holdingJump;
    private boolean playerJumpWasPressed;

    private int debugTickCounter;

    public Jesus() {
        this.addSettings(new Setting[]{
                mode, dolphinHold, bounceUp, bounceHBoost, debug, flyExempt
        });
    }


    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable) {
            this.releaseJump();
            return;
        }

        if (mc.player == null || mc.level == null) {
            this.releaseJump();
            return;
        }

        if (flyExempt.get()) {
            FlyExempt.tick();
        }

        if (setbackCooldown > 0) {
            setbackCooldown--;
            this.releaseJump();
            return;
        }

        /*
         * Shift / own jump = full vanilla physics.
         */
        if (mc.player.isSneaking() || mc.options.jumpKey.isPressed()) {
            this.releaseJump();
            holdTicksLeft = 0;
            airTicks = 0;
            return;
        }

        boolean water = mc.player.isTouchingWater();
        boolean lava = mc.player.isInLava();
        boolean fluid = water || lava;

        if (!fluid) {
            this.releaseJump();

            if (mc.player.onGround()) {
                holdTicksLeft = 0;
                airTicks = 0;
            } else {
                airTicks++;
            }
            return;
        }

        airTicks = 0;

        if (!hasMovementInput()) {
            this.releaseJump();
            holdTicksLeft = 0;
            return;
        }

        if (mode.is("Dolphin")) {
            this.tickDolphin();
        } else if (mode.is("Liquid")) {
            this.tickLiquid(water, lava);
        } else {
            this.tickBounce(water);
        }

        this.debugTick();
    }

    /*
     * Dolphin: only the swim-up INPUT is used (the jump key),
     * the physics remain fully vanilla on both sides.
     */
    private void tickDolphin() {
        double fluidHeight = mc.player.isTouchingWater()
                ? mc.player.getFluidHeight(FluidTags.WATER)
                : mc.player.getFluidHeight(FluidTags.LAVA);

        if (holdTicksLeft > 0) {
            holdTicksLeft--;
            this.holdJump();
            return;
        }

        this.releaseJump();

        /*
         * Wait for submersion, then a new burst of swim-up.
         */
        if (fluidHeight > 0.1D && mc.player.getDeltaMovement().y <= 0.0D) {
            holdTicksLeft = dolphinHold.get().intValue();
            this.holdJump();
        }
    }

    private void tickLiquid(boolean water, boolean lava) {
        double fluidHeight = water
                ? mc.player.getFluidHeight(FluidTags.WATER)
                : mc.player.getFluidHeight(FluidTags.LAVA);

        if (fluidHeight <= 0.0D || fluidHeight >= 0.8D) {
            return;
        }

        int fluidBlockY = (int) Math.floor(mc.player.getY());
        double surfaceY = fluidBlockY + 0.9D;
        double currentY = mc.player.getY();

        if (currentY < surfaceY && mc.player.getDeltaMovement().y < 0.05D) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.05D, mc.player.getDeltaMovement().z);
        } else if (currentY >= surfaceY && mc.player.getDeltaMovement().y > 0.0D && mc.player.getDeltaMovement().y < 0.15D) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.0D, mc.player.getDeltaMovement().z);
        }
    }

    private void tickBounce(boolean water) {
        double fluidHeight = water
                ? mc.player.getFluidHeight(FluidTags.WATER)
                : mc.player.getFluidHeight(FluidTags.LAVA);

        if (fluidHeight <= 0.0D || fluidHeight >= 0.8D) {
            return;
        }

        int fluidBlockY = (int) Math.floor(mc.player.getY());
        double surfaceY = fluidBlockY + 0.9D;
        double currentY = mc.player.getY();

        if (currentY < surfaceY) {
            double up = Math.min(bounceUp.get(), (surfaceY - currentY) * 0.25D + 0.02D);
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, up, mc.player.getDeltaMovement().z);

            double boost = bounceHBoost.get();
            if (boost > 0.0D) {
                double yaw = Math.toRadians(mc.player.getYRot());
                double xt = -Math.sin(yaw);
                double zt = Math.cos(yaw);
                mc.player.addVelocity(xt * boost, 0.0D, zt * boost);
            }
        } else {
            if (mc.player.getDeltaMovement().y < 0.0D && mc.player.getDeltaMovement().y > -0.1D) {
                mc.player.setDeltaMovement(mc.player.getDeltaMovement().x, 0.0D, mc.player.getDeltaMovement().z);
            }
        }
    }

    private void debugTick() {
        if (!debug.get()) return;

        debugTickCounter++;
        if (debugTickCounter < 20) return;
        debugTickCounter = 0;

        Vec3 v = mc.player.getDeltaMovement();
        double fluidHeight = mc.player.isTouchingWater()
                ? mc.player.getFluidHeight(FluidTags.WATER)
                : mc.player.getFluidHeight(FluidTags.LAVA);

        this.debugMessage(String.format(
                "J | y=%.3f vy=%.3f h=%.3f fluidH=%.2f hold=%d air=%d setbacks=%d",
                mc.player.getY(), v.y,
                Math.sqrt(v.x * v.x + v.z * v.z),
                fluidHeight,
                holdTicksLeft, airTicks, setbackCount
        ));
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (!this.enable || mc.player == null) {
            return;
        }

        if (!event.isSend()
                && event.getPacket() instanceof PlayerPositionLookS2CPacket) {

            setbackCooldown = 12;
            setbackCount++;

            if (debug.get()) {
                Vec3 v = mc.player.getDeltaMovement();
                this.debugMessage(String.format(
                        "SETBACK #%d y=%.3f vy=%.3f",
                        setbackCount, mc.player.getY(), v.y
                ));
            }

            holdTicksLeft = 0;
            airTicks = 0;
            this.releaseJump();
        }
    }

    private void holdJump() {
        if (!holdingJump) {
            holdingJump = true;
            playerJumpWasPressed = mc.options.jumpKey.isPressed();
        }
        mc.options.jumpKey.setPressed(true);
    }

    private void releaseJump() {
        if (holdingJump) {
            holdingJump = false;
            mc.options.jumpKey.setPressed(playerJumpWasPressed);
        }
    }

    private boolean hasMovementInput() {
        if (mc.player == null || mc.options == null) {
            return false;
        }

        return mc.options.forwardKey.isPressed()
                || mc.options.backKey.isPressed()
                || mc.options.leftKey.isPressed()
                || mc.options.rightKey.isPressed();
    }

    private void debugMessage(String message) {
        if (mc.player == null) return;
        try {
            mc.player.sendMessage(
                    net.minecraft.text.Component.literal("[Jesus] " + message),
                    false
            );
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (flyExempt.get()) {
            FlyExempt.sendNow();
        }
    }

    @Override
    public void onDisable() {
        this.releaseJump();
          setbackCooldown = 0;
        holdTicksLeft = 0;
        airTicks = 0;
        super.onDisable();
    }
}
