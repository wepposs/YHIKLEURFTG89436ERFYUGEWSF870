package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.painting.PaintingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import vesence.module.impl.misc.NameProtect;

/**
 * Streamer Mode (3): делает item-frame / glow item-frame и картины (painting)
 * полностью невидимыми, возвращая {@code false} из
 * {@link EntityRenderer#shouldRender} для этих сущностей.
 *
 * <p>В 1.21.11 класс {@code EntityRenderDispatcher} (который хукал оригинал в
 * RelevantPremiumpp4) больше не рендерит сущности напрямую — конвейер перешёл
 * на render-state. Точка {@code shouldRender(entity, frustum, ...)} по-прежнему
 * получает саму сущность и является чистым способом отменить её отрисовку.
 *
 * <p>{@code GlowItemFrameEntity} наследует {@code ItemFrameEntity}, поэтому один
 * {@code instanceof ItemFrameEntity} покрывает оба варианта рамок.
 */
@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public class FrameNoRenderMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void vesence$hideFrames(Entity entity, Frustum frustum, double x, double y, double z,
                                    CallbackInfoReturnable<Boolean> cir) {
        NameProtect np = NameProtect.getInstance();
        if (np == null || !np.shouldHideFrames()) {
            return;
        }
        if (entity instanceof ItemFrameEntity || entity instanceof PaintingEntity) {
            cir.setReturnValue(false);
        }
    }
}
