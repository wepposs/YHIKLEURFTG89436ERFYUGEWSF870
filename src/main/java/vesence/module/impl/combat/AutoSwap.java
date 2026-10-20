package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.chat.Component;

import com.mojang.blaze3d.platform.InputConstants;

import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;
import vesence.utils.notifications.Notifications;
import vesence.utils.player.InventoryActionUtil;
import vesence.utils.render.text.ColorFormat;

import java.util.Locale;

@IModule(
        name = "AutoSwap",
        description = "Автоматический свап предметов через бинды",
        category = Category.COMBAT,
        bind = -1
)
@Environment(EnvType.CLIENT)
public class AutoSwap extends Module {

    /*
     * ============================================================
     * ОСНОВНЫЕ НАСТРОЙКИ
     * ============================================================
     */

    public final ModeSetting source = new ModeSetting(
            "Режим работы",
            "Обычный",
            "Обычный",
            "Трио свап"
    );

    public final ModeSetting target = new ModeSetting(
            "Фильтр свапов",
            "Без фильтра",
            "Без фильтра",
            "FT/RW"
    );

    public final ModeSetting firstItem = new ModeSetting(
            "Первый предмет",
            "Тотем",
            "Золотое яблоко",
            "Щит",
            "Шар",
            "Тотем"
    );

    public final ModeSetting secondItem = new ModeSetting(
            "Второй предмет",
            "Золотое яблоко",
            "Золотое яблоко",
            "Щит",
            "Шар",
            "Тотем"
    );

    public final BindSettings swapBind =
            new BindSettings(
                    "Кнопка",
                    InputConstants.UNKNOWN.getValue()
            );

    public final BooleanSetting notify =
            new BooleanSetting(
                    "Уведомление",
                    true
            );

    public final BooleanSetting onlyEnchanted =
            new BooleanSetting(
                    "Только зачарованные тотемы",
                    false
            );

    public final BooleanSetting spookyBypass =
            new BooleanSetting(
                    "Обход SpookyTime",
                    false
            );

    /*
     * ============================================================
     * СОСТОЯНИЕ
     * ============================================================
     */

    private boolean swapRequested;
    private boolean wasPressed;

    private static final long SWAP_COOLDOWN_MS = 100L;
    private static final long LOCK_TIMEOUT_MS = 300L;

    private long lastSwapTime;

    /*
     * Wild sourceSchedule -> состояние активного Трио свапа.
     */
    private boolean sourceSchedule;

    /*
     * Wild eventAttach -> чередование первого/второго предмета.
     */
    private boolean eventAttach;

    /*
     * Легитный / обход Spooky.
     */
    private boolean progress;
    private boolean allow;

    private long delay = -1L;

    private int cachedSlot = -1;

    /*
     * Легитный режим.
     */
    private boolean lockHeld;
    private boolean lockApplied;
    private long lockStartTime;

    /*
     * ============================================================
     * TRIO SWAP
     * ============================================================
     *
     * Wild хранил 3 дополнительных предмета.
     * В Vesence оставляем ту же структуру.
     */

    private final ItemStack[] positionAdvance = new ItemStack[]{
            ItemStack.EMPTY,
            ItemStack.EMPTY,
            ItemStack.EMPTY
    };

    private final String[] frameCheck = new String[]{
            "",
            "",
            ""
    };

    private int moduleCollect;
    private int providerClose;
    private int rendererScan;
    private int presetSave = -1;

    private ItemStack presetWrite = ItemStack.EMPTY;
    private String colorMeasure = "";

    private int animationSchedule;
    private int sourceBuild = -1;
    private String outputCollapse = "";

    /*
     * Используется как lock состояния Трио.
     */
    private boolean profileInvoke;

    /*
     * ============================================================
     * CONSTRUCTOR
     * ============================================================
     */

    public AutoSwap() {
        addSettings(
                source,
                target,
                firstItem,
                secondItem,
                swapBind,
                notify,
                onlyEnchanted,
                spookyBypass
        );
    }

    /*
     * ============================================================
     * UPDATE
     * ============================================================
     */

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (!this.enable) {
            return;
        }

        if (mc.player == null || mc.level == null) {
            performVector();
            return;
        }

        /*
         * ========================================================
         * SPOOKY BYPASS
         * ========================================================
         */

        if (spookyBypass.get() && progress) {
            handleSpookyProgress();
            return;
        }

        /*
         * ========================================================
         * TRIO SWAP
         * ========================================================
         */

        if (source.is("Трио свап")) {
            handleTrioUpdate();
            return;
        }

        /*
         * ========================================================
         * ОБЫЧНЫЙ РЕЖИМ
         * ========================================================
         */

        boolean pressed =
                swapBind.isPressed();

        if (pressed && !wasPressed) {
            swapRequested = true;
        }

        wasPressed = pressed;

        if (!swapRequested) {
            clearLock();
            return;
        }

        if (spookyBypass.get()) {
            int slot =
                    resolveTargetSlot();

            if (slot < 0) {
                swapRequested = false;
                clearLock();
                return;
            }

            progress = true;
            allow = true;
            cachedSlot = slot;

            if (mc.player.onGround()) {
                MoveComponent.stopTicks = 1 + (int) (Math.random() * 2);
                MoveComponent.stop = true;
            }

            swapRequested = false;
            return;
        }

        long now =
                System.currentTimeMillis();

        if (now - lastSwapTime < SWAP_COOLDOWN_MS) {
            return;
        }

        int targetSlot =
                resolveTargetSlot();

        if (targetSlot < 0) {
            swapRequested = false;
            clearLock();
            return;
        }

        /*
         * ========================================================
         * ЛЕГИТНЫЙ
         * ========================================================
         */

        if (getMode().equals("Легитный")) {

            if (!lockHeld) {
                MoveComponent.stop = true;

                lockHeld = true;
                lockApplied = false;
                lockStartTime = now;

                return;
            }

            if (!lockApplied) {
                lockApplied = true;
                return;
            }

            boolean canSwap =
                    mc.gui.screen() == null;

            if (canSwap) {
                performSwap(targetSlot);

                lastSwapTime = now;
                swapRequested = false;

                clearLock();

                return;
            }

            if (
                    lockHeld &&
                            now - lockStartTime >
                                    LOCK_TIMEOUT_MS
            ) {
                clearLock();
                swapRequested = false;
            }

            return;
        }

        /*
         * ========================================================
         * ОБЫЧНЫЙ
         * ========================================================
         */

        performSwap(targetSlot);

        lastSwapTime = now;
        swapRequested = false;
    }

    /*
     * ============================================================
     * TRIO UPDATE
     * ============================================================
     */

    private void handleTrioUpdate() {

        boolean pressed =
                swapBind.isPressed();

        if (pressed && !wasPressed) {
            swapRequested = true;
        }

        wasPressed = pressed;

        if (!swapRequested && !sourceSchedule) {
            return;
        }

        /*
         * Если активен процесс Трио свапа —
         * продолжаем его.
         */
        if (sourceSchedule) {

            if (!swapBind.isPressed()) {
                compute(false);
                return;
            }

            encodePoint();

            if (providerClose > 0) {
                providerClose--;
                return;
            }

            renderTrio();
            return;
        }

        if (!swapRequested) {
            return;
        }

        swapRequested = false;

        /*
         * Стартуем Трио.
         */
        unload();
    }

    /*
     * ============================================================
     * SPOOKY PROGRESS
     * ============================================================
     */

    private void handleSpookyProgress() {

        if (allow) {

            if (
                    isMoving() &&
                            mc.player.onGround()
            ) {
                MoveComponent.stopTicks =
                        1 + (int) (Math.random() * 2);

                MoveComponent.stop = true;
            } else {

                performSwap(cachedSlot);

                delay =
                        System.currentTimeMillis() +
                                250L;

                allow = false;
            }

        } else if (
                delay >= 0L &&
                        System.currentTimeMillis() >= delay
        ) {

            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;

            resetSpookyState();
        }
    }

    /*
     * ============================================================
     * РАЗРЕШЕНИЕ ПРЕДМЕТА
     * ============================================================
     */

    private String getMode() {
        /*
         * В твоём старом Vesence AutoSwap
         * mode назывался "Легитный/Обычный".
         *
         * Теперь source используется для Обычный/Трио свап,
         * поэтому отдельного ModeSetting больше нет.
         *
         * Обычный работает как быстрый свап.
         */
        return "Обычный";
    }

    private int resolveTargetSlot() {

        ItemStack offhand =
                mc.player.getOffhandItem();

        Item currentOff =
                offhand.getItem();

        Item first =
                resolveItem(
                        firstItem.get()
                );

        Item second =
                resolveItem(
                        secondItem.get()
                );

        if (currentOff == Items.AIR) {

            int firstSlot =
                    findSlot(first);

            if (firstSlot >= 0) {
                return firstSlot;
            }

            return findSlot(second);
        }

        if (currentOff == first) {
            return findSlot(second);
        }

        if (currentOff == second) {
            return findSlot(first);
        }

        int firstSlot =
                findSlot(first);

        if (firstSlot >= 0) {
            return firstSlot;
        }

        return findSlot(second);
    }

    private Item resolveItem(String name) {

        if (name == null) {
            return Items.AIR;
        }

        return switch (name) {
            case "Тотем" ->
                    Items.TOTEM_OF_UNDYING;

            case "Щит" ->
                    Items.SHIELD;

            case "Золотое яблоко" ->
                    Items.GOLDEN_APPLE;

            case "Шар" ->
                    Items.PLAYER_HEAD;

            default ->
                    Items.AIR;
        };
    }

    /*
     * ============================================================
     * ПОИСК СЛОТА
     * ============================================================
     */

    private int findSlot(Item item) {

        if (item == Items.AIR) {
            return -1;
        }

        boolean totem =
                item == Items.TOTEM_OF_UNDYING;

        for (int i = 0; i < 36; i++) {

            ItemStack stack =
                    mc.player.getInventory()
                            .getItem(i);

            if (!stack.is(item)) {
                continue;
            }

            if (
                    totem &&
                            onlyEnchanted.get() &&
                            !stack.hasFoil()
            ) {
                continue;
            }

            /*
             * Поведение твоего старого Vesence:
             * если "Только зачарованные" выключено,
             * сначала ищем обычный тотем.
             */
            if (
                    totem &&
                            !onlyEnchanted.get() &&
                            stack.hasFoil()
            ) {
                continue;
            }

            /*
             * Для FT/RW разрешаем головы.
             */
            if (
                    item == Items.PLAYER_HEAD &&
                            target.is("FT/RW")
            ) {
                if (isFunTimeHead(stack)) {
                    return i;
                }

                continue;
            }

            return i;
        }

        /*
         * Fallback для тотема.
         */
        if (totem) {

            for (int i = 0; i < 36; i++) {

                ItemStack stack =
                        mc.player.getInventory()
                                .getItem(i);

                if (stack.is(item)) {
                    return i;
                }
            }
        }

        /*
         * Fallback для FT/RW головы.
         */
        if (
                item == Items.PLAYER_HEAD &&
                        target.is("FT/RW")
        ) {
            return findFtHead();
        }

        return -1;
    }

    /*
     * ============================================================
     * FT/RW HEAD
     * ============================================================
     */

    private int findFtHead() {

        for (int i = 0; i < 36; i++) {

            ItemStack stack =
                    mc.player.getInventory()
                            .getItem(i);

            if (!stack.is(Items.PLAYER_HEAD)) {
                continue;
            }

            if (isFunTimeHead(stack)) {
                return i;
            }
        }

        return -1;
    }

    private boolean isFunTimeHead(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty() ||
                        !stack.is(Items.PLAYER_HEAD)
        ) {
            return false;
        }

        /*
         * Аналог Wild:
         *
         * Specials / attribute modifiers /
         * lore / custom name.
         */

        if (hasOffhandAttribute(stack)) {
            return true;
        }

        if (hasOffhandLore(stack)) {
            return true;
        }

        String name =
                normalize(
                        stack.getHoverName()
                                .getString()
                );

        return
                name.contains("шар") ||
                        name.contains("сфера") ||
                        name.contains("ft") ||
                        name.contains("rw");
    }

    private boolean hasOffhandAttribute(
            ItemStack stack
    ) {

        ItemAttributeModifiers modifiers =
                stack.get(
                        DataComponents
                                .ATTRIBUTE_MODIFIERS
                );

        if (modifiers == null) {
            return false;
        }

        for (
                ItemAttributeModifiers.Entry entry :
                modifiers.modifiers()
        ) {

            if (
                    entry.slot()
                            .test(
                                    EquipmentSlot.OFFHAND
                            )
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean hasOffhandLore(
            ItemStack stack
    ) {

        ItemLore lore =
                stack.get(
                        DataComponents.LORE
                );

        if (lore == null) {
            return false;
        }

        for (Component text : lore.lines()) {

            String line =
                    normalize(
                            text.getString()
                    );

            if (
                    line.contains(
                            "when in off hand"
                    ) ||
                            line.contains(
                                    "когда во второстепенной"
                            ) ||
                            line.contains(
                                    "при ношении в левой"
                            ) ||
                            line.contains(
                                    "в лівій руці"
                            )
            ) {
                return true;
            }
        }

        return false;
    }

    /*
     * ============================================================
     * SWAP
     * ============================================================
     */

    private void performSwap(
            int invSlot
    ) {

        if (
                invSlot < 0 ||
                        invSlot >= 36
        ) {
            return;
        }

        ItemStack targetStack =
                mc.player.getInventory()
                        .getItem(invSlot)
                        .copy();

        if (targetStack.isEmpty()) {
            return;
        }

        /*
         * Hotbar.
         */
        if (invSlot < 9) {

            InventoryActionUtil
                    .swapHotbarWithOffhandPacket(
                            invSlot
                    );

        } else {

            InventoryActionUtil
                    .swapWithOffhand(
                            invSlot
                    );
        }

        if (notify.get()) {

            try {

                String itemName =
                        vesence.utils.render.text
                                .RichTextUtil
                                .itemName(
                                        targetStack,
                                        255
                                );

                Notifications.add(
                        targetStack,
                        ColorFormat.color(
                                255,
                                255,
                                255
                        ) +
                                "Свап на: " +
                                itemName
                );

            } catch (Throwable ignored) {
            }
        }
    }

    /*
     * ============================================================
     * TRIO
     * ============================================================
     */

    private void unload() {

        sourceSchedule = true;

        moduleCollect = 1;
        providerClose = 0;

        sourceBuild = -1;
        outputCollapse = "";

        presetSave = -1;

        presetWrite =
                ItemStack.EMPTY;

        colorMeasure = "";

        animationSchedule = 0;
        rendererScan = 0;

        /*
         * Если сохранённых трёх предметов нет,
         * строим их из текущих настроек.
         */
        buildTrioPresets();
    }

    private void buildTrioPresets() {

        ArraysFill();

        Item first =
                resolveItem(
                        firstItem.get()
                );

        Item second =
                resolveItem(
                        secondItem.get()
                );

        if (first != Items.AIR) {
            positionAdvance[0] =
                    createInventoryStack(
                            first
                    );
        }

        if (second != Items.AIR) {
            positionAdvance[1] =
                    createInventoryStack(
                            second
                    );
        }

        /*
         * Третий слот — текущий offhand,
         * если он не пустой.
         */
        ItemStack offhand =
                mc.player.getOffhandItem();

        if (
                offhand != null &&
                        !offhand.isEmpty()
        ) {
            positionAdvance[2] =
                    offhand.copy();

            positionAdvance[2]
                    .setCount(1);
        }

        for (int i = 0; i < 3; i++) {

            if (
                    positionAdvance[i] != null &&
                            !positionAdvance[i].isEmpty()
            ) {
                frameCheck[i] =
                        processItem(
                                positionAdvance[i]
                        );
            }
        }
    }

    private ItemStack createInventoryStack(
            Item item
    ) {

        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        /*
         * Не создаём искусственный специальный предмет,
         * а пытаемся взять реальный экземпляр из инвентаря.
         */
        for (int i = 0; i < 36; i++) {

            ItemStack stack =
                    mc.player.getInventory()
                            .getItem(i);

            if (stack.is(item)) {

                ItemStack copy =
                        stack.copy();

                copy.setCount(1);

                return copy;
            }
        }

        return ItemStack.EMPTY;
    }

    private void renderTrio() {

        if (!sourceSchedule) {
            return;
        }

        /*
         * Wild сначала меняет состояние,
         * затем делает фактический свап.
         *
         * Здесь сохраняем ту же последовательность.
         */
        if (moduleCollect == 1) {

            moduleCollect = 2;
            providerClose = 1;

            return;
        }

        if (moduleCollect == 2) {

            int slot =
                    resolveTrioSlot();

            if (slot < 0) {

                moduleCollect = 3;
                providerClose = 1;

                return;
            }

            performSwap(slot);

            moduleCollect = 3;
            providerClose = 1;

            return;
        }

        if (moduleCollect == 3) {

            moduleCollect = 0;

            sourceSchedule = false;

            presetSave = -1;
            sourceBuild = -1;

            presetWrite =
                    ItemStack.EMPTY;

            colorMeasure = "";

            animationSchedule = 0;
            rendererScan = 0;
            providerClose = 0;

            matrixBlendReset();
        }
    }

    private int resolveTrioSlot() {

        /*
         * Сначала пытаемся найти предмет,
         * которого сейчас нет в offhand.
         */

        ItemStack offhand =
                mc.player.getOffhandItem();

        Item current =
                offhand.getItem();

        for (int i = 0; i < 3; i++) {

            ItemStack stack =
                    positionAdvance[i];

            if (
                    stack == null ||
                            stack.isEmpty()
            ) {
                continue;
            }

            if (stack.getItem() == current) {
                continue;
            }

            int slot =
                    findSlot(
                            stack.getItem()
                    );

            if (slot >= 0) {
                return slot;
            }
        }

        return -1;
    }

    /*
     * ============================================================
     * ITEM IDENTIFIER
     * ============================================================
     */

    private String processItem(
            ItemStack stack
    ) {

        if (
                stack == null ||
                        stack.isEmpty()
        ) {
            return "";
        }

        if (
                stack.is(
                        Items.PLAYER_HEAD
                ) &&
                        target.is("FT/RW") &&
                        isFunTimeHead(stack)
        ) {
            return "ft:sphere:any";
        }

        return
                "item:" +
                        net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(
                                        stack.getItem()
                                );
    }

    /*
     * ============================================================
     * STATE HELPERS
     * ============================================================
     */

    private boolean isMoving() {

        double x =
                mc.player.getDeltaMovement().x;

        double z =
                mc.player.getDeltaMovement().z;

        return
                Math.abs(x) > 0.003D ||
                        Math.abs(z) > 0.003D;
    }

    private void encodePoint() {

        if (mc.player == null) {
            return;
        }

        MoveComponent.stop = true;
        MoveComponent.stopTicks = 1;
    }

    private void compute(
            boolean restore
    ) {

        if (!sourceSchedule) {
            return;
        }

        sourceSchedule = false;

        moduleCollect = 0;
        providerClose = 0;

        if (restore) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
        }

        matrixBlendReset();
    }

    private void ArraysFill() {

        for (int i = 0; i < 3; i++) {
            positionAdvance[i] =
                    ItemStack.EMPTY;

            frameCheck[i] =
                    "";
        }
    }

    private void matrixBlendReset() {

        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;
    }

    private void performVector() {

        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;

        swapRequested = false;
        wasPressed = false;

        sourceSchedule = false;
        eventAttach = false;

        progress = false;
        allow = false;

        delay = -1L;

        cachedSlot = -1;

        clearLock();

        moduleCollect = 0;
        providerClose = 0;
        rendererScan = 0;
        presetSave = -1;

        sourceBuild = -1;
        outputCollapse = "";

        presetWrite =
                ItemStack.EMPTY;

        colorMeasure = "";

        animationSchedule = 0;
    }

    private void clearLock() {

        if (lockHeld) {

            MoveComponent.stop =
                    false;

            MoveComponent.stopTicks =
                    0;

            lockHeld =
                    false;

            lockApplied =
                    false;
        }
    }

    private void resetSpookyState() {

        progress = false;
        allow = false;

        delay = -1L;
        cachedSlot = -1;

        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;
    }

    private void cleanup() {

        clearLock();

        if (progress) {

            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;

            resetSpookyState();
        }

        sourceSchedule = false;
        moduleCollect = 0;
        providerClose = 0;
    }

    /*
     * ============================================================
     * STRING NORMALIZE
     * ============================================================
     */

    private String normalize(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value
                .replaceAll(
                        "§[0-9a-fk-orA-FK-OR]",
                        ""
                )
                .replaceAll(
                        "\\p{Cntrl}",
                        ""
                )
                .replace(
                        ' ',
                        ' '
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    /*
     * ============================================================
     * DISABLE
     * ============================================================
     */

    @Override
    public void onDisable() {

        performVector();
        cleanup();

        super.onDisable();
    }
}