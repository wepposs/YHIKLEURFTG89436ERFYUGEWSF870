package vesence.mixin;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.misc.AuctionHelper;

@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public abstract class ScreenTooltipMixin {

    // require = 0 — не крашить игру, если метод не найден
    // remap = true — Loom сам подставит правильное имя метода по Yarn-маппингам
    @Inject(
            method = "getTooltipFromItem",
            at = @At("RETURN"),
            require = 0
    )
    private static void onGetTooltip(Minecraft client, ItemStack stack,
                                     CallbackInfoReturnable<List<Component>> cir) {
        try {
            AuctionHelper.appendPerUnitTooltip(stack, cir.getReturnValue());
        } catch (Throwable ignored) {
        }
    }
}