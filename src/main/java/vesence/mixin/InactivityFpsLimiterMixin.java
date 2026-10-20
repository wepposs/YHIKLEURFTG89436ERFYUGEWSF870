package vesence.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.InactivityFpsLimit;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Environment(EnvType.CLIENT)
// TODO(port26): в 26.3 InactivityFpsLimit — простой enum, логика лимита в
// com.mojang.blaze3d.platform.FramerateLimitTracker / Minecraft. Хук FPS в меню отключён.
@Mixin({InactivityFpsLimit.class})
public abstract class InactivityFpsLimiterMixin {
}
