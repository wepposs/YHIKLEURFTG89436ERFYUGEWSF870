package vesence.hmi.script_wrappers;

import vesence.hmi.access.LivingEntityAccessor;
import net.minecraft.util.Hand;
import net.minecraft.item.ItemStack;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public class P {
    public boolean isSneaking(AbstractClientPlayerEntity player) {
        return player.isSneaking();
    }

    public boolean isOnGround(AbstractClientPlayerEntity player) {
        return player.isOnGround();
    }

    public boolean isSwimming(AbstractClientPlayerEntity player) {
        return player.isSwimming();
    }

    public boolean isClimbing(AbstractClientPlayerEntity player) {
        return player.isClimbing();
    }

    public boolean isCrawling(AbstractClientPlayerEntity player) {
        return player.isCrawling();
    }

    public boolean isSubmergedInWater(AbstractClientPlayerEntity player) {
        return player.isSubmergedInWater();
    }

    public double getX(AbstractClientPlayerEntity player) {
        return player.getX();
    }

    public double getY(AbstractClientPlayerEntity player) {
        return player.getY();
    }

    public double getZ(AbstractClientPlayerEntity player) {
        return player.getZ();
    }

    public double getXSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().getX();
    }

    public double getYSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().getY();
    }

    public double getZSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().getZ();
    }

    public double getSpeed(AbstractClientPlayerEntity player) {
        return player.getVelocity().length();
    }

    public boolean isUsingItem(AbstractClientPlayerEntity player) {
        return player.isUsingItem();
    }

    public double getYaw(AbstractClientPlayerEntity player) {
        return player.getHeadYaw();
    }

    public double getPitch(AbstractClientPlayerEntity player) {
        return player.getPitch();
    }

    public ItemStack getMainItem(AbstractClientPlayerEntity player) {
        return player.getMainHandStack();
    }

    public ItemStack getOffhandItem(AbstractClientPlayerEntity player) {
        return player.getOffHandStack();
    }

    public Hand getActiveHand(AbstractClientPlayerEntity player) {
        return player.getActiveHand();
    }

    public int getAge(AbstractClientPlayerEntity player) {
        return player.age;
    }

    public boolean isItemCoolingDown(ItemStack item, AbstractClientPlayerEntity player) {
        return player.getItemCooldownManager().isCoolingDown(item);
    }

    public double getSwingCount(AbstractClientPlayerEntity player) {
        if (player instanceof LivingEntityAccessor) {
            LivingEntityAccessor access = (LivingEntityAccessor)player;
            return access.hMI5_0$getSwingCount();
        }
        return 0.0;
    }
}

