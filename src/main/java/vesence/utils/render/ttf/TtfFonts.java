package vesence.utils.render.ttf;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class TtfFonts {

   private TtfFonts() {
   }

   public static final TtfFontFamily SF_MEDIUM = new TtfFontFamily("sf_medium.ttf");
   public static final TtfFontFamily SF_REGULAR = new TtfFontFamily("sf_regular.ttf");
   public static final TtfFontFamily SF_REGULAR_LEGACY = new TtfFontFamily("sfregular.ttf");
   public static final TtfFontFamily SF_SEMIBOLD = new TtfFontFamily("sf_semibold.ttf");
   public static final TtfFontFamily SF_BOLD = new TtfFontFamily("sf_bold.ttf");
   public static final TtfFontFamily SF_DISPLAY_SEMIBOLD = new TtfFontFamily("sf_display_semibold.ttf");
   public static final TtfFontFamily LOGO = new TtfFontFamily("logo.ttf");
   public static final TtfFontFamily RELAKE = new TtfFontFamily("relake.ttf");
   public static final TtfFontFamily ESSENCE = new TtfFontFamily("essence.ttf");

   public static final TtfFontFamily MAINMENU = new TtfFontFamily("mainmenu.ttf");
   public static final TtfFontFamily ALTMANAGER = new TtfFontFamily("altmanager.ttf");
   public static final TtfFontFamily CLICKGUI = new TtfFontFamily("clickgui.ttf");
   public static final TtfFontFamily DESC = new TtfFontFamily("desc.ttf");
   public static final TtfFontFamily DIVINE = new TtfFontFamily("divine.ttf");
   public static final TtfFontFamily LOX = new TtfFontFamily("lox.ttf");
   public static final TtfFontFamily MOTOTANYA = new TtfFontFamily("mototanya.ttf");
   public static final TtfFontFamily MYFONT = new TtfFontFamily("myfont.ttf");
   public static final TtfFontFamily MYYYY = new TtfFontFamily("myyyy.ttf");
   public static final TtfFontFamily TEST = new TtfFontFamily("test.ttf");
   public static final TtfFontFamily THEME = new TtfFontFamily("theme.ttf");

   public static final TtfFontFamily ICON = new TtfFontFamily("icon.ttf");
   public static final TtfFontFamily ICON1 = new TtfFontFamily("icon1.ttf");
   public static final TtfFontFamily ICONNEW = new TtfFontFamily("iconnew.ttf");
   public static final TtfFontFamily ICONS = new TtfFontFamily("icons.ttf");
   public static final TtfFontFamily DIVINE_ICONS = new TtfFontFamily("divine_icons.ttf");
   public static final TtfFontFamily WAYPOINT_ICONS = new TtfFontFamily("waypoint_icons.ttf");

   public static TtfFontFamily custom(String fileName) {
      return new TtfFontFamily(fileName);
   }
}
