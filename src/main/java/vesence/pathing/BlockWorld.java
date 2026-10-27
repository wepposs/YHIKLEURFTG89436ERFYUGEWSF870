package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.Arrays;

/**
 * Доступ к блокам мира для поиска пути, с кэшем на время одного расчёта.
 *
 * <p>A* обращается к одним и тем же блокам десятки раз (каждый сосед проверяется
 * из нескольких узлов), а {@code getBlockState} + {@code getCollisionShape} — не
 * бесплатные. Поэтому свойства блока упаковываются в один {@code int} и кладутся
 * в карту по упакованной позиции.
 *
 * <p>Кэш живёт только на время поиска: мир меняется, и держать его дольше нельзя.
 */
@Environment(EnvType.CLIENT)
public final class BlockWorld {

    private static final int PASSABLE   = 1;
    private static final int SOLID      = 1 << 1;
    private static final int WATER      = 1 << 2;
    private static final int DANGEROUS  = 1 << 3;
    private static final int CLIMBABLE  = 1 << 4;
    private static final int HAZARD_EDGE = 1 << 5;
    /** Блок можно сломать и пройти сквозь него. */
    private static final int DIGGABLE   = 1 << 6;

    /** Предохранитель от разрастания кэша на дальних маршрутах. */
    private static final int MAX_CACHE = 400_000;

    private final Level level;
    private final LongIntMap cache = new LongIntMap();

    /** Тестовый источник блоков: когда задан, мир не опрашивается. */
    private final SolidTest solidTest;

    /**
     * Проверка «блок твёрдый». Нужна только для автотестов поиска пути:
     * позволяет прогнать A* по синтетическому рельефу с ямами и стенами,
     * не строя их в реальном мире.
     */
    @FunctionalInterface
    public interface SolidTest {
        boolean solid(int x, int y, int z);
    }

    public BlockWorld(Level level) {
        this.level = level;
        this.solidTest = null;
    }

    private SolidTest waterTest;

    public static BlockWorld synthetic(SolidTest test) {
        return new BlockWorld(test);
    }

    public static BlockWorld synthetic(SolidTest solid, SolidTest water) {
        BlockWorld world = new BlockWorld(solid);
        world.waterTest = water;
        return world;
    }

    private BlockWorld(SolidTest test) {
        this.level = null;
        this.solidTest = test;
    }

    public Level level() {
        return this.level;
    }

    private int flags(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        int known = this.cache.get(key);
        if (known != LongIntMap.ABSENT) {
            return known;
        }

        if (this.solidTest != null) {
            // В синтетическом рельефе твёрдый блок считается и копаемым: иначе
            // автотесты копания проверяли бы отсутствие функции, а не её работу.
            int synthetic = this.solidTest.solid(x, y, z) ? (SOLID | DIGGABLE) : PASSABLE;
            if (this.waterTest != null && this.waterTest.solid(x, y, z)) synthetic |= WATER;
            this.put(key, synthetic);
            return synthetic;
        }

        int value = 0;
        BlockPos pos = new BlockPos(x, y, z);

        if (this.level.isOutsideBuildHeight(y)) {
            // За пределами мира — считаем как воздух, но опираться на это нельзя.
            value = PASSABLE;
            this.put(key, value);
            return value;
        }

        BlockState state = this.level.getBlockState(pos);
        FluidState fluid = state.getFluidState();

        if (fluid.getType() == Fluids.WATER || fluid.getType() == Fluids.FLOWING_WATER) {
            value |= WATER;
        }
        if (fluid.getType() == Fluids.LAVA || fluid.getType() == Fluids.FLOWING_LAVA) {
            value |= DANGEROUS;
        }

        boolean collisionEmpty = state.getCollisionShape(this.level, pos).isEmpty();
        if (collisionEmpty) {
            value |= PASSABLE;
        } else {
            value |= SOLID;
        }

        if (state.is(Blocks.LAVA) || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) {
            value |= DANGEROUS;
        }

        if (state.is(Blocks.LADDER) || state.is(Blocks.VINE) || state.is(Blocks.SCAFFOLDING)
                || state.is(Blocks.TWISTING_VINES) || state.is(Blocks.TWISTING_VINES_PLANT)
                || state.is(Blocks.WEEPING_VINES) || state.is(Blocks.WEEPING_VINES_PLANT)
                || state.is(Blocks.CAVE_VINES) || state.is(Blocks.CAVE_VINES_PLANT)) {
            value |= CLIMBABLE;
        }

        // Можно ли прорубить ход.
        //
        // Признак ровно один и он честный: твёрдость неотрицательна. Так ваниль
        // сама говорит, что блок ломается, — бедрок, барьер и порталы возвращают
        // -1, и перечислять их по именам не нужно.
        //
        // Жидкости и опасные блоки не копаем: прорубить воду нельзя, а лезть
        // сквозь лаву с огнём — не «дойти до цели», а «умереть по дороге».
        if ((value & SOLID) != 0 && (value & DANGEROUS) == 0
                && state.getDestroySpeed(this.level, pos) >= 0.0F) {
            value |= DIGGABLE;
        }

        this.put(key, value);
        return value;
    }

    private void put(long key, int value) {
        if (this.cache.size() >= MAX_CACHE) {
            this.cache.clear();
        }
        this.cache.put(key, value);
    }

    /**
     * Хранилище «позиция → флаги» на открытых адресах, без упаковки в объекты.
     *
     * <p>Зачем своё. Поиск пути делает миллионы обращений к кэшу за маршрут, и
     * {@code HashMap<Long, Integer>} на каждом из них создаёт по два объекта
     * (Long и Integer) плюс платит за их хеширование. На длинных маршрутах это
     * были сотни мегабайт мусора и ощутимые паузы — часть того самого «пять лет
     * строит маршрут». Открытая адресация на примитивах не создаёт ничего и
     * попадает в кэш процессора.
     */
    private static final class LongIntMap {

        /** Значение «нет записи»: настоящие флаги — неотрицательные битовые маски. */
        static final int ABSENT = Integer.MIN_VALUE;

        private long[] keys;
        private int[] values;
        private boolean[] used;
        private int size;

        LongIntMap() {
            int capacity = 8192;
            this.keys = new long[capacity];
            this.values = new int[capacity];
            this.used = new boolean[capacity];
        }

        int get(long key) {
            int mask = this.keys.length - 1;
            int i = mix(key) & mask;
            while (this.used[i]) {
                if (this.keys[i] == key) {
                    return this.values[i];
                }
                i = (i + 1) & mask;
            }
            return ABSENT;
        }

        void put(long key, int value) {
            int mask = this.keys.length - 1;
            int i = mix(key) & mask;
            while (this.used[i]) {
                if (this.keys[i] == key) {
                    this.values[i] = value;
                    return;
                }
                i = (i + 1) & mask;
            }
            this.used[i] = true;
            this.keys[i] = key;
            this.values[i] = value;
            this.size++;
            // Загрузка 0.7: дальше линейное пробирование начинает длинно скакать.
            if (this.size * 10 >= this.keys.length * 7) {
                this.grow();
            }
        }

        void clear() {
            Arrays.fill(this.used, false);
            this.size = 0;
        }

        int size() {
            return this.size;
        }

        private void grow() {
            long[] oldKeys = this.keys;
            int[] oldValues = this.values;
            boolean[] oldUsed = this.used;

            int newCapacity = oldKeys.length << 1;
            this.keys = new long[newCapacity];
            this.values = new int[newCapacity];
            this.used = new boolean[newCapacity];
            this.size = 0;
            for (int j = 0; j < oldKeys.length; j++) {
                if (oldUsed[j]) {
                    this.put(oldKeys[j], oldValues[j]);
                }
            }
        }

        /** Перемешивание длинного ключа: координаты близки, обычный хеш слипается. */
        private static int mix(long k) {
            k ^= (k >>> 33);
            k *= 0xff51afd7ed558ccdL;
            k ^= (k >>> 33);
            k *= 0xc4ceb9fe1a85ec53L;
            k ^= (k >>> 33);
            return (int) k;
        }
    }

    public boolean passable(int x, int y, int z) {
        return (this.flags(x, y, z) & PASSABLE) != 0;
    }

    public boolean solid(int x, int y, int z) {
        return (this.flags(x, y, z) & SOLID) != 0;
    }

    public boolean water(int x, int y, int z) {
        return (this.flags(x, y, z) & WATER) != 0;
    }

    public boolean dangerous(int x, int y, int z) {
        return (this.flags(x, y, z) & DANGEROUS) != 0;
    }

    public boolean climbable(int x, int y, int z) {
        return (this.flags(x, y, z) & CLIMBABLE) != 0;
    }

    /** Можно ли прорубить этот блок, чтобы пройти сквозь него. */
    public boolean diggable(int x, int y, int z) {
        return (this.flags(x, y, z) & DIGGABLE) != 0;
    }

    /**
     * Есть ли на блоке твёрдая поверхность, на которой можно стоять.
     *
     * <p>Вода опорой НЕ считается. Раньше считалась, и это была грубая ошибка:
     * поиск прокладывал маршрут «по поверхности» водоёма, будто игрок может
     * стоять на воде. В игре он туда падал, тонул и не мог выбраться — ровно то
     * «бьётся в конвульсиях». Воду надо переплывать, а не проходить пешком.
     */
    public boolean hasFloor(int x, int y, int z) {
        int below = this.flags(x, y - 1, z);
        return (below & SOLID) != 0;
    }

    /** Стоит ли игрок в воде: сам блок — вода или она на уровне ног. */
    public boolean inWater(int x, int y, int z) {
        return this.water(x, y, z) || this.water(x, y + 1, z);
    }

    /** Swimming body clearance, including feet just above the water surface. */
    public boolean canSwim(int x, int y, int z) {
        return passable(x, y, z) && passable(x, y + 1, z)
                && !dangerous(x, y, z) && !dangerous(x, y + 1, z)
                && (water(x, y, z) || water(x, y - 1, z));
    }

    /** Можно ли стоять в этом блоке: сам свободен, над ним свободно, под ним опора. */
    public boolean canStand(int x, int y, int z) {
        if (this.dangerous(x, y, z) || this.dangerous(x, y + 1, z)) {
            return false;
        }
        if (!this.passable(x, y, z) || !this.passable(x, y + 1, z)) {
            return false;
        }
        return this.hasFloor(x, y, z) || this.climbable(x, y, z);
    }

    /**
     * Ближайшая высота, на которую можно встать, если шагнуть в колонку (x,z) с высоты y.
     *
     * @return высота опоры либо {@link Integer#MIN_VALUE}, если в пределах допуска её нет
     */
    public int landingY(int x, int y, int z, int maxFall) {
        for (int drop = 0; drop <= maxFall; drop++) {
            int cy = y - drop;
            if (!this.passable(x, cy, z) || !this.passable(x, cy + 1, z)) {
                continue;
            }
            if (this.dangerous(x, cy, z) || this.dangerous(x, cy + 1, z)) {
                return Integer.MIN_VALUE;
            }
            if (this.hasFloor(x, cy, z) || this.climbable(x, cy, z)) {
                return cy;
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Можно ли пройти по диагонали, не срезав угол.
     *
     * <p>Игрок не проходит сквозь стык двух блоков: чтобы попасть по диагонали,
     * нужно чтобы хотя бы одна из двух ортогональных клеток была свободна. Иначе
     * бот будет пытаться проскользнуть в щель, которой нет.
     */
    public boolean diagonalClear(int x, int y, int z, int dx, int dz) {
        boolean sideA = this.passable(x + dx, y, z) && this.passable(x + dx, y + 1, z);
        boolean sideB = this.passable(x, y, z + dz) && this.passable(x, y + 1, z + dz);
        return sideA || sideB;
    }

    /** Опасный край: стоим у обрыва, откуда падать слишком глубоко. */
    public boolean lethalDropAhead(int x, int y, int z, int dx, int dz, int maxFall) {
        int landing = this.landingY(x + dx, y, z + dz, maxFall + 1);
        if (landing == Integer.MIN_VALUE) {
            return true;
        }
        return y - landing > maxFall;
    }
}
