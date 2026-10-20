package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.module.impl.visuals.Animations;
import vesence.utils.render.math.animation.AnimationUtils;

/**
 * Порт Relevant анимации TabList'а (режим «Список игроков»). Вертикальный
 * сдвиг раскрытия при удержании Tab. Скрытый → показанный.
 */
@Environment(EnvType.CLIENT)
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerListHudAnimationMixin {

    @Unique
    private boolean vesence$pushed;

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("HEAD")
    )
    private void vesence$onRenderHead(GuiGraphicsExtractor context, int scaledWindowWidth,
                                      net.minecraft.world.scores.Scoreboard scoreboard,
                                      net.minecraft.world.scores.Objective objective,
                                      CallbackInfo ci) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isPlayerListEnabled()) {
            vesence$pushed = false;
            return;
        }

        AnimationUtils anim = animations.playerListAnim();
        anim.update(1.0f);
        float slideProgress = anim.getValue();

        if (slideProgress >= 0.999f) {
            vesence$pushed = false;
            return;
        }

        float slideDistance = Minecraft.getInstance().getWindow().getGuiScaledHeight();

        Matrix3x2fStack matrices = context.pose();
        matrices.pushMatrix();
        matrices.translate(0.0F, -(1.0F - slideProgress) * slideDistance);
        vesence$pushed = true;
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V",
            at = @At("RETURN")
    )
    private void vesence$onRenderTail(GuiGraphicsExtractor context, int scaledWindowWidth,
                                      net.minecraft.world.scores.Scoreboard scoreboard,
                                      net.minecraft.world.scores.Objective objective,
                                      CallbackInfo ci) {
        if (vesence$pushed) {
            context.pose().popMatrix();
            vesence$pushed = false;
        }
    }

    @Inject(method = "setVisible", at = @At("HEAD"))
    private void vesence$onSetVisible(boolean visible, CallbackInfo ci) {
        Animations animations = Animations.getInstance();
        if (animations == null || !animations.isPlayerListEnabled()) return;
        if (!visible) {
            animations.playerListAnim().setValue(0.0F);
        }
    }
}
