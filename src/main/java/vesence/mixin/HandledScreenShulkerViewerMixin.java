package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import vesence.module.impl.visuals.ShulkerViewer;

/**
 * Порт wave#EventRenderToolTip (отмена ванильного тултипа) под 1.21.11.
 *
 * <p>В wave-клиенте (MCP 1.16.5) превью шалкера вешалось на событие
 * {@code EventRenderToolTip} и при зажатом бинде вызывало {@code e.setCancel(true)},
 * подменяя ванильный тултип. Под Fabric/Yarn 1.21.11 то же самое достигается
 * инъекцией в {@link AbstractContainerScreen#drawMouseoverTooltip(GuiGraphicsExtractor, int, int)}
 * (HEAD, cancellable): когда {@link ShulkerViewer#onTooltip} нарисовал превью и
 * вернул {@code true} — отменяем ванильный тултип через {@code ci.cancel()}.
 */
@Environment(EnvType.CLIENT)
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenShulkerViewerMixin {

    @Shadow
    protected Slot hoveredSlot;

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 1)
    private void vesence$shulkerViewerTooltip(GuiGraphicsExtractor context, int x, int y, CallbackInfo ci) {
        Slot slot = this.hoveredSlot;
        if (slot == null || !slot.hasItem()) {
            return;
        }
        ItemStack stack = slot.getItem();
        if (ShulkerViewer.onTooltip(context, x, y, stack)) {
            ci.cancel();
        }
    }
}
