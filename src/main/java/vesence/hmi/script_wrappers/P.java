package vesence.hmi.script_wrappers;

import vesence.hmi.access.LivingEntityAccessor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.player.AbstractClientPlayer;

public class P {
    public boolean isSneaking(AbstractClientPlayer player) {
        return player.isSneaking();
    }

    public boolean isOnGround(AbstractClientPlayer player) {
        return player.onGround();
    }

    public boolean isSwimming(AbstractClientPlayer player) {
        return player.isSwimming();
    }

    public boolean isClimbing(AbstractClientPlayer player) {
        return player.isClimbing();
    }

    public boolean isCrawling(AbstractClientPlayer player) {
        return player.isCrawling();
    }

    public boolean isSubmergedInWater(AbstractClientPlayer player) {
        return player.isSubmergedInWater();
    }

    public double getX(AbstractClientPlayer player) {
        return player.getX();
    }

    public double getY(AbstractClientPlayer player) {
        return player.getY();
    }

    public double getZ(AbstractClientPlayer player) {
        return player.getZ();
    }

    public double getXSpeed(AbstractClientPlayer player) {
        return player.getDeltaMovement().getX();
    }

    public double getYSpeed(AbstractClientPlayer player) {
        return player.getDeltaMovement().getY();
    }

    public double getZSpeed(AbstractClientPlayer player) {
        return player.getDeltaMovement().getZ();
    }

    public double getSpeed(AbstractClientPlayer player) {
        return player.getDeltaMovement().length();
    }

    public boolean isUsingItem(AbstractClientPlayer player) {
        return player.isUsingItem();
    }

    public double getYaw(AbstractClientPlayer player) {
        return player.getHeadYaw();
    }

    public double getPitch(AbstractClientPlayer player) {
        return player.getXRot();
    }

    public ItemStack getMainItem(AbstractClientPlayer player) {
        return player.getMainHandItem();
    }

    public ItemStack getOffhandItem(AbstractClientPlayer player) {
        return player.getOffhandItem();
    }

    public InteractionHand getActiveHand(AbstractClientPlayer player) {
        return player.getActiveHand();
    }

    public int getAge(AbstractClientPlayer player) {
        return player.tickCount;
    }

    public boolean isItemCoolingDown(ItemStack item, AbstractClientPlayer player) {
        return player.getItemCooldownManager().isCoolingDown(item);
    }

    public double getSwingCount(AbstractClientPlayer player) {
        if (player instanceof LivingEntityAccessor) {
            LivingEntityAccessor access = (LivingEntityAccessor)player;
            return access.hMI5_0$getSwingCount();
        }
        return 0.0;
    }
}

