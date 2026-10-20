package vesence.utils.inventory;

import java.util.Locale;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.ContainerInput;

/**
 * Логика кнопок «Сложить / Взять / Выбросить всё» для сундуков и шалкеров.
 *
 * <p>Порт из wave-клиента (1.16.5): там кнопки жили прямо в
 * {@code net.minecraft.client.gui.screens.inventory.ChestScreen}/{@code ShulkerBoxScreen}
 * и дёргали {@code mc.playerController.windowClick(...)}. Здесь под 1.21.11 (Yarn)
 * то же самое делается через {@code mc.gameMode.handleContainerInput(...)}.
 *
 * <p>Разметка слотов у контейнерного {@link AbstractContainerMenu}: сначала идут слоты
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
    public static int containerSlotCount(AbstractContainerMenu handler) {
        if (handler instanceof ChestMenu generic) {
            return generic.getRowCount() * 9;
        }
        if (handler instanceof ShulkerBoxMenu) {
            return 27;
        }
        return -1;
    }

    public static boolean isSupported(AbstractContainerMenu handler) {
        return containerSlotCount(handler) > 0;
    }

    /** «Сложить»: перекидывает все предметы из инвентаря игрока в контейнер. */
    public static void storeAll() {
        AbstractContainerMenu handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return;
        }
        for (int slot = count; slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasItem()) {
                click(handler, slot, 0, ContainerInput.QUICK_MOVE);
            }
        }
    }

    /** «Взять»: перекидывает всё содержимое контейнера в инвентарь игрока. */
    public static void takeAll() {
        AbstractContainerMenu handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return;
        }
        for (int slot = 0; slot < count && slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasItem()) {
                click(handler, slot, 0, ContainerInput.QUICK_MOVE);
            }
        }
    }

    /** «Выбросить всё»: выбрасывает содержимое контейнера целиком на землю. */
    public static void dropAll() {
        AbstractContainerMenu handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return;
        }
        for (int slot = 0; slot < count && slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasItem()) {
                // button=1 => выбросить весь стак (THROW всей ячейки).
                click(handler, slot, 1, ContainerInput.THROW);
            }
        }
    }

    /** Есть ли что перекладывать/выбрасывать из контейнера. */
    public static boolean hasContainerItems() {
        AbstractContainerMenu handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return false;
        }
        for (int slot = 0; slot < count && slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasItem()) {
                return true;
            }
        }
        return false;
    }

    /** Есть ли что складывать из инвентаря игрока. */
    public static boolean hasPlayerItems() {
        AbstractContainerMenu handler = handler();
        int count = containerSlotCount(handler);
        if (count < 0) {
            return false;
        }
        for (int slot = count; slot < handler.slots.size(); slot++) {
            if (handler.getSlot(slot).hasItem()) {
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
        if (mc.gui.screen() == null) {
            return false;
        }
        String title = mc.gui.screen().getTitle() != null ? mc.gui.screen().getTitle().getString() : "";
        if (isBlockedTitle(title)) {
            return true;
        }
        AbstractContainerMenu handler = handler();
        if (handler instanceof ChestMenu generic
                && generic.getRowCount() >= 6
                && handler.slots.size() > 49) {
            ItemStack refresh = handler.getSlot(49).getItem();
            if (!refresh.isEmpty() && (refresh.is(Items.CLOCK) || refresh.is(Items.NETHER_STAR))) {
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

    private static void click(AbstractContainerMenu handler, int slot, int button, ContainerInput type) {
        if (mc.gameMode == null || mc.player == null || handler == null) {
            return;
        }
        mc.gameMode.handleContainerInput(handler.containerId, slot, button, type, mc.player);
    }

    private static AbstractContainerMenu handler() {
        return mc.player == null ? null : mc.player.containerMenu;
    }
}
