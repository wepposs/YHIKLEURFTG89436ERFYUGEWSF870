package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import vesence.module.impl.visuals.Animations;
import vesence.utils.render.math.animation.AnimationUtils;

/**
 * Дополняет анимацию открытия инвентаря: кукла игрока (3D-превью) тоже
 * масштабируется вместе с панелью.
 * <p>
 * Причина: {@code GuiGraphicsExtractor.addEntity(...)} НЕ учитывает GUI matrix stack —
 * он захватывает сырые пиксельные координаты прямоугольника. Поэтому
 * matrix-scale из {@link HandledScreenAnimationMixin} масштабирует панель, но
 * не куклу. Здесь через {@link ModifyArgs} масштабируем сам прямоугольник
 * (вокруг центра экрана) и размер рендера сущности тем же значением
 * анимации — кукла растёт синхронно с инвентарём.
 */
@Environment(EnvType.CLIENT)
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenDollAnimationMixin {

    // 26.3: drawEntity → extractEntityInInventoryFollowsMouse; GuiGraphicsExtractor.addEntity → entity(...).
    @ModifyArgs(
            method = "extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;entity(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"
            )
    )
    private static void vesence$scaleDoll(Args args) {
        Animations a = Animations.getInstance();
        if (a == null || !a.isInventoryEnabled()) {
            return;
        }

        AnimationUtils anim = a.inventoryOpenAnim();
        float v = anim.getValue();
        if (Math.abs(v - 1.0f) < 1.0E-4f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        float pivotX = mc.getWindow().getGuiScaledWidth() / 2f;
        float pivotY = mc.getWindow().getGuiScaledHeight() / 2f;

        // arg1 = size (масштаб рендера сущности), arg5..8 = x1,y1,x2,y2.
        float size = (float) args.get(1);
        int x1 = args.get(5);
        int y1 = args.get(6);
        int x2 = args.get(7);
        int y2 = args.get(8);

        args.set(1, size * v);
        args.set(5, (int) (pivotX + (x1 - pivotX) * v));
        args.set(6, (int) (pivotY + (y1 - pivotY) * v));
        args.set(7, (int) (pivotX + (x2 - pivotX) * v));
        args.set(8, (int) (pivotY + (y2 - pivotY) * v));
    }
}
