package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;

// 26.3: Display.ItemDisplay больше не отдаёт внутренний класс Data, поэтому старый
// accessor «data» удалён. Сейчас у интерфейса нет ни одного вызывающего — оставлен
// как точка расширения (пустой миксин безопасен: он лишь добавляет интерфейс к цели).
@Environment(EnvType.CLIENT)
@Mixin({Display.ItemDisplay.class})
public interface IItemDisplayEntity {
}
