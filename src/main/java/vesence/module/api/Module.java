package vesence.module.api;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import vesence.Vesence;
import vesence.event.EventManager;
import vesence.module.api.setting.Config;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ListSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.RangeSliderSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.StringSetting;
import vesence.module.api.setting.impl.ThemeSetting;
import vesence.module.Theme;

import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.Translate;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import vesence.utils.render.math.animation.impl.EaseInOutQuad;
import vesence.utils.notifications.Notifications;
import vesence.utils.render.text.ColorFormat;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.utils.ClientSound;

@Environment(EnvType.CLIENT)
public class Module extends Config {
   public IModule module = this.getClass().getAnnotation(IModule.class);
   public static Minecraft mc = Minecraft.getInstance();
   public String name;
   public int bind;
   public boolean enable;
   public boolean defaultEnabled = false;
   public boolean open = false;
   public Category category;
   public String displayName;
   public String description;
   public boolean binding;
   public boolean isRender = true;

   public boolean hiddenFromGui = false;
   public Translate a = new Translate(0.0F, 0.0F);
   public Animation2 animation = new Animation2();
   public vesence.utils.render.math.animation.Animation animation1 = new EaseInOutQuad(300, 1.0);
   public vesence.utils.render.math.animation.Animation animation2 = new EaseInOutQuad(300, 1.0);
   public final Animation2 mAnim = new Animation2();
   public vesence.utils.render.math.animation.Animation animation3 = new EaseInOutQuad(300, 1.0);
   public vesence.utils.render.math.animation.Animation animation4 = new EaseInOutQuad(300, 1.0);
   public final Animation2 textScaleAnim = new Animation2();
   public final Animation2 settingsIconHoverAnim = new Animation2();

   public Module() {
      this.name = this.module.name();
      this.category = this.module.category();
      if (this.module.bind() == 0) {
         this.bind = -1;
      } else {
         this.bind = this.module.bind();
      }

      this.enable = false;
      this.description = this.module.description();
      this.displayName = this.name;
   }

   public Module(String name, Category category) {
      this.name = name;
      this.category = category;
      this.enable = false;
      this.displayName = name;
      this.description = "";
      this.bind = -1;
   }

   public void onEnable() {

      try {
         EventManager.register(this);
      } catch (Exception var2) {
         var2.printStackTrace();
         this.enable = false;
         return;
      }

      ClientSound.playModuleToggle(true);

      this.mAnim.run(1.0, 0.24F, Easings.QUART_OUT);
   }

   public void onDisable() {
      EventManager.unregister(this);
      ClientSound.playModuleToggle(false);

      this.mAnim.run(0.0, 0.24F, Easings.QUART_OUT);
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public void toggle() {
      this.enable = !this.enable;
      // Dynamic Island: хук последнего переключённого модуля (порт onModuleToggle из privatedlc).
      vesence.module.impl.visuals.elements.modern.ModernDynamicIslandElement.onModuleToggle(this.name, this.enable);
      if (this.enable) {
         this.onEnable();
          Notifications.add(ColorFormat.color(255,255,255) + "Модуль "
                  + ColorFormat.color(ColorUtil.theme(255)) + this.name
                  + ColorFormat.color(255,255,255) + " — "
                  + ColorFormat.color(125,255,125) + "включен",
                  this.category.getIcon(), FontRegistry.MON, FontRegistry.SF_MEDIUM);
      } else {
         this.onDisable();
          Notifications.add(ColorFormat.color(255,255,255) + "Модуль "
                  + ColorFormat.color(ColorUtil.theme(255)) + this.name
                  + ColorFormat.color(255,255,255) + " — "
                  + ColorFormat.color(255,125,125) + "выключен",
                  this.category.getIcon(), FontRegistry.MON, FontRegistry.SF_MEDIUM);
      }

        if (Vesence.get.configManager != null) {
           Vesence.get.configManager.autoSave();
        }
   }

   public String bindLabel() {
      return vesence.utils.render.utils.KeyUtil.getKey(this.bind);
   }

   public JsonObject save() {
      JsonObject object = new JsonObject();
      if (this.enable) {
         object.addProperty("enable", this.enable);
      }

      if (this.bind != 0) {
         object.addProperty("keyIndex", this.bind);
      }

      JsonObject propertiesObject = new JsonObject();

      java.util.Map<String, Integer> nameCounts = new java.util.HashMap<>();

      for (Setting set : this.getSettings()) {
         String key = uniqueKey(nameCounts, set.name);
         if (set instanceof BooleanSetting) {
            propertiesObject.addProperty(key, ((BooleanSetting)set).get());
         } else if (set instanceof ModeSetting) {
            propertiesObject.addProperty(key, ((ModeSetting)set).currentMode);
         } else if (set instanceof SliderSetting) {
            propertiesObject.addProperty(key, ((SliderSetting)set).current);
         } else if (set instanceof RangeSliderSetting) {
            JsonObject rangeObject = new JsonObject();
            rangeObject.addProperty("from", ((RangeSliderSetting)set).valueFrom);
            rangeObject.addProperty("to", ((RangeSliderSetting)set).valueTo);
            propertiesObject.add(key, rangeObject);
          } else if (set instanceof BindSettings) {
             BindSettings bs = (BindSettings) set;
             propertiesObject.addProperty(key, bs.key);

         } else if (set instanceof StringSetting) {
            propertiesObject.addProperty(key, ((StringSetting)set).input);
         } else if (set instanceof HueSetting) {
            // Полный цвет: hue(current)/saturation/brightness/alpha, чтобы значения
            // цвета переживали перезапуск (раньше сохранялся только hue).
            HueSetting hue = (HueSetting) set;
            JsonObject hueObject = new JsonObject();
            hueObject.addProperty("h", hue.current);
            hueObject.addProperty("s", hue.saturation);
            hueObject.addProperty("b", hue.brightness);
            hueObject.addProperty("a", hue.alpha);
            propertiesObject.add(key, hueObject);
         } else if (set instanceof MultiBooleanSetting) {
            JsonObject multiBoolObject = new JsonObject();

            for (BooleanSetting boolSetting : ((MultiBooleanSetting)set).settings) {
               multiBoolObject.addProperty(boolSetting.name, boolSetting.get());
            }

            propertiesObject.add(key, multiBoolObject);
         } else if (set instanceof ListSetting) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ((ListSetting)set).selected.size(); i++) {
               sb.append(((ListSetting)set).selected.get(i));
               if (i < ((ListSetting)set).selected.size() - 1) {
                  sb.append(",");
               }
            }
            propertiesObject.addProperty(key, sb.toString());
         } else if (set instanceof ThemeSetting) {
            propertiesObject.addProperty(key, ((ThemeSetting)set).get().name());
         }
      }

      object.add("Settings", propertiesObject);
      return object;
   }

   private static String uniqueKey(java.util.Map<String, Integer> counts, String name) {
      int count = counts.getOrDefault(name, 0) + 1;
      counts.put(name, count);
      return count == 1 ? name : name + "#" + count;
   }

   public void load(JsonObject object) {
      if (object != null) {
         if (object.has("enable")) {
            this.setState(object.get("enable").getAsBoolean());
         }

         if (object.has("keyIndex")) {
            this.bind = vesence.utils.other.KeyCodeMigration.migrate(object.get("keyIndex").getAsInt());
         }
          // legacy-read: старые keyIndexExtra игнорируются, система биндов теперь single-bind only.


         java.util.Map<String, Integer> loadNameCounts = new java.util.HashMap<>();

         for (Setting set : this.getSettings()) {
            JsonObject propertiesObject = object.getAsJsonObject("Settings");
            if (set == null || propertiesObject == null) continue;

            String key = uniqueKey(loadNameCounts, set.name);

            if (!propertiesObject.has(key)) continue;
            try {
               if (set instanceof BooleanSetting) {
                  ((BooleanSetting)set).set(propertiesObject.get(key).getAsBoolean());
               } else if (set instanceof ModeSetting) {
                  ((ModeSetting)set).currentMode = propertiesObject.get(key).getAsString();
               } else if (set instanceof SliderSetting) {
                  ((SliderSetting)set).current = propertiesObject.get(key).getAsDouble();
               } else if (set instanceof RangeSliderSetting) {
                  if (propertiesObject.get(key).isJsonObject()) {
                     JsonObject rangeObject = propertiesObject.getAsJsonObject(key);
                     RangeSliderSetting range = (RangeSliderSetting)set;
                     if (rangeObject.has("from")) range.setFrom(rangeObject.get("from").getAsDouble());
                     if (rangeObject.has("to")) range.setTo(rangeObject.get("to").getAsDouble());
                  }
               } else if (set instanceof BindSettings) {
                  BindSettings bs = (BindSettings) set;
                  if (propertiesObject.get(key).isJsonObject()) {
                     JsonObject bindObject = propertiesObject.getAsJsonObject(key);
                     bs.key = bindObject.has("k") ? bindObject.get("k").getAsInt() : -1;
                  } else {
                     bs.key = propertiesObject.get(key).getAsInt();
                  }
                  bs.key = vesence.utils.other.KeyCodeMigration.migrate(bs.key);
               } else if (set instanceof StringSetting) {
                  ((StringSetting)set).input = propertiesObject.get(key).getAsString();
               } else if (set instanceof HueSetting) {
                  HueSetting hue = (HueSetting) set;
                  com.google.gson.JsonElement hueElement = propertiesObject.get(key);
                  if (hueElement.isJsonObject()) {
                     // Новый формат {h,s,b,a}.
                     JsonObject hueObject = hueElement.getAsJsonObject();
                     if (hueObject.has("h")) hue.current = hueObject.get("h").getAsFloat();
                     if (hueObject.has("s")) hue.saturation = hueObject.get("s").getAsFloat();
                     if (hueObject.has("b")) hue.brightness = hueObject.get("b").getAsFloat();
                     if (hueObject.has("a")) hue.alpha = hueObject.get("a").getAsFloat();
                  } else {
                     // Обратная совместимость: старый формат — одно число (только hue).
                     hue.current = hueElement.getAsFloat();
                  }
               } else if (set instanceof MultiBooleanSetting) {
                  if (propertiesObject.get(key).isJsonObject()) {
                     JsonObject multiBoolObject = propertiesObject.getAsJsonObject(key);

                     for (BooleanSetting boolSetting : ((MultiBooleanSetting)set).settings) {
                        if (multiBoolObject.has(boolSetting.name)) {
                           boolSetting.set(multiBoolObject.get(boolSetting.name).getAsBoolean());
                        }
                     }
                  }
               } else if (set instanceof ListSetting) {
                  String value = propertiesObject.get(key).getAsString();
                  if (!value.isEmpty()) {
                     String[] split = value.split(",");
                     ((ListSetting)set).selected = new ArrayList<>();

                     for (String s : split) {
                        if (((ListSetting)set).list.contains(s)) {
                           ((ListSetting)set).selected.add(s);
                        }
                     }
                  }
               } else if (set instanceof ThemeSetting) {
                  try {
                     Theme theme = Theme.valueOf(propertiesObject.get(key).getAsString());
                     ((ThemeSetting)set).set(theme);
                  } catch (IllegalArgumentException ignored) {
                  }
               }
            } catch (Exception ignored) {
               // Несовпадение типа/формата сохранённого значения (например, тип
               // настройки изменился между версиями) — оставляем значение по умолчанию.
            }
         }
      }
   }

   public int getBind() {
      return this.bind;
   }

   public void setState(boolean enable) {
      this.enable = enable;
      if (enable) {
         this.onEnable();
      } else {
         this.onDisable();
      }
   }

   public void setEnable(boolean enable) {
      this.enable = !enable;
      if (enable) {
         this.onEnable();
      } else {
         this.onDisable();
      }
   }
}
