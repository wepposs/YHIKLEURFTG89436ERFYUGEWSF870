package vesence.hmi.mixin;

import vesence.hmi.access.LivingEntityAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(value=EnvType.CLIENT)
@Mixin(value={LivingEntity.class})
public abstract class LivingEntityMixin
implements LivingEntityAccessor {
    private boolean interactOffhand = false;
    private boolean interactMainHand = false;
    private boolean blockBreaking = false;
    @Unique
    private int offHandSwingTicks;
    @Unique
    private boolean offHandSwinging;
    @Unique
    public float offHandSwingProgress;
    @Unique
    public float lastOffHandSwingProgress;
    @Unique
    private int mainHandSwingTicks;
    @Unique
    private boolean mainHandSwinging;
    @Unique
    public float mainHandSwingProgress;
    @Unique
    public float lastMainHandSwingProgress;
    private int mainSwingCount = 0;
    @Unique
    private boolean swingMHand = false;
    @Unique
    private boolean swingOHand = false;

    @Shadow
    protected abstract int getHandSwingDuration();

    @Shadow
    public abstract ItemStack getMainHandStack();

    public int hMI5_0$getSwingCount() {
        return this.mainSwingCount;
    }

    public void hMI5_0$resetOffHandSwing(boolean interact) {
        this.offHandSwingTicks = 0;
        this.offHandSwinging = true;
        this.swingOHand = !this.swingOHand;
        this.interactOffhand = interact;
    }

    public void hMI5_0$resetMainHandSwing(boolean interact) {
        this.mainHandSwingTicks = 0;
        this.mainHandSwinging = true;
        this.swingMHand = !this.swingMHand;
        ++this.mainSwingCount;
        this.interactMainHand = interact;
        if (MinecraftClient.getInstance().interactionManager.isBreakingBlock()) {
            this.blockBreaking = true;
        }
    }

    public float hMI5_0$getMainHandSwingProgress(float tickDelta) {
        float f = this.mainHandSwingProgress - this.lastMainHandSwingProgress;
        if (f < 0.0f) {
            f += 1.0f;
        }
        return this.lastMainHandSwingProgress + f * tickDelta;
    }

    public float hMI5_0$getOffHandSwingProgress(float tickDelta) {
        float f = this.offHandSwingProgress - this.lastOffHandSwingProgress;
        if (f < 0.0f) {
            f += 1.0f;
        }
        return this.lastOffHandSwingProgress + f * tickDelta;
    }

    public boolean hMI5_0$getMInteract() {
        return this.interactMainHand;
    }

    public boolean hMI5_0$getOInteract() {
        return this.interactOffhand;
    }

    public boolean hMI5_0$getBlockBreak() {
        return this.blockBreaking;
    }

    public boolean hMI5_0$getMHandEvent() {
        return this.swingMHand;
    }

    public boolean hMI5_0$getOHandEvent() {
        return this.swingOHand;
    }

    @Inject(method={"baseTick"}, at={@At(value="HEAD")})
    private void tick(CallbackInfo ci) {
        int i;
        this.lastOffHandSwingProgress = this.offHandSwingProgress;
        this.lastMainHandSwingProgress = this.mainHandSwingProgress;
        int n = i = this.getMainHandStack().isOf(Items.MACE) ? 12 : 10;
        if (this.mainHandSwinging) {
            ++this.mainHandSwingTicks;
            if (this.mainHandSwingTicks >= i) {
                this.mainHandSwingTicks = 0;
                this.mainHandSwinging = false;
            }
        } else {
            if (this.interactMainHand) {
                this.interactMainHand = false;
            }
            if (this.blockBreaking) {
                this.blockBreaking = false;
            }
            this.mainHandSwingTicks = 0;
        }
        this.mainHandSwingProgress = (float)this.mainHandSwingTicks / (float)i;
        int i2 = 10;
        if (this.offHandSwinging) {
            ++this.offHandSwingTicks;
            if (this.offHandSwingTicks >= i2) {
                this.offHandSwingTicks = 0;
                this.offHandSwinging = false;
            }
        } else {
            if (this.interactOffhand) {
                this.interactOffhand = false;
            }
            this.offHandSwingTicks = 0;
        }
        this.offHandSwingProgress = (float)this.offHandSwingTicks / (float)i2;
    }

    @Inject(method={"swingHand(Lnet/minecraft/util/Hand;Z)V"}, at={@At(value="HEAD")})
    private void onSwingHand(Hand hand, boolean fromServerPlayer, CallbackInfo ci) {
        if (hand == Hand.OFF_HAND) {
            int duration = 9;
            if (!this.offHandSwinging || this.offHandSwingTicks >= duration / 2 || this.offHandSwingTicks < 0) {
                this.offHandSwingTicks = -1;
                this.offHandSwinging = true;
            }
        } else {
            int duration;
            int n = duration = this.getMainHandStack().isOf(Items.MACE) ? 11 : 9;
            if (!this.mainHandSwinging || this.mainHandSwingTicks >= duration / 2 || this.mainHandSwingTicks < 0) {
                this.mainHandSwingTicks = -1;
                this.mainHandSwinging = true;
                if (MinecraftClient.getInstance().interactionManager.isBreakingBlock()) {
                    this.blockBreaking = true;
                }
            }
        }
    }

    @Inject(method = {"getHandSwingDuration"}, at = {@At("HEAD")}, cancellable = true)
    private void modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
        if (!vesence.module.impl.visuals.LivingHands.isEnabled()) {
            return;
        }
        if ((Object) this == MinecraftClient.getInstance().player) {
            cir.setReturnValue(vesence.hmi.resource_controller.LuaScriptCache.swingSpeed);
        }
    }
}

