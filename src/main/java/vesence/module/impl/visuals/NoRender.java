package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.effect.MobEffects;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;

@IModule(
   name = "Overlay Remover",
   description = "Убирает различные элементы отрисовки",
   category = Category.VISUALS,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NoRender extends Module {

   public static final MultiBooleanSetting elements = new MultiBooleanSetting("Элементы",
           new BooleanSetting("Огонь на экране", true),
           new BooleanSetting("Лава на экране", false),
           new BooleanSetting("Вода на экране", false),
           new BooleanSetting("Оверлей в блоке", false),
           new BooleanSetting("Портал на экране", false),
           new BooleanSetting("Виньетка", false),
           new BooleanSetting("Иконки эффектов", false),
           new BooleanSetting("Тошнота", false),
           new BooleanSetting("Линия босса", true),
           new BooleanSetting("Анимация тотема", true),
           new BooleanSetting("Партиклы тотема", true),
           new BooleanSetting("Тайтлы", true),
           new BooleanSetting("Таблица", true),
           new BooleanSetting("Тряску камеры", true),
           new BooleanSetting("Плохие эффекты", true),
           new BooleanSetting("Блеск зачарования", false),
           new BooleanSetting("Сердца визера", false),
           new BooleanSetting("Плохие сердца", false),
           new BooleanSetting("Дождь", true),
           new BooleanSetting("Облака", false),
           new BooleanSetting("Блок-сущности", false),
           new BooleanSetting("Тени", false),
           new BooleanSetting("Частицы", false),
           new BooleanSetting("Частицы удара", false),
           // ── Deluxe NoRender ──
           new BooleanSetting("Эффекты FOV", false),
           new BooleanSetting("Тыква на голове", false),
           new BooleanSetting("Индикатор атаки", false)
   );

   /** Deluxe NoRender: какие звуки заглушать (дефолты — «Опыт» и «Портал»). */
   public static final MultiBooleanSetting sounds = new MultiBooleanSetting("Звуки",
           new BooleanSetting("Опыт", true),
           new BooleanSetting("Пластинки", false),
           new BooleanSetting("Портал", true),
           new BooleanSetting("Иссушитель", false),
           new BooleanSetting("Шипы", false),
           new BooleanSetting("Копание", false),
           new BooleanSetting("Варден", false)
   );

   public NoRender() {
      this.addSettings(elements, sounds);
   }

   /** Общий помощник: активен ли модуль и включён ли элемент. */
   public static boolean isElementActive(String element) {
      try {
         NoRender inst = vesence.Vesence.get.manager.get(NoRender.class);
         return inst != null && inst.enable && elements.get(element);
      } catch (Throwable ignored) {
         return false;
      }
   }

   /**
    * Deluxe NoRender (Class149#m1689): нужно ли заглушить этот звук.
    * «Опыт» глушится только дальше 2 блоков от игрока.
    */
   public static boolean shouldMute(net.minecraft.client.resources.sounds.SoundInstance sound) {
      try {
         NoRender inst = vesence.Vesence.get.manager.get(NoRender.class);
         if (inst == null || !inst.enable || sound == null) return false;
         String path = sound.getIdentifier().getPath();
         if (sounds.get("Пластинки") && sound.getSource() == net.minecraft.sounds.SoundSource.RECORDS) return true;
         if (sounds.get("Портал") && path.startsWith("block.portal.")) return true;
         if (sounds.get("Иссушитель") && path.startsWith("entity.wither.")) return true;
         if (sounds.get("Варден") && path.startsWith("entity.warden.")) return true;
         if (sounds.get("Шипы") && path.equals("enchant.thorns.hit")) return true;
         if (sounds.get("Опыт") && path.equals("entity.experience_orb.pickup")
                 && mc.player != null
                 && (sound.getX() - mc.player.getX()) * (sound.getX() - mc.player.getX())
                  + (sound.getY() - mc.player.getY()) * (sound.getY() - mc.player.getY())
                  + (sound.getZ() - mc.player.getZ()) * (sound.getZ() - mc.player.getZ()) > 4.0) return true;
         return sounds.get("Копание") && path.startsWith("block.")
                 && (path.endsWith(".hit") || path.endsWith(".break"));
      } catch (Throwable ignored) {
         return false;
      }
   }

   @EventInit
   public void onUpdate(EventUpdate e) {
      if (mc.player != null) {
         if (elements.get("Плохие эффекты")) {
            if (mc.player.hasEffect(MobEffects.DARKNESS)) mc.player.removeEffect(MobEffects.DARKNESS);
            if (mc.player.hasEffect(MobEffects.BLINDNESS)) mc.player.removeEffect(MobEffects.BLINDNESS);
            if (mc.player.hasEffect(MobEffects.NAUSEA)) mc.player.removeEffect(MobEffects.NAUSEA);
         }
      }
   }
}
