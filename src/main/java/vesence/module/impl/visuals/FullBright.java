package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.ModeSetting;

@IModule(name = "FullBright ", description = "Максимальная яркость через гамму", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class FullBright extends Module {
    public ModeSetting mode = new ModeSetting("Тип", "Гамма", "Гамма", "Эффект");

    public FullBright() {
        this.addSettings(new Setting[]{this.mode});
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (mc.levelRenderer != null && this.mode.is("Гамма")) {
            mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (mc.levelRenderer != null && this.mode.is("Гамма")) {
            mc.levelRenderer.invalidateCompiledGeometry(mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
        }

        if (this.mode.is("Эффект")) {
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    @EventInit
    public void onUpdate(ClientTickEvent e) {
        if (mc.player != null) {
            if (this.mode.is("Гамма")) {
                mc.player.removeEffect(MobEffects.NIGHT_VISION);
            }

            if (this.mode.is("Эффект")) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0, false, false));
            }
        }
    }
}
