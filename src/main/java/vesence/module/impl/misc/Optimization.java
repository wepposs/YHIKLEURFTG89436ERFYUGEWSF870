package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.event.EventInit;
import vesence.event.EventManager;
import vesence.event.impl.EventChangeWorld;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.optimization.Opt;
import vesence.optimization.TextLayoutCache;

/**
 * Клиентские оптимизации в одном модуле.
 *
 * <p>Настроек у модуля нет намеренно: набор подобран так, чтобы не менять ванильное
 * поведение, поэтому он всегда работает в максимальном режиме. Выключить можно только
 * весь модуль — например, чтобы сравнить FPS.
 *
 * <p>Модуль — единственная точка включения: миксины спрашивают {@link Opt},
 * а не лезут в {@code ModuleManager}.
 */
@IModule(name = "Optimization", description = "Оптимизации рендера: частицы, сущности, блок-сущности, текст", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class Optimization extends Module {

   /**
    * Дистанция, с которой мобы рисуются без слоёв: брони, предмета в руке, плаща
    * и внешнего слоя скина. Дальше 32 блоков эти детали занимают единицы пикселей,
    * а стоят как полноценный проход по модели.
    */
   public static final double ENTITY_LOD_DISTANCE = 32.0;

   /**
    * То же для игроков, но дальше: в PvP важно видеть снаряжение и плащ противника,
    * поэтому порог мягче.
    */
   public static final double PLAYER_LOD_DISTANCE = 64.0;

   private static Optimization instance;

   public Optimization() {
      // Настроек нет, набор фиксирован — модуль включён сразу после установки.
      // defaultEnabled (а не enable=true) потому, что Config#loadModules сначала
      // гасит уже включённые модули через setState(false), а тот проигрывает звук
      // переключения; с defaultEnabled включение происходит один раз.
      this.defaultEnabled = true;
      instance = this;
      Opt.bind(this);
   }

   public static Optimization getInstance() {
      return instance;
   }

   @Override
   public void onEnable() {
      super.onEnable();
      Opt.bind(this);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      clearCaches();
   }

   /** Сброс кэшей: вызывается при выключении модуля и при смене мира. */
   public static void clearCaches() {
      TextLayoutCache.clear();
   }

   /** Сброс кэшей при смене мира: данные из старого мира больше не годятся. */
   public static void onWorldChange() {
      clearCaches();
   }

   /** Слушатель событий: держит кэши в актуальном состоянии между мирами. */
   @Environment(EnvType.CLIENT)
   public static final class Hooks {
      @EventInit
      private void onWorldChange(EventChangeWorld event) {
         Optimization.onWorldChange();
      }
   }

   static {
      // Слушатель не привязан к включению модуля: кэши нужно чистить и при выключенном
      // модуле, иначе после смены мира можно отдать текст из прошлого.
      EventManager.register(new Hooks());
   }
}
