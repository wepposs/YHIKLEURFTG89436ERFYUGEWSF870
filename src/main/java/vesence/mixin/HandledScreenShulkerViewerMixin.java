package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
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
 * инъекцией в {@link HandledScreen#drawMouseoverTooltip(DrawContext, int, int)}
 * (HEAD, cancellable): когда {@link ShulkerViewer#onTooltip} нарисовал превью и
 * вернул {@code true} — отменяем ванильный тултип через {@code ci.cancel()}.
 */
@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class HandledScreenShulkerViewerMixin {

    @Shadow
    protected Slot focusedSlot;

    @Inject(method = "drawMouseoverTooltip", at = @At("HEAD"), cancellable = true, require = 1)
    private void vesence$shulkerViewerTooltip(DrawContext context, int x, int y, CallbackInfo ci) {
        Slot slot = this.focusedSlot;
        if (slot == null || !slot.hasStack()) {
            return;
        }
        ItemStack stack = slot.getStack();
        if (ShulkerViewer.onTooltip(context, x, y, stack)) {
            ci.cancel();
        }
    }
}
