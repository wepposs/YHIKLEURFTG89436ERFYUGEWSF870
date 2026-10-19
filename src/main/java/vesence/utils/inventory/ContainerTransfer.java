package vesence.utils.inventory;

import java.util.Locale;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ShulkerBoxScreenHandler;
import net.minecraft.screen.slot.ClickType;

/**
 * Логика кнопок «Сложить / Взять / Выбросить всё» для сундуков и шалкеров.
 *
 * <p>Порт из wave-клиента (1.16.5): там кнопки жили прямо в
 * {@code net.minecraft.client.gui.screen.inventory.ChestScreen}/{@code ShulkerBoxScreen}
 * и дёргали {@code mc.playerController.windowClick(...)}. Здесь под 1.21.11 (Yarn)
 * то же самое делается через {@code mc.gameMode.clickSlot(...)}.
 *
 * <p>Разметка слотов у контейнерного {@link ScreenHandler}: сначала идут слоты
 * самого контейнера (0..containerSize-1), затем слоты инвентаря игрока. Поэтому
 * «Сложить» шифт-кликает слоты игрока, «Взять» — слоты контейнера, «Выбросить» —
 * выбрасывает содержимое контейнера целиком.
 */
@Environment(EnvType.CLIENT)
public final class ContainerTransfer {

    private static final Minecraft mc = Minecraft.getInstance();

    private ContainerTransfer() {
    }

    /** Кол-во слотов самого контейнера (без инвентаря игрока), либо -1 если тип не поддержан. */
    public static int containerSlotCount(ScreenHandler handler) {
        if (handler instanceof GenericContainerScreenHandler generic) {
            return generic.getRows() * 9;
        }
        if (handler instanceof ShulkerBoxScreenHandler) {
            return 27;
        }
        return -1;
    }

    public static boolean isSupported(ScreenHandler handler) {
        return containerSlotCount(handler) > 0;
    }

    /** «Сложить»: перекидывает все предметы из инвентаря игрока в контейнер. */
    public static void storeAll() {
        ScreenHandler handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return;
        }
        for (int slot = count; slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasStack()) {
                click(handler, slot, 0, ClickType.QUICK_MOVE);
            }
        }
    }

    /** «Взять»: перекидывает всё содержимое контейнера в инвентарь игрока. */
    public static void takeAll() {
        ScreenHandler handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return;
        }
        for (int slot = 0; slot < count && slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasStack()) {
                click(handler, slot, 0, ClickType.QUICK_MOVE);
            }
        }
    }

    /** «Выбросить всё»: выбрасывает содержимое контейнера целиком на землю. */
    public static void dropAll() {
        ScreenHandler handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return;
        }
        for (int slot = 0; slot < count && slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasStack()) {
                // button=1 => выбросить весь стак (THROW всей ячейки).
                click(handler, slot, 1, ClickType.THROW);
            }
        }
    }

    /** Есть ли что перекладывать/выбрасывать из контейнера. */
    public static boolean hasContainerItems() {
        ScreenHandler handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return false;
        }
        for (int slot = 0; slot < count && slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasStack()) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли что складывать из инвентаря игрока. */
    public static boolean hasPlayerItems() {
        ScreenHandler handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return false;
        }
        for (int slot = count; slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasStack()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Экран, на котором кнопки скрываются: аукцион/поиск/магазин/хранилище.
     * Проверяет заголовок текущего экрана и эвристику аукциона (6 рядов + часы/
     * незер-звезда в 49 слоте) — как в {@code AuctionHelper}.
     */
    public static boolean isBlockedContainer() {
        if (mc.screen == null) {
            return false;
        }
        String title = mc.screen.getTitle() != null ? mc.screen.getTitle().getString() : "";
        if (isBlockedTitle(title)) {
            return true;
        }
        ScreenHandler handler = handler();
        if (handler instanceof GenericContainerScreenHandler generic
                && generic.getRows() >= 6
                && handler.slots.size() > 49) {
            ItemStack refresh = handler.getSlot(49).getItem();
            if (!refresh.isEmpty() && (refresh.isOf(Items.CLOCK) || refresh.isOf(Items.NETHER_STAR))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlockedTitle(String rawTitle) {
        if (rawTitle == null) {
            return false;
        }
        String title = rawTitle.toLowerCase(Locale.ROOT).trim();
        return title.contains("\u0430\u0443\u043a\u0446\u0438\u043e\u043d")   // аукцион
                || title.contains("auction")
                || title.contains("\u043f\u043e\u0438\u0441\u043a")            // поиск
                || title.contains("search")
                || title.contains("\u0445\u0440\u0430\u043d\u0438\u043b\u0438\u0449\u0435") // хранилище
                || title.contains("\u043c\u0430\u0433\u0430\u0437\u0438\u043d") // магазин
                || title.contains("shop")
                || title.contains("market");
    }

    private static void click(ScreenHandler handler, int slot, int button, ClickType type) {
        if (mc.gameMode == null || mc.player == null || handler == null) {
            return;
        }
        mc.gameMode.clickSlot(handler.syncId, slot, button, type, mc.player);
    }

    private static ScreenHandler handler() {
        return mc.player == null ? null : mc.player.containerMenu;
    }
}
