package vesence.utils.render.text;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.renderengine.providers.GlBackend;
import vesence.renderengine.render.Renderer2D;

@Environment(EnvType.CLIENT)
public final class FontRegistry {
   private static final Map<String, MsdfFont> REGISTERED_FONTS = new HashMap<>();
   private static final Map<String, FontObject> FONT_OBJECTS = new HashMap<>();
   private static GlBackend backend;
   private static boolean backendConfigured = false;
   private static boolean rendererFontsInitialized = false;
   public static FontObject SF_MEDIUM;
   public static FontObject SF_SEMI;
   public static FontObject SF_BOLD;
   public static FontObject SU_MEDIUM;
   public static FontObject MONTSERRAT;
   public static FontObject ICONS;
   public static FontObject VESENCE;
   public static FontObject MON;
   public static FontObject NUC;
   public static FontObject LOGO;
   public static FontObject MARK;
   public static FontObject DELUXE;
   public static FontObject ICON;
   public static FontObject RELAKE;
   public static FontObject TEST;
   public static FontObject NOTIFY;

   private FontRegistry() {
   }

   public static synchronized void initialize(GlBackend glBackend, Renderer2D renderer) {
      configureBackend(glBackend);
      Objects.requireNonNull(renderer, "renderer");
      if (!rendererFontsInitialized) {
         renderer.registerTextRenderer(SF_MEDIUM, createTextRenderer(SF_MEDIUM));
         renderer.registerTextRenderer(SF_SEMI, createTextRenderer(SF_SEMI));
         renderer.registerTextRenderer(SF_BOLD, createTextRenderer(SF_BOLD));
         renderer.registerTextRenderer(SU_MEDIUM, createTextRenderer(SU_MEDIUM));
         renderer.registerTextRenderer(MONTSERRAT, createTextRenderer(MONTSERRAT));
         renderer.registerTextRenderer(ICONS, createTextRenderer(ICONS));
         renderer.registerTextRenderer(NOTIFY, createTextRenderer(NOTIFY));
         renderer.registerTextRenderer(TEST, createTextRenderer(TEST));
         renderer.registerTextRenderer(VESENCE, createTextRenderer(VESENCE));
         renderer.registerTextRenderer(LOGO, createTextRenderer(LOGO));
         renderer.registerTextRenderer(MARK, createTextRenderer(MARK));
         renderer.registerTextRenderer(MON, createTextRenderer(MON));
         renderer.registerTextRenderer(NUC, createTextRenderer(NUC));
         renderer.registerTextRenderer(DELUXE, createTextRenderer(DELUXE));
         renderer.registerTextRenderer(RELAKE, createTextRenderer(RELAKE));
         renderer.registerTextRenderer(ICON, createTextRenderer(ICON));
         rendererFontsInitialized = true;
      }
   }

   public static synchronized FontObject register(String id, String jsonResourcePath, String textureResourcePath) {
      ensureBackendConfigured();
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(jsonResourcePath, "jsonResourcePath");
      Objects.requireNonNull(textureResourcePath, "textureResourcePath");
      if (REGISTERED_FONTS.containsKey(id)) {
         throw new IllegalStateException("Font already registered: " + id);
      } else {
         MsdfFont font = MsdfFont.load(backend, jsonResourcePath, textureResourcePath);
         REGISTERED_FONTS.put(id, font);
         FontObject fontObject = new FontObject(id);
         FONT_OBJECTS.put(id, fontObject);
         return fontObject;
      }
   }

   public static synchronized Font createTextRenderer(FontObject fontObject) {
      ensureBackendConfigured();
      MsdfFont msdfFont = resolve(fontObject);
      return new Font(backend, msdfFont);
   }

   public static synchronized float centeredBaselineOffset(FontObject fontObject, int codepoint, float size) {
      ensureBackendConfigured();
      if (fontObject != null && !(size <= 0.0F)) {
         MsdfFont font = resolve(fontObject);
         MsdfFont.Glyph glyph = font.glyph(codepoint);
         if (glyph != null && glyph.renderable) {
            float emSize = Math.max(1.0E-6F, font.emSize());
            float scale = size / emSize;
            return (glyph.planeTop + glyph.planeBottom) * 0.5F * scale;
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   public static synchronized FontObject get(String id) {
      ensureBackendConfigured();
      FontObject fontObject = FONT_OBJECTS.get(id);
      if (fontObject == null) {
         throw new IllegalArgumentException("Font not registered: " + id);
      } else {
         return fontObject;
      }
   }

   static synchronized MsdfFont resolve(FontObject fontObject) {
      ensureBackendConfigured();
      MsdfFont font = REGISTERED_FONTS.get(fontObject.id);
      if (font == null) {
         throw new IllegalStateException("Font not registered: " + fontObject.id);
      } else {
         return font;
      }
   }

   /**
    * {@code true} если MSDF-атлас шрифта содержит глиф для codepoint'а.
    * Best-effort (без исключений): нужен кастомному scoreboard, чтобы
    * пропускать символы, отсутствующие в атласе (иначе {@code Font}
    * для cp&lt;128 рисует «?», а серверные маркеры/рамки видны как мусор).
    * Аналог {@code Font.hasGlyph} из RelevantPremiumpp4.
    */
   public static synchronized boolean hasGlyph(FontObject fontObject, int codepoint) {
      if (fontObject == null) {
         return false;
      }
      try {
         MsdfFont font = REGISTERED_FONTS.get(fontObject.id);
         return font != null && font.glyph(codepoint) != null;
      } catch (Throwable ignored) {
         return false;
      }
   }

   private static void configureBackend(GlBackend glBackend) {
      Objects.requireNonNull(glBackend, "backend");
      if (backendConfigured) {
         if (backend != glBackend) {
            throw new IllegalStateException("FontRegistry already initialized with a different backend instance");
         }
      } else {
         backend = glBackend;
         backendConfigured = true;
         registerBuiltinFonts();
      }
   }

   private static void registerBuiltinFonts() {
      // Раньше 15 шрифтов грузились СТРОГО ПОСЛЕДОВАТЕЛЬНО на рендер-потоке в
      // первом кадре сплэша: чтение JSON+PNG + STB-декод + GL-заливка каждого
      // => фриз ~1.4с (FreezeWatchdog: вероятная причина MsdfFont.load /
      // FontRegistry.createTextRenderer). Теперь тяжёлую CPU/IO-фазу (decode)
      // выполняем ПАРАЛЛЕЛЬНО на воркерах, а на рендер-потоке остаётся только
      // быстрая GL-заливка (finishUpload). Декод потокобезопасен.
      String[][] specs = {
         {"SF_MEDIUM", "assets/vesence/fonts/name_tag.json", "assets/vesence/fonts/name_tag.png"},
         {"SF_SEMI", "assets/vesence/fonts/sf_semibold.json", "assets/vesence/fonts/sf_semibold.png"},
         {"SF_BOLD", "assets/vesence/fonts/sf_bold.json", "assets/vesence/fonts/sf_bold.png"},
         {"SU", "assets/vesence/fonts/suisseintl_medium.json", "assets/vesence/fonts/suisseintl_medium.png"},
         {"MONTSERRAT", "assets/vesence/fonts/montserrat_semibold.json", "assets/vesence/fonts/montserrat_semibold.png"},
         {"icons", "assets/vesence/fonts/icons.json", "assets/vesence/fonts/icons.png"},
         {"notify", "assets/vesence/fonts/notify.json", "assets/vesence/fonts/notify.png"},
         {"test", "assets/vesence/fonts/productsans_medium.json", "assets/vesence/fonts/productsans_medium.png"},
         {"vesence", "assets/vesence/fonts/vesence.json", "assets/vesence/fonts/vesence.png"},
         {"logo", "assets/vesence/fonts/logo.json", "assets/vesence/fonts/logo.png"},
         {"mark", "assets/vesence/fonts/mark.json", "assets/vesence/fonts/mark.png"},
         {"mon", "assets/vesence/fonts/mon_icons.json", "assets/vesence/fonts/mon_icons.png"},
         {"nuc", "assets/vesence/fonts/nuc_icons.json", "assets/vesence/fonts/nuc_icons.png"},
         {"deluxe", "assets/vesence/fonts/test.json", "assets/vesence/fonts/test.png"},
         {"icon", "assets/vesence/fonts/icon_2.json", "assets/vesence/fonts/icon_2.png"},
         {"relake", "assets/vesence/fonts/relake.json", "assets/vesence/fonts/relake.png"}
      };

      // Фаза 1 — параллельный decode (CPU/IO), без OpenGL.
      Map<String, MsdfFont.Decoded> decoded = new java.util.concurrent.ConcurrentHashMap<>();
      java.util.Arrays.stream(specs).parallel().forEach(s -> decoded.put(s[0], MsdfFont.decode(s[1], s[2])));

      // Фаза 2 — последовательная GL-заливка на текущем (рендер) потоке.
      SF_MEDIUM = registerDecoded("SF_MEDIUM", decoded.get("SF_MEDIUM"));
      SF_SEMI = registerDecoded("SF_SEMI", decoded.get("SF_SEMI"));
      SF_BOLD = registerDecoded("SF_BOLD", decoded.get("SF_BOLD"));
      SU_MEDIUM = registerDecoded("SU", decoded.get("SU"));
      MONTSERRAT = registerDecoded("MONTSERRAT", decoded.get("MONTSERRAT"));
      ICONS = registerDecoded("icons", decoded.get("icons"));
      NOTIFY = registerDecoded("notify", decoded.get("notify"));
      TEST = registerDecoded("test", decoded.get("test"));
      VESENCE = registerDecoded("vesence", decoded.get("vesence"));
      LOGO = registerDecoded("logo", decoded.get("logo"));
      MARK = registerDecoded("mark", decoded.get("mark"));
      MON = registerDecoded("mon", decoded.get("mon"));
      NUC = registerDecoded("nuc", decoded.get("nuc"));
      DELUXE = registerDecoded("deluxe", decoded.get("deluxe"));
      ICON = registerDecoded("icon", decoded.get("icon"));
      RELAKE = registerDecoded("relake", decoded.get("relake"));
   }

   /** Заливает предварительно декодированный шрифт в GL и регистрирует его (рендер-поток). */
   private static FontObject registerDecoded(String id, MsdfFont.Decoded decoded) {
      if (decoded == null) {
         throw new IllegalStateException("Font decode failed / missing: " + id);
      }
      MsdfFont font = MsdfFont.finishUpload(backend, decoded);
      REGISTERED_FONTS.put(id, font);
      FontObject fontObject = new FontObject(id);
      FONT_OBJECTS.put(id, fontObject);
      return fontObject;
   }

    public static synchronized void reset() {
      REGISTERED_FONTS.clear();
      FONT_OBJECTS.clear();
      backend = null;
      backendConfigured = false;
      rendererFontsInitialized = false;
   }

    private static void ensureBackendConfigured() {
      if (!backendConfigured || backend == null) {
         throw new IllegalStateException("FontRegistry.initialize(backend, renderer) must be called before use");
      }
   }
}
