package vesence.mixin;

import baritone.api.event.events.TickEvent;
import baritone.utils.InputOverrideHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.pathing.VesenceBaritone;

/**
 * Ввод остаётся у Vesence, пока клиент ведёт маршрут.
 *
 * <p>Пока у Baritone активен процесс (а наш расчёт маршрута — это активный
 * {@code CustomGoalProcess}), его {@code InputOverrideHandler} подменяет
 * {@code player.input} своим {@code PlayerMovementInput}. Для клиента это
 * катастрофа: миксин {@code KeyboardInputMixin} висит на
 * {@code KeyboardInput.tick}, с чужим объектом ввода {@code EventInput} не
 * возникает вовсе, и исполнитель Vesence не получает управление — бот стоит
 * столбом с живой головой.
 *
 * <p>Поэтому на всё время вождения ({@link VesenceBaritone#isDriving()} —
 * расчёт маршрута или работа ног) тик Baritone по вводу отменяется, а в
 * {@code player.input} возвращается ванильный {@code KeyboardInput} — тем же
 * конструктором {@code new KeyboardInput(options)}, каким его возвращает сам
 * Baritone, когда отпускает управление. В простое (цели нет) миксин не делает
 * ничего: ручной ввод и чужие процессы Baritone работают как обычно.
 */
@Mixin(InputOverrideHandler.class)
public abstract class BaritoneInputOverrideMixin {

    @Inject(method = "onTick", at = @At("HEAD"), cancellable = true)
    private void vesenceKeepKeyboardInput(TickEvent event, CallbackInfo ci) {
        if (!VesenceBaritone.get().isDriving()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        if (!(player.input instanceof KeyboardInput)) {
            player.input = new KeyboardInput(mc.options);
        }
        ci.cancel();
    }
}
