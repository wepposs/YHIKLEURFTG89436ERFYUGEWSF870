package vesence.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Доступ к времени кулдауна предмета.
 *
 * <p>Порт 26.3: {@code ItemCooldowns$Entry} переименован в
 * {@code ItemCooldowns$CooldownInstance} и стал record'ом с компонентами
 * {@code startTime}/{@code endTime}. У record'а есть публичные методы-аксессоры,
 * поэтому используем {@link Invoker}, а не {@code @Accessor} к полю — так доступ
 * не зависит от того, как компилятор разложил final-поля record'а.
 */
@Mixin(targets = "net.minecraft.world.item.ItemCooldowns$CooldownInstance")
public interface ItemCooldownManagerEntryAccessor {

    @Invoker("startTime")
    int invokeStartTick();

    @Invoker("endTime")
    int invokeEndTick();
}
