package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({Minecraft.class})
public interface MinecraftClientAccessor {
   @Accessor("missTime")
   void setAttackCooldown(int var1);

   @Accessor("missTime")
   int getAttackCooldown();

   @Accessor("rightClickDelay")
   void setItemUseCooldown(int var1);

   @Accessor("rightClickDelay")
   int getItemUseCooldown();

   @Mutable
   @Accessor("user")
   void setSession(User user);
}
