package vesence.module;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.Animation;
import vesence.utils.render.math.animation.impl.EaseInOutQuad;

@Environment(EnvType.CLIENT)
public enum Theme {
    White(new Color(ColorUtil.getColor(255,255,255)), new Color(ColorUtil.getColor(255,255,255))),
    Blue(new Color(ColorUtil.getColor(125,125,255)), new Color(ColorUtil.getColor(125,125,255))),
    Red(new Color(ColorUtil.getColor(255,125,125)), new Color(ColorUtil.getColor(255,125,125))),
    Orange(new Color(ColorUtil.getColor(255,174,123)), new Color(ColorUtil.getColor(255,174,123))),
    Green(new Color(ColorUtil.getColor(125,255,125)), new Color(ColorUtil.getColor(125,255,125))),
    Purple(new Color(ColorUtil.getColor(200,125,255)), new Color(ColorUtil.getColor(200,125,255))),
    Pink(new Color(ColorUtil.getColor(255,125,200)), new Color(ColorUtil.getColor(255,125,200))),
    Cyan(new Color(ColorUtil.getColor(125,255,255)), new Color(ColorUtil.getColor(125,255,255))),
    Yellow(new Color(ColorUtil.getColor(255,255,125)), new Color(ColorUtil.getColor(255,255,125))),
    Lime(new Color(ColorUtil.getColor(180,255,125)), new Color(ColorUtil.getColor(180,255,125))),
    Magenta(new Color(ColorUtil.getColor(255,125,255)), new Color(ColorUtil.getColor(255,125,255))),
    Teal(new Color(ColorUtil.getColor(125,200,200)), new Color(ColorUtil.getColor(125,200,200))),
    Lavender(new Color(ColorUtil.getColor(180,150,255)), new Color(ColorUtil.getColor(180,150,255))),
    Peach(new Color(ColorUtil.getColor(255,200,150)), new Color(ColorUtil.getColor(255,200,150))),
    Mint(new Color(ColorUtil.getColor(150,255,200)), new Color(ColorUtil.getColor(150,255,200))),
    Rose(new Color(ColorUtil.getColor(255,150,180)), new Color(ColorUtil.getColor(255,150,180))),
    Rose2(new Color(ColorUtil.getColor(172, 104, 152)), new Color(ColorUtil.getColor(172, 104, 152))),
    Green2(new Color(ColorUtil.getColor(75, 165, 76)), new Color(ColorUtil.getColor(75, 165, 76))),
    Sky(new Color(ColorUtil.getColor(150,200,255)), new Color(ColorUtil.getColor(150,200,255))),
    Coral(new Color(ColorUtil.getColor(255,180,150)), new Color(ColorUtil.getColor(255,180,150))),
    Aqua(new Color(ColorUtil.getColor(150,255,230)), new Color(ColorUtil.getColor(150,255,230))),
    Violet(new Color(ColorUtil.getColor(220,150,255)), new Color(ColorUtil.getColor(220,150,255))),
    GradientPurple(new Color(ColorUtil.getColor(158, 129, 199)), new Color(ColorUtil.getColor(136, 102, 181))),
    GradientBlue(new Color(ColorUtil.getColor(125,125,255)), new Color(ColorUtil.getColor(61, 188, 217))),
    GradientPink(new Color(ColorUtil.getColor(165, 128, 177)), new Color(ColorUtil.getColor(195, 173, 150))),
    GradientGreen(new Color(ColorUtil.getColor(41, 156, 119)), new Color(ColorUtil.getColor(41, 156, 119)));

    private final Color main;
    private final Color main2;

    public Animation animation = new EaseInOutQuad(300, 1.0);

    private Theme(Color main, Color main2) {
        this.main = main;
        this.main2 = main2;
    }

    public Color getMain() { return main; }
    public Color getMain2() { return main2; }
    public Color getBg() { return main; }
    public Color getBg2() { return main; }
    public Color getOutline() { return main; }
    public Color getText() { return main; }
    public Color getText2() { return main; }
    public Animation getAnimation() { return animation; }
    public String getName() { return name(); }
}
