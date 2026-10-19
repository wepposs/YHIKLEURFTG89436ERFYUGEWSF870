package vesence.utils.shulker;

// ═══════════════════════════════════════════════════════════════════════════
//  Пакетный кэш содержимого шалкеров. Аналог ViaVersion-снифинга:
//  данные берутся ПРЯМО из S2C-пакетов и запоминаются под BlockPos.
//
//  Источники (заполняются в ClientPlayNetworkHandlerShulkerMixin):
//    1) BlockEntityUpdateS2CPacket — прилетает на загрузке чанка/апдейте
//       шалкера, если у него есть Items в NBT (loot/named/items). На многих
//       серверах именно тут виден реальный контент, даже если CONTAINER
//       компонент у предмета в инвентаре был обрезан.
//    2) InventoryS2CPacket — когда игрок реально открыл шалкер: слоты 0-26
//       ScreenHandler'а — это содержимое шалкера. Кешируется под последней
//       позицией, на которую смотрел игрок (см. ShulkerViewer.tick).
//    3) ScreenHandlerSlotUpdateS2CPacket — обновление отдельного слота
//       открытого ShulkerBoxScreenHandler, обновляем кеш.
//
//  Чтение — getItems(BlockPos) или getItemsAround(pos, radius) из любой
//  точки клиента.
// ═══════════════════════════════════════════════════════════════════════════

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public final class ShulkerCache {

    /** TTL записи в кэше — 30 минут. */
    private static final long TTL_MS = 30L * 60L * 1000L;

    /** Содержимое по позиции блока (immutable BlockPos ключ). */
    private static final Map<BlockPos, Entry> BY_POS = new ConcurrentHashMap<>();

    /** Последняя позиция шалкера, на которую смотрел игрок. */
    private static volatile BlockPos lastOpenedPos;

    private ShulkerCache() {}

    public static void put(BlockPos pos, DefaultedList<ItemStack> items) {
        if (pos == null || items == null || isAllEmpty(items)) return;
        BY_POS.put(pos.toImmutable(), new Entry(items, System.currentTimeMillis()));
        cleanup();
    }

    public static DefaultedList<ItemStack> getItems(BlockPos pos) {
        if (pos == null) return null;
        Entry e = BY_POS.get(pos);
        if (e == null) return null;
        if (System.currentTimeMillis() - e.timestamp > TTL_MS) {
            BY_POS.remove(pos);
            return null;
        }
        return e.items;
    }

    /**
     * Поиск в радиусе r блоков — предмет-шалкер обычно валяется в той же
     * клетке или рядом с местом разрушения.
     */
    public static DefaultedList<ItemStack> getItemsAround(BlockPos center, int r) {
        if (center == null) return null;
        DefaultedList<ItemStack> hit = getItems(center);
        if (hit != null) return hit;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    DefaultedList<ItemStack> v = getItems(center.add(dx, dy, dz));
                    if (v != null) return v;
                }
            }
        }
        return null;
    }

    public static void setLastOpenedPos(BlockPos pos) {
        lastOpenedPos = pos == null ? null : pos.toImmutable();
    }

    public static BlockPos getLastOpenedPos() {
        return lastOpenedPos;
    }

    public static void clear() {
        BY_POS.clear();
        lastOpenedPos = null;
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        BY_POS.entrySet().removeIf(e -> now - e.getValue().timestamp > TTL_MS);
    }

    private static boolean isAllEmpty(DefaultedList<ItemStack> items) {
        for (ItemStack s : items) {
            if (!s.isEmpty()) return false;
        }
        return true;
    }

    private static final class Entry {
        final DefaultedList<ItemStack> items;
        final long timestamp;
        Entry(DefaultedList<ItemStack> items, long timestamp) {
            this.items = items;
            this.timestamp = timestamp;
        }
    }
}
