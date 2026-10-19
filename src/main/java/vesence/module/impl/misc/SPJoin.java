package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.StringSetting;
import vesence.ui.clickgui.GuiClient;

/**
 * SPJoin — автоматический заход на сервер Spooky.
 *
 * Полный порт ru.whylol.client.modules.impl.misc.SPJoin из RelevantPremiumpp4
 * (MC 1.21.4) в vesence (MC 1.21.11, Fabric). Логика перенесена ЦЕЛИКОМ.
 *
 * Режимы:
 *   • "Дуэли"   — клик по компасу/слоту с дуэлями, ждёт scoreboard.
 *   • "Анархия" — после закрытия ClickGui отправляет указанную команду
 *                 (по умолчанию "/an") и ждёт scoreboard. Логика отключения
 *                 такая же, как в режиме дуэлей.
 *
 * Адаптации 1.21.4/ru.whylol -> 1.21.11/vesence:
 *   - @EventLink -> @EventInit; ModuleCategory.MISC -> Category.MISC.
 *   - TextSetting -> StringSetting; setting.visible(()->X) -> setting.hidden(()->!X).
 *   - ChatUtils.sendMessage -> mc.player.sendMessage(Text.literal(...), false).
 *   - MenuPanel (ClickGui открыт) -> mc.currentScreen instanceof GuiClient.
 *   - setEnabled(false) -> toggle() (самоотключение, как AutoDuel).
 *   - mc.player.getScoreboard() -> mc.world.getScoreboard().
 *   - getInventory().selectedSlot=X -> getInventory().setSelectedSlot(X).
 */
@IModule(name = "SPJoin", description = "Автоматический заход на Spooky", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class SPJoin extends Module {

    private final ModeSetting mode = new ModeSetting("Режим", "Дуэли", "Дуэли", "Анархия");
    // "Только Spooky" — только для режима "Дуэли"
    private final BooleanSetting onlySpooky = new BooleanSetting("Только Spooky", true)
            .hidden(() -> !this.mode.is("Дуэли"));
    // Поле команды — только для режима "Анархия"
    private final StringSetting anarchyCommand = new StringSetting("Команда", "/an")
            .hidden(() -> !this.mode.is("Анархия"));

    private int delayTicks = 0;
    private boolean hasScoreboard = false;

    public SPJoin() {
        this.addSettings(this.mode, this.onlySpooky, this.anarchyCommand);
    }

    @Override
    public void onEnable() {
        this.delayTicks = 0;
        this.hasScoreboard = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        this.delayTicks = 0;
        this.hasScoreboard = false;
        super.onDisable();
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE) return;
        if (!(event.getPacket() instanceof GameMessageS2CPacket packet)) return;

        String message = normalizeText(packet.content().getString());

        // Если сервер заполнен или другие ошибки - небольшая задержка
        if (message.contains("сервер заполнен") || message.contains("сервер переполнен")
                || message.contains("подождите 20 секунд") || message.contains("вы уже подключены")
                || message.contains("большой поток игроков")) {
            delayTicks = 10; // 0.5 секунды задержки (было 40)
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        // Если модуль отключен - ничего не делаем
        if (!this.enable) return;

        if (mc.world == null || mc.player == null || mc.interactionManager == null) return;

        // ПРОВЕРКА SCOREBOARD - если есть, отключаемся (общая логика для всех режимов)
        if (checkScoreboard()) {
            if (!hasScoreboard) {
                hasScoreboard = true;
                mc.player.sendMessage(Text.literal("§aУспешно зашли на сервер spookytime."), false);
                this.toggle();
            }
            return;
        }

        // Задержка между попытками
        if (delayTicks > 0) {
            delayTicks--;
            return;
        }

        // Развилка по режиму
        if (mode.is("Анархия")) {
            handleAnarchyMode();
        } else {
            handleDuelsMode();
        }
    }

    /**
     * Логика режима "Дуэли" — клик по компасу/слоту с дуэлями.
     */
    private void handleDuelsMode() {
        // Проверка "Только Spooky"
        if (onlySpooky.get()) {
            String serverAddress = getServerAddress();
            if (!serverAddress.toLowerCase().contains("spooky")) {
                return;
            }
        }

        // Если открыто GUI меню (HandledScreen) - РАБОТАЕТ ДАЖЕ В МЕНЮ ПАУЗЫ
        if (mc.currentScreen instanceof HandledScreen<?>) {
            tryClickInMenu();
            return;
        }

        // Если нет GUI или любой другой экран - пытаемся открыть компасом
        tryOpenCompass();
    }

    /**
     * Логика режима "Анархия" — ждём закрытия ClickGui, затем периодически
     * отправляем команду в чат, пока не появится scoreboard.
     */
    private void handleAnarchyMode() {
        // Пока ClickGui открыт — ничего не отправляем
        if (mc.currentScreen instanceof GuiClient) return;

        if (mc.player == null || mc.player.networkHandler == null) return;

        String raw = anarchyCommand.get();
        if (raw == null) return;
        String cmd = raw.trim();
        if (cmd.isEmpty()) return;

        // Отправляем как команду, если начинается с "/", иначе как сообщение
        if (cmd.startsWith("/")) {
            mc.player.networkHandler.sendChatCommand(cmd.substring(1));
        } else {
            mc.player.networkHandler.sendChatMessage(cmd);
        }

        // Спамим раз в ~1 секунду пока scoreboard не появится
        delayTicks = 20;
    }

    /**
     * Проверяет наличие scoreboard
     */
    private boolean checkScoreboard() {
        if (mc.player == null || mc.world == null) return false;

        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective sidebar = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);

        // Scoreboard есть, игрок существует больше 1 секунды, нет открытого GUI
        return sidebar != null && mc.player.age > 20 && mc.currentScreen == null;
    }

    /**
     * Пытается кликнуть в открытом меню на нужный слот
     */
    private void tryClickInMenu() {
        if (mc.player.currentScreenHandler == null || mc.player.currentScreenHandler.slots.isEmpty()) {
            return;
        }

        int targetSlot = findTargetSlot();
        if (targetSlot >= 0) {
            mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                targetSlot,
                0,
                SlotActionType.PICKUP,
                mc.player
            );
            delayTicks = 2; // Минимальная задержка (было 5)
        }
    }

    /**
     * Ищет нужный слот в меню — приоритет: предмет с "дуэл" в названии
     */
    private int findTargetSlot() {
        if (mc.player.currentScreenHandler == null) return -1;

        // Приоритет 1: ищем по названию "дуэл"
        for (int slot = 0; slot < mc.player.currentScreenHandler.slots.size(); slot++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(slot).getStack();
            if (stack == null || stack.isEmpty()) continue;

            String name = normalizeText(stack.getName().getString());
            if (name.contains("дуэл") || name.contains("1duels") || name.contains("duel")) {
                return slot;
            }
        }

        // Приоритет 2: якорь возрождения
        for (int slot = 0; slot < mc.player.currentScreenHandler.slots.size(); slot++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(slot).getStack();
            if (stack != null && !stack.isEmpty() && stack.isOf(Items.RESPAWN_ANCHOR)) {
                return slot;
            }
        }

        // Приоритет 3: другие ключевые слова
        for (int slot = 0; slot < mc.player.currentScreenHandler.slots.size(); slot++) {
            ItemStack stack = mc.player.currentScreenHandler.getSlot(slot).getStack();
            if (stack == null || stack.isEmpty()) continue;

            String name = normalizeText(stack.getName().getString());
            if (name.contains("якор") || name.contains("respawn") || name.contains("anchor")) {
                return slot;
            }
        }

        return -1;
    }

    /**
     * Пытается открыть меню компасом
     */
    private void tryOpenCompass() {
        int compassSlot = findCompassSlot();
        if (compassSlot < 0) {
            delayTicks = 5; // Если нет компаса, короткая задержка (было 10)
            return;
        }

        // Переключаемся на слот с компасом
        mc.player.getInventory().setSelectedSlot(compassSlot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(compassSlot));

        // Используем компас
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        mc.player.swingHand(Hand.MAIN_HAND);

        delayTicks = 2; // Минимальная задержка (было 5)
    }

    /**
     * Ищет компас или другой селектор в хотбаре
     */
    private int findCompassSlot() {
        if (mc.player == null) return -1;

        // Сначала проверяем слоты 3 и 4
        for (int slot : new int[]{3, 4}) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isCompass(stack)) {
                return slot;
            }
        }

        // Потом проверяем весь хотбар
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (isCompass(stack)) {
                return slot;
            }
        }

        return -1;
    }

    /**
     * Проверяет, является ли предмет компасом или селектором
     */
    private boolean isCompass(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        // Проверяем по типу предмета
        if (stack.isOf(Items.COMPASS) || stack.isOf(Items.CLOCK)
                || stack.isOf(Items.NETHER_STAR) || stack.isOf(Items.PLAYER_HEAD)) {
            return true;
        }

        // Проверяем по названию
        String name = normalizeText(stack.getName().getString());
        return name.contains("дуэл") || name.contains("режим") || name.contains("сервер")
                || name.contains("игра") || name.contains("меню") || name.contains("compass");
    }

    /**
     * Получает адрес текущего сервера
     */
    private String getServerAddress() {
        ServerInfo serverInfo = mc.getCurrentServerEntry();
        return serverInfo == null || serverInfo.address == null ? "" : serverInfo.address;
    }

    /**
     * Нормализует текст (убирает цветовые коды и переводит в нижний регистр)
     */
    private String normalizeText(String input) {
        if (input == null || input.isEmpty()) return "";

        StringBuilder builder = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // Пропускаем цветовые коды (§ + следующий символ)
            if (c == '\u00A7' && i + 1 < input.length()) {
                i++;
                continue;
            }
            builder.append(Character.toLowerCase(c));
        }
        return builder.toString();
    }
}
