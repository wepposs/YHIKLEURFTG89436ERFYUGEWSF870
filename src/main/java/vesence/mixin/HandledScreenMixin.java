package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.event.EventManager;
import vesence.event.impl.HandledScreenEvent;

@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Shadow
    public int imageWidth;

    @Shadow
    public int imageHeight;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        EventManager.call(new HandledScreenEvent(context, hoveredSlot, imageWidth, imageHeight, this.leftPos, this.topPos));
    }
}
