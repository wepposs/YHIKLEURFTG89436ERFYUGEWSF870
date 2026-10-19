package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.session.Session;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({MinecraftClient.class})
public interface MinecraftClientAccessor {
   @Accessor("attackCooldown")
   void setAttackCooldown(int var1);

   @Accessor("attackCooldown")
   int getAttackCooldown();

   @Accessor("itemUseCooldown")
   void setItemUseCooldown(int var1);

   @Accessor("itemUseCooldown")
   int getItemUseCooldown();

   @Mutable
   @Accessor("session")
   void setSession(Session session);
}
