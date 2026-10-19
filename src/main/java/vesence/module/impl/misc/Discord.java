package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.overlay.core.FloatingWindowManager;

@Environment(EnvType.CLIENT)
@IModule(name = "Discord", description = "Окно Discord в мире (только bot API)", category = Category.MISC, bind = -1)
public final class Discord extends Module {
    public final BindSettings openBind = new BindSettings("Бинд открытия", -1);
    public Discord() { this.addSettings(openBind); }
    public void handleOpenKey(int key) { if (openBind.get() == key) FloatingWindowManager.get().toggleDiscord(); }
    @Override public void onEnable() { super.onEnable(); FloatingWindowManager.get().openDiscord(); }
    @Override public void onDisable() { FloatingWindowManager.get().close(vesence.overlay.discord.DiscordWindow.class); super.onDisable(); }
}
