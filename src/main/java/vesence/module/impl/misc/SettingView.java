package vesence.module.impl.misc;

import vesence.module.Theme;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.*;

@IModule(name = "SettingView", category = Category.MISC)
public class SettingView extends Module {
    public BindSettings bindSettings = new BindSettings("Bind", -1);
    public TitleSetting titleSetting = new TitleSetting("Title");
    public BooleanSetting booleanSetting = new BooleanSetting("Booleansdkasodpaksdpoaksdpoaskaposkd", false);
    public HueSetting hueSetting = new HueSetting("Color", -1);
    public ListSetting listSetting = new ListSetting("List", "Mode 1", "Mode 2");
    public ModeSetting modeSetting = new ModeSetting("Mode", "Mode 1", "Mode 1", "Mode 2");
    public MultiBooleanSetting multiBooleanSetting = new MultiBooleanSetting("Multi", new BooleanSetting("Mode 1", false), new BooleanSetting("Mode 2", false));
    public SliderSetting sliderSetting = new SliderSetting("Slider", 5, 1, 10, 1);
    public StringSetting stringSetting = new StringSetting("String", "Test");

    public SettingView() {
        this.addSettings(
                bindSettings,
                titleSetting,
                booleanSetting,
                hueSetting,
                listSetting,
                modeSetting,
                multiBooleanSetting,
                sliderSetting,
                stringSetting);
    }
}
