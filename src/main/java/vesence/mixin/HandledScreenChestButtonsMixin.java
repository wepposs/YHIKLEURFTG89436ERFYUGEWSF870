package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.ShulkerBoxScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import vesence.utils.inventory.ContainerTransfer;

/**
 * Кнопки «Сложить / Взять / Выбросить всё» справа от GUI сундука/шалкера.
 *
 * <p>Порт уникальной фичи из wave-клиента (там правился напрямую декомпил
 * {@code ChestScreen}/{@code ShulkerBoxScreen} 1.16.5). Под Fabric 1.21.11 то же
 * поведение реализовано миксином в {@link HandledScreen}: кнопки добавляются
 * только для контейнерных экранов (сундук/бочка/эндер-сундук/шалкер) как обычные
 * ванильные {@link ButtonWidget}, а их состояние (вкл/выкл, видимость) обновляется
 * каждый кадр. На экранах аукциона/магазина кнопки скрываются.
 */
@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class HandledScreenChestButtonsMixin extends Screen {

    @Shadow
    protected int x;

    @Shadow
    protected int y;

    @Shadow
    protected int backgroundWidth;

    @Shadow
    protected int backgroundHeight;

    private HandledScreenChestButtonsMixin(Text title) {
        super(title);
    }

    @Unique
    private ButtonWidget vesence$storeButton;

    @Unique
    private ButtonWidget vesence$takeButton;

    @Unique
    private ButtonWidget vesence$dropButton;

    @Inject(method = "init", at = @At("TAIL"))
    private void vesence$addTransferButtons(CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof GenericContainerScreen) && !(self instanceof ShulkerBoxScreen)) {
            return;
        }

        int buttonX = this.x + this.backgroundWidth + 4;
        int buttonY = this.y + 3;

        this.vesence$storeButton = ButtonWidget
                .builder(Text.literal("\u0421\u043b\u043e\u0436\u0438\u0442\u044c"), button -> ContainerTransfer.storeAll())
                .dimensions(buttonX, buttonY, 85, 20)
                .build();
        this.vesence$takeButton = ButtonWidget
                .builder(Text.literal("\u0412\u0437\u044f\u0442\u044c"), button -> ContainerTransfer.takeAll())
                .dimensions(buttonX, buttonY + 26, 85, 20)
                .build();
        this.vesence$dropButton = ButtonWidget
                .builder(Text.literal("\u0412\u044b\u0431\u0440\u043e\u0441\u0438\u0442\u044c \u0432\u0441\u0435"), button -> ContainerTransfer.dropAll())
                .dimensions(buttonX, buttonY + 52, 85, 20)
                .build();

        this.addDrawableChild(this.vesence$storeButton);
        this.addDrawableChild(this.vesence$takeButton);
        this.addDrawableChild(this.vesence$dropButton);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void vesence$updateTransferButtons(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.vesence$storeButton == null) {
            return;
        }

        boolean blocked = ContainerTransfer.isBlockedContainer();
        boolean hasContainerItems = !blocked && ContainerTransfer.hasContainerItems();
        boolean hasPlayerItems = !blocked && ContainerTransfer.hasPlayerItems();

        this.vesence$storeButton.visible = !blocked;
        this.vesence$takeButton.visible = !blocked;
        this.vesence$dropButton.visible = !blocked;

        this.vesence$storeButton.active = hasPlayerItems;
        this.vesence$takeButton.active = hasContainerItems;
        this.vesence$dropButton.active = hasContainerItems;
    }
}
