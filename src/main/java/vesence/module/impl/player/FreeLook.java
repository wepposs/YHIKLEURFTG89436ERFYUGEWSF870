package vesence.module.impl.player;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventLook;
import vesence.event.player.EventMotion;
import vesence.event.player.EventRotation;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.ModeSetting;

@IModule(name = "FreeLook", description = "Позволяет свободно осматриваться без изменения направления игрока", category = Category.PLAYER, bind = -1)
@Environment(EnvType.CLIENT)
public class FreeLook extends Module {

    public static ModeSetting modeSetting = new ModeSetting("Активация", "По нажатию", "По нажатию", "По зажатию");
    public static BindSettings bindSetting = new BindSettings("Кнопка", -1);

    private boolean freeLookActive = false;
    private boolean wasKeyDown = false;
    private float savedYaw;
    private float savedPitch;
    private float freeYaw;
    private float freePitch;

    public FreeLook() {
        addSettings(bindSetting, modeSetting);
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!this.enable) return;
        if (mc.player == null) return;
        if (bindSetting.key == -1) return;

        boolean keyDown = bindSetting.isPressed();
        String mode = modeSetting.get();

        if (mode.equals("По зажатию")) {
            if (keyDown && !freeLookActive) {
                activateFreeLook();
            } else if (!keyDown && freeLookActive) {
                deactivateFreeLook();
            }
        }

        if (mode.equals("По нажатию")) {
            if (keyDown && !wasKeyDown) {
                if (freeLookActive) {
                    deactivateFreeLook();
                } else {
                    activateFreeLook();
                }
            }
        }

        wasKeyDown = keyDown;
    }

    @EventInit
    public void onEvent(EventLook event) {
        if (!this.enable || !freeLookActive) return;
        freePitch = MathHelper.clamp((float) (freePitch + event.getPitch() * 0.15), -90.0F, 90.0F);
        freeYaw = (float) (freeYaw + event.getYaw() * 0.15);
        event.cancel();
    }

    @EventInit
    public void onEvent(EventMotion event) {
        if (!this.enable) return;
        if (freeLookActive) {
            event.setYaw(savedYaw);
            event.setPitch(savedPitch);
        }
    }

    @EventInit
    public void onEvent(EventRotation event) {
        if (!this.enable) return;
        if (freeLookActive) {
            event.setYaw(freeYaw);
            event.setPitch(freePitch);
        } else {
            freeYaw = event.getYaw();
            freePitch = event.getPitch();
        }
    }

    private void activateFreeLook() {
        freeLookActive = true;
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();
        freeYaw = mc.player.getYaw();
        freePitch = mc.player.getPitch();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    private void deactivateFreeLook() {
        freeLookActive = false;
        mc.options.setPerspective(Perspective.FIRST_PERSON);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        freeLookActive = false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (freeLookActive) {
            deactivateFreeLook();
        }
    }

    public static boolean isActive() {
        FreeLook fl = (FreeLook) vesence.Vesence.get.manager.getModule(FreeLook.class);
        return fl != null && fl.enable && fl.freeLookActive;
    }

    public static float getFreeYaw() {
        FreeLook fl = (FreeLook) vesence.Vesence.get.manager.getModule(FreeLook.class);
        return fl != null ? fl.freeYaw : 0;
    }

    public static float getFreePitch() {
        FreeLook fl = (FreeLook) vesence.Vesence.get.manager.getModule(FreeLook.class);
        return fl != null ? fl.freePitch : 0;
    }
}
