package vesence.optimization;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.impl.misc.Optimization;

/**
 * Точка входа для всех оптимизаций: фиксированные пороги и быстрые проверки
 * «включено ли» без похода в {@code Vesence.get.manager} на каждом кадре.
 *
 * <p>Отдельных настроек нет — модуль либо включён целиком, либо выключен.
 * Пороги подобраны под максимальный прирост FPS при минимально заметной разнице
 * в картинке.
 */
@Environment(EnvType.CLIENT)
public final class Opt {

   /**
    * Ванильная дальность блок-сущностей ({@code BlockEntityRenderer#getViewDistance}).
    * Рендереры, которые рисуют дальше намеренно (маяк, врата Края), не урезаем —
    * иначе пропадут ориентиры, которые видно издалека специально.
    */
   public static final int VANILLA_BLOCK_ENTITY_DISTANCE = 64;

   /**
    * Множитель дальности прорисовки сущностей.
    *
    * <p>Ваниль рисует сущность, пока {@code distSq < (размерBBox * 64 * viewScale)²},
    * то есть игрока примерно со 128 блоков. При 0.7 порог падает до ~90 блоков, и из
    * кадра уходит весь конвейер дальних сущностей: extract render state, слои,
    * неймтеги, тени, submit.
    */
   private static final double ENTITY_DISTANCE_SCALE = 0.7;

   /**
    * Потолок частиц в группе вместо ванильных 16384.
    *
    * <p>Каждая частица — это квад, запись в атлас и место в render state; в сценах с
    * дождём, фермами и редстоуном именно частицы съедают кадр. Случайный отброс
    * начинается с 3/4 потолка и линейно нарастает к нему, как в ванили, поэтому
    * частицы исчезают постепенно, а не обрывом.
    */
   private static final int PARTICLE_CAP = 4096;
   private static final int PARTICLE_RESERVOIR_START = 3072;
   private static final int PARTICLE_RESERVOIR_SIZE = 1024;

   /** Дальность блок-сущностей (сундуки, таблички, головы) вместо ванильных 64 блоков. */
   private static final double BLOCK_ENTITY_DISTANCE = 32.0;

   private static Optimization module;

   /** Регистрирует инстанс модуля; вызывается из конструктора {@link Optimization}. */
   public static void bind(Optimization instance) {
      module = instance;
   }

   public static Optimization module() {
      return module;
   }

   public static boolean on() {
      Optimization m = module;
      return m != null && m.enable;
   }

   public static double entityDistanceScale() {
      return ENTITY_DISTANCE_SCALE;
   }

   public static int particleCap() {
      return PARTICLE_CAP;
   }

   public static int particleReservoirStart() {
      return PARTICLE_RESERVOIR_START;
   }

   public static int particleReservoirSize() {
      return PARTICLE_RESERVOIR_SIZE;
   }

   public static double blockEntityDistance() {
      return BLOCK_ENTITY_DISTANCE;
   }

   /** Лежащий стак рисуется одной копией вместо 1–5. */
   public static boolean itemSingleCopy() {
      return true;
   }

   /** {@code distanceSq} — квадрат расстояния от камеры до сущности. */
   public static boolean entityFar(double distanceSq) {
      return on() && distanceSq > Optimization.ENTITY_LOD_DISTANCE * Optimization.ENTITY_LOD_DISTANCE;
   }

   public static boolean playerFar(double distanceSq) {
      return on() && distanceSq > Optimization.PLAYER_LOD_DISTANCE * Optimization.PLAYER_LOD_DISTANCE;
   }
}
