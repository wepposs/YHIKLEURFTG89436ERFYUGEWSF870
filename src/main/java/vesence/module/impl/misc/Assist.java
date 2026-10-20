package vesence.module.impl.misc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.MoveComponent;
import vesence.utils.player.InventoryActionUtil;
import vesence.utils.other.Mathf;
import vesence.utils.other.TimerUtil;

@IModule(name = "Server Assist", description = "Быстрое использование предметов по кнопке", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class Assist extends Module {
    public final ModeSetting mode = new ModeSetting("Режим", "FunTime", "FunTime", "HolyWorld", "SpookyTime");
    public final BindSettings windChargeBind = new BindSettings("Виндхоп", -1)
            .hidden(() -> mode.is("SpookyTime"));
    public final BindSettings ftDisorientationBind = new BindSettings("Дезориентация", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftTrapBind = new BindSettings("Трапка", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftDustBind = new BindSettings("Явная пыль", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftCrossbowBind = new BindSettings("Арбалет", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftPlastBind = new BindSettings("Пласт", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftAuraBind = new BindSettings("Божья аура", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftSnowballBind = new BindSettings("Снежок", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BindSettings ftSmerchBind = new BindSettings("Смерч", -1)
            .hidden(() -> !mode.is("FunTime"));
    public final BooleanSetting ftAutoAura = new BooleanSetting("Авто божья аура", true)
            .hidden(() -> !mode.is("FunTime"));

    public final BindSettings hwExplosiveTrapBind = new BindSettings("Взрывная трапка", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwStanBind = new BindSettings("Стан", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwExplosiveThingBind = new BindSettings("Взрывная штучка", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwTrapBind = new BindSettings("Трапка", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BindSettings hwSnowballBind = new BindSettings("Ком снега", -1)
            .hidden(() -> !mode.is("HolyWorld"));
    public final BooleanSetting hwBypass = new BooleanSetting("Обход", true)
            .hidden(() -> !mode.is("HolyWorld"));

    // ── Авто хорус: если цель ауры рядом (<5) ест хорус (телепортируется), сами
    //    берём хорус и едим (телепортируемся следом) + таргетим её. Вне режимов
    //    FunTime/HolyWorld (мир-агностик). Порт логики авто-хоруса. ──
    public final BooleanSetting autoChorus = new BooleanSetting("Авто хорус", false)
            .hidden(() -> mode.is("SpookyTime"));
    private boolean usingChorus = false;
    private int chorusPrevSlot = -1;
    private int chorusInvSlot = -1;
    private final TimerUtil chorusTargetTimer = new TimerUtil();
    private final TimerUtil chorusWaitTimer = new TimerUtil();
    private double chorusPrevX;
    private double chorusPrevZ;
    private boolean chorusPosInit = false;

    private final boolean[] wasPressed = new boolean[11];
    private int state = 0;
    private int savedCurrentSlot = -1;
    private int savedInvSlot = -1;
    private Item pendingItem = null;
    private int swapSlot = 8;
    private boolean wasFull = false;

    private enum HWState { IDLE, USE, SWAP_BACK }
    private HWState hwState = HWState.IDLE;
    private Item hwCurrentItem = null;
    private int hwOldHotbarSlot = -1;
    private int hwTargetHotbarSlot = -1;
    private int hwSwappedFromSlot = -1;
    private boolean hwRestoreSwappedSlot = false;
    private long hwActionStartTime = 0L;
    private final java.util.Random hwRandom = new java.util.Random();

    // ─────────────────────────────────────────────────────────────────────────
    //  SpookyTime — полный порт ServerHelper (режим "test"): атомарный
    //  swap→use→swap-back через офф-хенд, 2-тиковый обход Grim, матчинг зелий
    //  по эффектам, заряд ветра строго вниз, мгновенный выстрел арбалета.
    // ─────────────────────────────────────────────────────────────────────────
    public final BindSettings disorientationKey = new BindSettings("Дезориентация", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings trapKey           = new BindSettings("Трапка", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings plastKey          = new BindSettings("Пласт", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings dustKey           = new BindSettings("Явная пыль", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings snegKey           = new BindSettings("Снег заморозки", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings auraKey           = new BindSettings("Божья аура", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings fireTornadoKey    = new BindSettings("Огненный смерч", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings pearlKey          = new BindSettings("Эндер перл", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings jumpPotionKey     = new BindSettings("Зелье прыжка", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings weaknessPotionKey = new BindSettings("Зелье слабости", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings hlopushkaKey      = new BindSettings("Хлопушка", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings holyWaterKey      = new BindSettings("Святая вода", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings gnevKey           = new BindSettings("Зелье Гнева", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings paladinKey        = new BindSettings("Зелье Палладина", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings assassinKey       = new BindSettings("Зелье Ассасина", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings radiationKey      = new BindSettings("Зелье Радиации", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings snotvornoeKey     = new BindSettings("Снотворное", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings windChargeKey     = new BindSettings("Заряд ветра", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BindSettings crossbowKey       = new BindSettings("Выстрел арбалета", -1)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BooleanSetting grimBypass      = new BooleanSetting("Обход Grim", false)
            .hidden(() -> !mode.is("SpookyTime"));
    public final BooleanSetting swingArm        = new BooleanSetting("Анимация руки", true)
            .hidden(() -> !mode.is("SpookyTime"));

    // ── rising-edge опрос спуки-биндов ────────────────────────────────────────
    private BindSettings[] actionBinds;
    private Action[] actionMap;
    private boolean[] spookyWasPressed;
    private boolean wasWind;
    private boolean wasCross;

    // ── test pipeline state ───────────────────────────────────────────────────
    private final ArrayDeque<Action> testQueue = new ArrayDeque<>();
    private boolean testActive = false;

    /** Сколько тиков bypass осталось. 2=preStop, 1=action, 0=cleanup. */
    private int testBypassTicks = 0;
    private Action testBypassAction = null;
    private boolean testBypassWasSprinting = false;

    private int testUseCooldownTicks = 0;
    private static final int TEST_USE_COOLDOWN_TICKS = 3;

    private Action testWaitStillAction = null;
    private long testWaitStillDeadline = 0L;
    private static final long TEST_WAIT_STILL_MAX_MS = 110L;
    private static final double TEST_PLAST_STILL_EPSILON = 0.02;

    /** Глобальный флаг подавления vanilla input во время SpookyTime-цепочки. */
    public static volatile boolean SUPPRESS_VANILLA_USE = false;

    /** Отслеживание смены режима — уходя из режима, чистим его состояние/флаги. */
    private String lastMode = null;

    public Assist() {
        addSettings(
                mode,
                autoChorus,
                windChargeBind,
                ftDisorientationBind, ftTrapBind, ftDustBind, ftCrossbowBind, ftPlastBind, ftAuraBind, ftSnowballBind, ftSmerchBind, ftAutoAura,
                hwExplosiveTrapBind, hwStanBind, hwExplosiveThingBind, hwTrapBind, hwSnowballBind, hwBypass,
                // ── SpookyTime (порт ServerHelper) — добавлены В КОНЕЦ, чтобы
                //    ключи конфига FunTime/HolyWorld не сдвинулись. ──
                disorientationKey, trapKey, plastKey, dustKey, snegKey, auraKey, fireTornadoKey, pearlKey,
                jumpPotionKey, weaknessPotionKey, hlopushkaKey, holyWaterKey, gnevKey, paladinKey, assassinKey,
                radiationKey, snotvornoeKey, windChargeKey, crossbowKey, grimBypass, swingArm
        );

        this.actionBinds = new BindSettings[]{
                this.disorientationKey, this.trapKey, this.plastKey, this.dustKey,
                this.snegKey, this.auraKey, this.fireTornadoKey, this.pearlKey,
                this.jumpPotionKey, this.weaknessPotionKey, this.hlopushkaKey,
                this.holyWaterKey, this.gnevKey, this.paladinKey, this.assassinKey,
                this.radiationKey, this.snotvornoeKey
        };
        this.actionMap = new Action[]{
                Action.DISORIENTATION, Action.TRAP, Action.PLAST, Action.DUST,
                Action.FREEZE_SNOW, Action.AURA, Action.FIRE_TORNADO, Action.ENDER_PEARL,
                Action.JUMP_POTION, Action.WEAKNESS_POTION, Action.HLOPUSHKA,
                Action.HOLY_WATER, Action.GNEV, Action.PALADIN, Action.ASSASSIN,
                Action.RADIATION, Action.SNOTVORNOE
        };
        this.spookyWasPressed = new boolean[this.actionBinds.length];
    }

    @Override
    public void onEnable() {
        this.resetTestState();
        this.lastMode = this.mode.get();
        super.onEnable();
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!this.enable) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.getConnection() == null) {
            this.testQueue.clear();
            return;
        }

        // Уходя из режима — чистим состояние покинутого режима, чтобы флаги
        // (MoveComponent.stop / SUPPRESS_VANILLA_USE / GrimBypass) не залипли.
        handleModeSwitch();

        if (mode.is("SpookyTime")) {
            // Поведение ServerHelper 1:1 (проверки экрана — внутри методов).
            this.checkBinds();
            this.testTick();
            return;
        }

        handleAutoChorus();

        if (mc.gui.screen() != null) return;

        if (mode.is("FunTime")) {
            handleFunTime();
        } else {
            handleHolyWorld();
        }
    }

    @EventInit
    public void onPacket(EventPacket e) {
        if (!this.enable) return;

        if (mode.is("SpookyTime")) {
            // SEND: GrimBypass подменяет поля исходящего ServerboundMovePlayerPacket пока
            // активен bypass-цикл SpookyTime (только если включён «Обход Grim»).
            if (!this.grimBypass.get() || !GrimBypass.ACTIVE) return;
            if (!e.isSend()) return;
            if (!(e.getPacket() instanceof ServerboundMovePlayerPacket move)) return;
            if (mc.player == null || mc.level == null) return;
            try {
                GrimBypass.handleMovement(move);
            } catch (Throwable ignored) {}
            return;
        }

        // Авто хорус: телепорт цели (position-sync) продлевает предохранитель.
        if (!autoChorus.get() || !usingChorus || e.isSend()) return;
        LivingEntity target = AttackAura.target;
        if (target == null) return;
        if (e.getPacket() instanceof ClientboundEntityPositionSyncPacket p && p.id() == target.getId()) {
            chorusWaitTimer.reset();
        }
    }

    /** Смена режима — полная очистка состояния всех подрежимов и глобальных флагов. */
    private void handleModeSwitch() {
        String cur = mode.get();
        if (this.lastMode == null) {
            this.lastMode = cur;
            return;
        }
        if (cur.equals(this.lastMode)) return;
        this.lastMode = cur;

        // Авто хорус.
        stopChorus();
        // FunTime.
        resetState();
        for (int i = 0; i < wasPressed.length; i++) wasPressed[i] = false;
        // HolyWorld.
        resetHWState();
        // SpookyTime.
        resetTestState();
        // Глобальные флаги.
        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;
        GrimBypass.ACTIVE = false;
        GrimBypass.reset();
        SUPPRESS_VANILLA_USE = false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  АВТО ХОРУС: цель ауры рядом ест хорус -> сами едим хорус (телепорт следом)
    //  и таргетим её. Свап хоруса в руку — через InventoryActionUtil.
    // ─────────────────────────────────────────────────────────────────────────

    private void handleAutoChorus() {
        if (!autoChorus.get() || mc.player == null || mc.level == null || mc.gui.screen() != null) {
            stopChorus();
            return;
        }

        LivingEntity target = AttackAura.target;

        if (!usingChorus) {
            // Старт: цель рядом (<5) и ест хорус не меньше 200мс (как в оригинале).
            if (target != null && target.isAlive() && mc.player.distanceTo(target) < 5.0F) {
                boolean targetEating = target.isUsingItem() && target.getActiveItem().is(Items.CHORUS_FRUIT);
                if (!targetEating) {
                    chorusTargetTimer.reset();
                } else if (chorusTargetTimer.hasReached(200)) {
                    startChorus();
                    // Таргетим ближайшую цель ауры (аура довернёт).
                    if (AttackAura.target == null) {
                        AttackAura.target = target;
                    }
                }
            } else {
                chorusTargetTimer.reset();
            }
            return;
        }

        // Уже едим: держим клавишу использования (на случай сброса ввода).
        mc.options.keyUse.setDown(true);

        // Наш телепорт (хорус съеден) = резкий ГОРИЗОНТАЛЬНЫЙ скачок позиции -> стоп.
        double cx = mc.player.getX();
        double cz = mc.player.getZ();
        if (chorusPosInit && Math.hypot(cx - chorusPrevX, cz - chorusPrevZ) > 2.0) {
            stopChorus();
            return;
        }
        chorusPrevX = cx;
        chorusPrevZ = cz;
        chorusPosInit = true;

        // Предохранитель по времени (продлевается телепортом цели — см. onPacket).
        if (chorusWaitTimer.hasReached(2500)) {
            stopChorus();
        }
    }

    private void startChorus() {
        int slot = findChorusSlot();
        if (slot == -1) return;
        chorusPrevSlot = mc.player.getInventory().getSelectedSlot();
        if (slot <= 8) {
            chorusInvSlot = -1;
            InventoryActionUtil.selectSlot(slot);
        } else {
            chorusInvSlot = slot;
            InventoryActionUtil.swapWithHotbar(slot, chorusPrevSlot); // хорус -> текущий слот руки
        }
        mc.options.keyUse.setDown(true);
        usingChorus = true;
        chorusPrevX = mc.player.getX();
        chorusPrevZ = mc.player.getZ();
        chorusPosInit = true;
        chorusWaitTimer.reset();
    }

    private void stopChorus() {
        if (!usingChorus) return;
        if (mc.options != null && mc.options.keyUse != null) {
            mc.options.keyUse.setDown(false);
        }
        if (mc.player != null) {
            if (chorusInvSlot >= 0) {
                InventoryActionUtil.swapWithHotbar(chorusInvSlot, chorusPrevSlot); // возвращаем хорус в инвентарь
            } else if (chorusPrevSlot >= 0 && chorusPrevSlot <= 8) {
                InventoryActionUtil.selectSlot(chorusPrevSlot);
            }
        }
        usingChorus = false;
        chorusPrevSlot = -1;
        chorusInvSlot = -1;
        chorusPosInit = false;
    }

    private int findChorusSlot() {
        int hotbar = InventoryActionUtil.findItem(Items.CHORUS_FRUIT, true);
        if (hotbar != -1) return hotbar;
        return InventoryActionUtil.findItem(Items.CHORUS_FRUIT, false);
    }

    private void handleFunTime() {
        if (mc.gui.screen() != null) return;

        switch (state) {
            case 1: {
                if (!InventoryActionUtil.swapWithHotbar(savedInvSlot, swapSlot)) {
                    resetState();
                    return;
                }
                wasFull = true;
                state = 2;
                return;
            }
            case 2: {
                InventoryActionUtil.useHand(InteractionHand.MAIN_HAND);
                state = 3;
                return;
            }
            case 3: {
                if (wasFull && savedInvSlot != -1) {
                    InventoryActionUtil.swapWithHotbar(savedInvSlot, swapSlot);
                }
                resetState();
                return;
            }
            case 4: {
                InventoryActionUtil.swapHotbarSlots(savedCurrentSlot, swapSlot);
                state = 5;
                return;
            }
            case 5: {
                InventoryActionUtil.useHand(InteractionHand.MAIN_HAND);
                state = 6;
                return;
            }
            case 6: {
                InventoryActionUtil.swapHotbarSlots(savedCurrentSlot, swapSlot);
                resetState();
                return;
            }
        }

        if (ftAutoAura.get()) {
            boolean hasNegative = false;
            MobEffectInstance poison = mc.player.getEffect(MobEffects.POISON);
            if (poison != null && poison.getAmplifier() >= 0 && poison.getDuration() >= 50 * 20) {
                hasNegative = true;
            }
            MobEffectInstance slowness = mc.player.getEffect(MobEffects.SLOWNESS);
            if (slowness != null && slowness.getAmplifier() >= 2 && slowness.getDuration() >= 90 * 20) {
                hasNegative = true;
            }
            MobEffectInstance weakness = mc.player.getEffect(MobEffects.WEAKNESS);
            if (weakness != null && weakness.getAmplifier() >= 1 && weakness.getDuration() >= 90 * 20) {
                hasNegative = true;
            }
            MobEffectInstance wither = mc.player.getEffect(MobEffects.WITHER);
            if (wither != null && wither.getAmplifier() >= 3 && wither.getDuration() >= 30 * 20) {
                hasNegative = true;
            }

            if (hasNegative) {
                useItem("божья", Items.PHANTOM_MEMBRANE);
            }
        }

        tryUse(ftDisorientationBind, 0, "дезориентация", Items.ENDER_EYE);
        tryUse(ftTrapBind, 1, "трапка", Items.NETHERITE_SCRAP);
        tryUse(ftDustBind, 2, "явная", Items.SUGAR);
        tryUse(ftCrossbowBind, 3, null, Items.CROSSBOW);
        tryUse(ftPlastBind, 6, "пласт", Items.DRIED_KELP);
        tryUse(ftAuraBind, 7, "божья", Items.PHANTOM_MEMBRANE);
        tryUse(ftSnowballBind, 8, "снежок", Items.SNOWBALL);
        tryUse(windChargeBind, 9, "виндхоп", Items.WIND_CHARGE);
        tryUse(ftSmerchBind, 10, "смерч", Items.FIRE_CHARGE);
    }

    private void tryUse(BindSettings bind, int index, String nameSubstring, Item item) {
        if (bind.key != -1 && bind.isDown()) {
            if (!wasPressed[index]) {
                wasPressed[index] = true;
                useItem(nameSubstring, item);
            }
        } else {
            wasPressed[index] = false;
        }
    }

    private void useItem(String nameSubstring, Item item) {
        if (state != 0) return;
        if (mc.gui.screen() != null) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        int hotbarSlot = findItemSlot(nameSubstring, item, true);

        if (hotbarSlot != -1) {
            if (hotbarSlot == currentSlot) {
                InventoryActionUtil.useHand(InteractionHand.MAIN_HAND);
            } else {
                MoveComponent.stop = true;
                savedCurrentSlot = currentSlot;
                swapSlot = hotbarSlot;
                state = 4;
            }
            return;
        }

        int invSlot = findItemSlot(nameSubstring, item, false);
        if (invSlot == -1) return;

        MoveComponent.stop = true;
        savedCurrentSlot = currentSlot;
        savedInvSlot = invSlot;
        swapSlot = currentSlot;
        state = 1;
    }

    private void resetState() {
        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;
        state = 0;
        pendingItem = null;
        wasFull = false;
    }

    private void handleHolyWorld() {
        if (mc.gui.screen() != null) return;

        checkHolyWorldBinds();

        if (hwState == HWState.IDLE) {
            return;
        }

        long now = System.currentTimeMillis();

        switch (hwState) {
            case USE: {
                if (now - hwActionStartTime >= 30 + hwRandom.nextInt(20)) {
                    InventoryActionUtil.useHand(InteractionHand.MAIN_HAND);
                    hwState = HWState.SWAP_BACK;
                    hwActionStartTime = now;
                }
                break;
            }
            case SWAP_BACK: {
                if (now - hwActionStartTime >= 45 + hwRandom.nextInt(15)) {
                    resetHWState();
                }
                break;
            }
        }
    }

    private void checkHolyWorldBinds() {
        if (hwState != HWState.IDLE) return;
        if (mc.gui.screen() != null) return;

        Item selectedItem = null;
        if (hwExplosiveTrapBind.key != -1 && hwExplosiveTrapBind.isDown()) {
            selectedItem = Items.PRISMARINE_SHARD;
        } else if (hwStanBind.key != -1 && hwStanBind.isDown()) {
            selectedItem = Items.NETHER_STAR;
        } else if (hwExplosiveThingBind.key != -1 && hwExplosiveThingBind.isDown()) {
            selectedItem = Items.FIRE_CHARGE;
        } else if (hwTrapBind.key != -1 && hwTrapBind.isDown()) {
            selectedItem = Items.POPPED_CHORUS_FRUIT;
        } else if (hwSnowballBind.key != -1 && hwSnowballBind.isDown()) {
            selectedItem = Items.SNOWBALL;
        } else if (windChargeBind.key != -1 && windChargeBind.isDown()) {
            selectedItem = Items.WIND_CHARGE;
        }

        if (selectedItem != null) {
            if (mc.player.getCooldowns().isOnCooldown(new ItemStack(selectedItem))) {
                return;
            }

            if (hwBypass.get()) {
                MoveComponent.stopTicks = (int) Mathf.randomValue(1, 2);
                MoveComponent.stop = true;
            }

            hwCurrentItem = selectedItem;
            hwOldHotbarSlot = mc.player.getInventory().getSelectedSlot();

            int hotbarSlot = findItemSlot(null, selectedItem, true);
            if (hotbarSlot != -1) {
                if (hotbarSlot == hwOldHotbarSlot) {
                    hwTargetHotbarSlot = hwOldHotbarSlot;
                    hwState = HWState.USE;
                    hwActionStartTime = System.currentTimeMillis();
                } else {
                    InventoryActionUtil.swapHotbarSlots(hwOldHotbarSlot, hotbarSlot);
                    hwTargetHotbarSlot = hwOldHotbarSlot;
                    hwRestoreSwappedSlot = true;
                    hwSwappedFromSlot = hotbarSlot;
                    hwState = HWState.USE;
                    hwActionStartTime = System.currentTimeMillis();
                }
            } else {
                int invSlot = findItemSlot(null, selectedItem, false);
                if (invSlot == -1) {
                    if (hwBypass.get()) {
                        MoveComponent.stop = false;
                        MoveComponent.stopTicks = 0;
                    }
                    return;
                }

                hwTargetHotbarSlot = hwOldHotbarSlot;
                hwSwappedFromSlot = invSlot;
                hwRestoreSwappedSlot = InventoryActionUtil.swapWithHotbar(invSlot, hwOldHotbarSlot);
                if (!hwRestoreSwappedSlot) {
                    if (hwBypass.get()) {
                        MoveComponent.stop = false;
                        MoveComponent.stopTicks = 0;
                    }
                    resetHWState();
                    return;
                }
                hwState = HWState.USE;
                hwActionStartTime = System.currentTimeMillis();
            }
        }
    }

    private void resetHWState() {
        if (hwRestoreSwappedSlot && hwSwappedFromSlot != -1 && hwTargetHotbarSlot >= 0) {
            if (hwSwappedFromSlot < 9) {
                InventoryActionUtil.swapHotbarSlots(hwTargetHotbarSlot, hwSwappedFromSlot);
            } else {
                InventoryActionUtil.swapWithHotbar(hwSwappedFromSlot, hwTargetHotbarSlot);
            }
        }
        hwState = HWState.IDLE;
        hwCurrentItem = null;
        hwOldHotbarSlot = -1;
        hwTargetHotbarSlot = -1;
        hwSwappedFromSlot = -1;
        hwRestoreSwappedSlot = false;
        if (hwBypass.get()) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
        }
    }

    private int findItemSlot(String nameSubstring, Item fallbackItem, boolean inHotbar) {
        int start = inHotbar ? 0 : 9;
        int end = inHotbar ? 9 : 36;
        for (int i = start; i < end; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            if (nameSubstring != null) {
                String displayName = normalizeName(stack.getHoverName().getString());
                String query = normalizeName(nameSubstring);
                if (displayName.contains(query) || displayName.contains(decodeMojibake(query))) {
                    return i;
                }
            }

            if (fallbackItem != null && stack.is(fallbackItem)) {
                return i;
            }
        }
        return -1;
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("\u00a7.", "").toLowerCase(Locale.ROOT);
    }

    private String decodeMojibake(String value) {
        try {
            return new String(value.getBytes(java.nio.charset.Charset.forName("windows-1251")), java.nio.charset.StandardCharsets.UTF_8)
                    .toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return value;
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══ SpookyTime pipeline (порт ServerHelper — режим "test") ════════════════
    // ══════════════════════════════════════════════════════════════════════════

    private void resetTestState() {
        this.testQueue.clear();
        this.testActive = false;
        this.testWaitStillAction = null;
        this.testBypassTicks = 0;
        this.testBypassAction = null;
        this.testBypassWasSprinting = false;
        this.testUseCooldownTicks = 0;
        if (this.spookyWasPressed != null) {
            java.util.Arrays.fill(this.spookyWasPressed, false);
        }
        this.wasWind = false;
        this.wasCross = false;
    }

    /** Rising-edge опрос всех спуки-биндов (в vesence нет EventBinding). */
    private void checkBinds() {
        if (mc.gui.screen() != null) return;

        for (int i = 0; i < this.actionBinds.length; i++) {
            BindSettings b = this.actionBinds[i];
            boolean pressed = b.key != -1 && b.isDown();
            if (pressed && !this.spookyWasPressed[i]) {
                Action a = this.actionMap[i];
                // Один бинд может быть назначен на несколько предметов — тогда по
                // нажатию юзаются они все (по порядку); дубли подряд не копим.
                if (this.testQueue.isEmpty() || this.testQueue.peekLast() != a) {
                    this.testQueue.addLast(a);
                }
            }
            this.spookyWasPressed[i] = pressed;
        }

        boolean wc = this.windChargeKey.key != -1 && this.windChargeKey.isDown();
        if (wc && !this.wasWind) {
            boolean thrown = this.throwWindChargeDown(this.swingArm.get());
            this.msg(thrown ? "Бросил заряд ветра!" : "Заряд ветра не найден!");
        }
        this.wasWind = wc;

        boolean cb = this.crossbowKey.key != -1 && this.crossbowKey.isDown();
        if (cb && !this.wasCross) {
            this.fireCrossbow();
        }
        this.wasCross = cb;
    }

    private void msg(String s) {
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(s));
        }
    }

    /** testActive → блокировка движения через MoveComponent (замена EventMoveInput). */
    private void setTestActive(boolean b) {
        this.testActive = b;
        MoveComponent.stop = b;
    }

    /** Замена InventoryWalk.runBypassingPacketFilter — в vesence нет
     *  пакет-фильтра инвентаря, поэтому просто выполняем действие. */
    private void runInv(Runnable r) {
        r.run();
    }

    private void testTick() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) {
            this.testWaitStillAction = null;
            this.testBypassTicks = 0;
            this.testBypassAction = null;
            return;
        }
        if (mc.gui.screen() != null) {
            this.testWaitStillAction = null;
            return;
        }

        if (this.testUseCooldownTicks > 0) this.testUseCooldownTicks--;

        // ── Двухтиковый bypass ───────────────────────────────────────────────
        if (this.testBypassTicks > 0) {
            this.testBypassTicks--;
            if (this.testBypassTicks == 1) {
                // tick=1 (action): движение НЕ глушим, концентрированный свап+use.
                Action act = this.testBypassAction;
                this.testBypassAction = null;
                this.setTestActive(false);
                if (act != null) {
                    this.testExecuteUse(act);
                }
            } else if (this.testBypassTicks == 0) {
                // tick=0 (cleanup): восстанавливаем sprint-input, снимаем флаги.
                if (this.testBypassWasSprinting) {
                    try {
                        mc.player.connection.send(new ServerboundPlayerInputPacket(mc.player.input.keyPresses));
                    } catch (Throwable ignored) {}
                    this.testBypassWasSprinting = false;
                }
                SUPPRESS_VANILLA_USE = false;
                this.setTestActive(false);
                this.testUseCooldownTicks = TEST_USE_COOLDOWN_TICKS;
            } else {
                // tick=2 (preStop): глушим движение/спринт, ждём stationary tick.
                this.setTestActive(true);
                SUPPRESS_VANILLA_USE = true;
            }
            return;
        }

        // ── Фаза ожидания остановки (только для пласта) ──────────────────────
        if (this.testWaitStillAction != null) {
            this.setTestActive(true);
            SUPPRESS_VANILLA_USE = true;
            boolean still = mc.player.getDeltaMovement().horizontalDistanceSqr() < TEST_PLAST_STILL_EPSILON;
            boolean timedOut = System.currentTimeMillis() >= this.testWaitStillDeadline;
            if (still || timedOut) {
                Action a = this.testWaitStillAction;
                this.testWaitStillAction = null;
                this.beginBypass(a);
            }
            return;
        }

        // ── Троттлинг между использованиями ──────────────────────────────────
        if (this.testUseCooldownTicks > 0) {
            return;
        }

        // ── Берём следующее действие ─────────────────────────────────────────
        Action next = this.testQueue.peekFirst();
        if (next == null) {
            // Очередь пуста — снимаем bypass-окружение.
            if (GrimBypass.ACTIVE) {
                GrimBypass.ACTIVE = false;
                GrimBypass.reset();
            }
            if (this.testActive) {
                this.setTestActive(false);
                SUPPRESS_VANILLA_USE = false;
            }
            return;
        }
        this.testQueue.pollFirst();

        // Пласт требует полной остановки.
        if (this.testNeedsStill(next)
                && mc.player.getDeltaMovement().horizontalDistanceSqr() >= TEST_PLAST_STILL_EPSILON) {
            this.testWaitStillAction = next;
            this.testWaitStillDeadline = System.currentTimeMillis() + TEST_WAIT_STILL_MAX_MS;
            return;
        }

        this.beginBypass(next);
    }

    /**
     * Начинает 2-тиковый bypass: tick=2 preStop (stop-sprint, глушим движение),
     * tick=1 action (свап+use), tick=0 cleanup.
     */
    private void beginBypass(Action act) {
        this.testBypassWasSprinting = false;
        if (mc.player.isSprinting()) {
            try {
                mc.player.connection.send(
                        new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, false, false, false, false, false, false)));
                mc.player.setSprinting(false);
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                mc.options.keySprint.setDown(false);
                this.testBypassWasSprinting = true;
            } catch (Throwable ignored) {}
        }
        this.setTestActive(true);
        SUPPRESS_VANILLA_USE = true;
        if (this.grimBypass.get()) {
            GrimBypass.ACTIVE = true;
        }
        this.testBypassAction = act;
        this.testBypassTicks = 2;
    }

    /** Требует ли действие полной остановки перед use. Только пласт. */
    private boolean testNeedsStill(Action act) {
        return act == Action.PLAST;
    }

    /** Бросаемый снаряд (снежок/перл): возврат слота откладываем чтобы не
     *  откатить client-prediction броска. */
    private boolean testIsProjectile(Action act) {
        return act == Action.FREEZE_SNOW || act == Action.ENDER_PEARL;
    }

    /** Единое использование руки. interactItem сам делает swingHand при успехе. */
    private void testUseHand(InteractionHand hand, boolean raw) {
        mc.gameMode.useItem(mc.player, hand);
    }

    /**
     * Публичная точка использования ФЕЙЕРВЕРКА через реализацию SpookyTime.
     * Вызывается из ElytraSwap («elytra helper»).
     *
     * ГАРАНТИРОВАННЫЙ use через ОФФ-ХЕНД (левая рука, слот 40) — тот же движок,
     * что и у всех остальных предметов SpookyTime (testExecuteUse). Main-hand НЕ
     * трогаем, ракета берётся из руки/офф-хенда/хотбара/ОСНОВНОГО инвентаря
     * (9..35). Полностью синхронно; работает при ЗАКРЫТОМ и открытом инвентаре и
     * НЕ требует включённого модуля/режима SpookyTime.
     *
     * @param grimBypass если true — перед use гасим спринт (как beginBypass).
     * @return {@code true} если фейерверк найден и использован.
     */
    public boolean useFirework(boolean grimBypass) {
        if (mc.player == null || mc.level == null || mc.gameMode == null
                || mc.getConnection() == null) {
            return false;
        }
        if (grimBypass && mc.player.isSprinting()) {
            try {
                mc.player.connection.send(
                        new ServerboundPlayerInputPacket(new net.minecraft.world.entity.player.Input(false, false, false, false, false, false, false)));
                mc.player.setSprinting(false);
                mc.player.connection.send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                mc.options.keySprint.setDown(false);
            } catch (Throwable ignored) {}
        }
        // ГАРАНТИРОВАННО через офф-хенд (левая рука). announce=false — ElytraSwap
        // сам печатает «Нет Фейерверков!»; успех не спамим.
        return this.testExecuteUse(Action.FIREWORK, false);
    }

    private boolean testExecuteUse(Action act) {
        return this.testExecuteUse(act, true);
    }

    /**
     * ЕДИНЫЙ use для всех предметов. Атомарный swap→use→swap-back через
     * офф-хенд (слот 40). Зелья матчатся по эффектам, остальное по имени.
     *
     * @param announce печатать ли сообщения в чат (успех/провал/кд).
     */
    private boolean testExecuteUse(Action act, boolean announce) {
        boolean isUsingItem = mc.player.isUsingItem();

        // 1) уже в main hand
        if (this.matchesAction(mc.player.getMainHandItem(), act)) {
            if (this.testCheckCooldown(mc.player.getMainHandItem(), act, announce)) return false;
            if (!isUsingItem) {
                this.testUseHand(InteractionHand.MAIN_HAND, this.testIsProjectile(act));
            }
            if (announce) this.msg(act.successText);
            return true;
        }
        // 2) уже в off hand
        if (this.matchesAction(mc.player.getOffhandItem(), act)) {
            if (this.testCheckCooldown(mc.player.getOffhandItem(), act, announce)) return false;
            this.testUseHand(InteractionHand.OFF_HAND, this.testIsProjectile(act));
            if (announce) this.msg(act.successText);
            return true;
        }

        // Ищем слот предмета (без кд). screenHandler: хотбар → 36+i, инвентарь → 9..35.
        int hotbarInv = this.findFirstNonCooldownSlot(act, 0, 8);
        int invSlot = this.findFirstNonCooldownSlot(act, 9, 35);

        if (hotbarInv == -1 && invSlot == -1) {
            int anySlot = this.findMatchingSlot(act, 0, 35);
            if (anySlot != -1) {
                this.testCheckCooldown(mc.player.getInventory().getItem(anySlot), act, announce);
            } else if (announce) {
                this.msg(act.failText);
            }
            return false;
        }

        // 3) игрок ТЯНЕТ предмет — офф-хенд свап, чтобы не сбить main hand.
        if (isUsingItem) {
            final int fScreenSlot = (hotbarInv != -1) ? (36 + hotbarInv) : invSlot;
            final boolean projectile = this.testIsProjectile(act);
            this.runInv(() -> {
                mc.gameMode.handleContainerInput(0, fScreenSlot, 40, ContainerInput.SWAP, mc.player);
                this.testUseHand(InteractionHand.OFF_HAND, projectile);
                mc.gameMode.handleContainerInput(0, fScreenSlot, 40, ContainerInput.SWAP, mc.player);
                mc.player.connection.send(new ServerboundContainerClosePacket(0));
            });
            if (announce) this.msg(act.successText);
            return true;
        }

        // 4) предмет в хотбаре — офф-хенд свап.
        if (hotbarInv != -1) {
            final int fHotScreen = 36 + hotbarInv;
            final boolean projectile = this.testIsProjectile(act);
            this.runInv(() -> {
                mc.gameMode.handleContainerInput(0, fHotScreen, 40, ContainerInput.SWAP, mc.player);
                this.testUseHand(InteractionHand.OFF_HAND, projectile);
                mc.gameMode.handleContainerInput(0, fHotScreen, 40, ContainerInput.SWAP, mc.player);
                mc.player.connection.send(new ServerboundContainerClosePacket(0));
            });
            if (announce) this.msg(act.successText);
            return true;
        }

        // 5) предмет в инвентаре — тот же офф-хенд свап.
        final int fInvSlot = invSlot;
        final boolean fProjectile = this.testIsProjectile(act);
        this.runInv(() -> {
            mc.gameMode.handleContainerInput(0, fInvSlot, 40, ContainerInput.SWAP, mc.player);
            this.testUseHand(InteractionHand.OFF_HAND, fProjectile);
            mc.gameMode.handleContainerInput(0, fInvSlot, 40, ContainerInput.SWAP, mc.player);
            mc.player.connection.send(new ServerboundContainerClosePacket(0));
        });
        if (announce) this.msg(act.successText);
        return true;
    }

    private boolean testCheckCooldown(ItemStack stack, Action act, boolean verbose) {
        if (stack == null || stack.isEmpty()) return false;
        if (mc.player.getCooldowns().isOnCooldown(stack)) {
            if (verbose) this.msg("У предмета " + act.cooldownName + " есть кд");
            return true;
        }
        return false;
    }

    private int findFirstNonCooldownSlot(Action act, int start, int end) {
        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (this.matchesAction(stack, act)
                    && !mc.player.getCooldowns().isOnCooldown(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int findMatchingSlot(Action action, int start, int end) {
        for (int slot = start; slot <= end; slot++) {
            if (this.matchesAction(mc.player.getInventory().getItem(slot), action)) {
                return slot;
            }
        }
        return -1;
    }

    private boolean matchesAction(ItemStack var1, Action var2) {
        if (var1 == null || var1.isEmpty() || var1.getItem() != var2.item) {
            return false;
        }
        // Зелья: матчим по набору эффектов — имя у них одинаковое ("Зелье"),
        // matching по подстроке давал ложные срабатывания (фейк-зелья).
        if (var2.effects != null && !var2.effects.isEmpty()) {
            return this.matchesPotionEffects(var1, var2.effects);
        }
        return var1.getHoverName().getString().toLowerCase(Locale.ROOT).contains(var2.query);
    }

    private Map<Holder<MobEffect>, Integer> getPotionEffects(ItemStack stack) {
        Map<Holder<MobEffect>, Integer> effects = new HashMap<>();
        if (stack == null || stack.isEmpty()) return effects;

        PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
        if (potionContents == null) return effects;

        for (MobEffectInstance effect : potionContents.customEffects()) {
            effects.put(effect.getEffect(), effect.getAmplifier());
        }
        return effects;
    }

    private boolean matchesPotionEffects(ItemStack stack, List<EffectRequirement> requirements) {
        if (requirements == null || requirements.isEmpty()) return false;
        if (stack.getItem() != Items.SPLASH_POTION && stack.getItem() != Items.LINGERING_POTION) return false;

        Map<Holder<MobEffect>, Integer> effects = getPotionEffects(stack);
        if (effects.isEmpty()) return false;

        int matchCount = 0;
        for (EffectRequirement req : requirements) {
            Integer amplifier = effects.get(req.effect);
            if (amplifier != null && amplifier >= req.minAmplifier) {
                matchCount++;
            }
        }
        return matchCount >= Math.min(2, requirements.size());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══ Заряд ветра — бросок строго вниз (порт WindHop.throwWindChargeDown) ══
    // ══════════════════════════════════════════════════════════════════════════

    private boolean throwWindChargeDown(boolean swing) {
        if (mc.level == null || mc.player == null || mc.gameMode == null) return false;
        if (mc.player.connection == null) return false;

        boolean isOffhand = mc.player.getOffhandItem().getItem() == Items.WIND_CHARGE;

        // 1) off-hand / хотбар — простое переключение слота.
        if (isOffhand || this.findHotbarWindCharge() != -1) {
            int hotbarSlot = isOffhand ? -1 : this.findHotbarWindCharge();
            int oldSlot = mc.player.getInventory().getSelectedSlot();
            if (!isOffhand && oldSlot != hotbarSlot) {
                mc.player.getInventory().setSelectedSlot(hotbarSlot);
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(hotbarSlot));
            }
            InteractionHand hand = isOffhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            this.sendInteractDown(hand, swing);
            if (!isOffhand && oldSlot != hotbarSlot) {
                mc.player.getInventory().setSelectedSlot(oldSlot);
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(oldSlot));
            }
            return true;
        }

        // 2) основной инвентарь (9..35) — временный swap в хотбар.
        int invSlot = -1;
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.WIND_CHARGE) {
                invSlot = i;
                break;
            }
        }
        if (invSlot == -1) return false;

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int tempSlot = this.findFreeHotbarSlot(oldSlot);
        final int fInv = invSlot;
        final int fTemp = tempSlot;
        mc.gameMode.handleContainerInput(0, fInv, fTemp, ContainerInput.SWAP, mc.player);
        mc.player.connection.send(new ServerboundContainerClosePacket(0));
        mc.player.getInventory().setSelectedSlot(fTemp);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(fTemp));

        this.sendInteractDown(InteractionHand.MAIN_HAND, swing);

        mc.player.connection.send(new ServerboundSetCarriedItemPacket(oldSlot));
        mc.player.getInventory().setSelectedSlot(oldSlot);
        mc.gameMode.handleContainerInput(0, fInv, fTemp, ContainerInput.SWAP, mc.player);
        mc.player.connection.send(new ServerboundContainerClosePacket(0));
        return true;
    }

    private void sendInteractDown(InteractionHand hand, boolean swing) {
        float yaw = mc.player.getYRot();
        float pitch = 90.0F;
        mc.player.connection.send(new ServerboundUseItemPacket(hand, 0, yaw, pitch));
        if (swing) {
            mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        }
    }

    private int findHotbarWindCharge() {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.WIND_CHARGE) return i;
        }
        return -1;
    }

    private int findFreeHotbarSlot(int selected) {
        for (int i = 0; i < 9; i++) {
            if (i != selected && mc.player.getInventory().getItem(i).isEmpty()) return i;
        }
        return selected;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══ Выстрел арбалета ═══════════════════════════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    private void fireCrossbow() {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;
        if (mc.player.connection == null) return;

        if (isLoadedCrossbow(mc.player.getMainHandItem())) {
            this.sendCrossbowShot(InteractionHand.MAIN_HAND);
            return;
        }
        if (isLoadedCrossbow(mc.player.getOffhandItem())) {
            this.sendCrossbowShot(InteractionHand.OFF_HAND);
            return;
        }

        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (isLoadedCrossbow(mc.player.getInventory().getItem(i))) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            if (this.hasAnyCrossbow()) {
                this.msg("Арбалет не заряжен!");
            } else {
                this.msg("Заряженный арбалет не найден!");
            }
            return;
        }

        int oldSlot = mc.player.getInventory().getSelectedSlot();
        mc.player.getInventory().setSelectedSlot(slot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));

        this.sendCrossbowShot(InteractionHand.MAIN_HAND);

        mc.player.getInventory().setSelectedSlot(oldSlot);
        mc.player.connection.send(new ServerboundSetCarriedItemPacket(oldSlot));
    }

    private void sendCrossbowShot(InteractionHand hand) {
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        mc.player.connection.send(new ServerboundUseItemPacket(hand, 0, yaw, pitch));
        mc.player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
    }

    private static boolean isLoadedCrossbow(ItemStack stack) {
        if (stack == null || stack.isEmpty() || stack.getItem() != Items.CROSSBOW) return false;
        ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
        return charged != null && !charged.isEmpty();
    }

    private boolean hasAnyCrossbow() {
        if (mc.player.getMainHandItem().getItem() == Items.CROSSBOW) return true;
        if (mc.player.getOffhandItem().getItem() == Items.CROSSBOW) return true;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).getItem() == Items.CROSSBOW) return true;
        }
        return false;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══ HUD: список забинженных предметов SpookyTime ═══════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Возвращает предметы, назначенные на спуки-бинды, для отрисовки на экране.
     * Идентификация предмета — тем же матчингом, что и использование (зелья по
     * эффектам, остальное по имени/предмету). Показываются только бинды с
     * назначенной клавишей и реально имеющимся предметом. Список НЕ пустой только
     * в режиме SpookyTime.
     */
    public List<HudBoundItem> getHudBoundItems() {
        List<HudBoundItem> out = new ArrayList<>();
        if (mc.player == null || !mode.is("SpookyTime")) return out;

        for (int i = 0; i < this.actionBinds.length; i++) {
            BindSettings b = this.actionBinds[i];
            if (b.key == -1) continue;
            Action a = this.actionMap[i];
            int slot = this.findMatchingSlot(a, 0, 35);
            if (slot == -1) continue;
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (stack.isEmpty()) continue;
            int count = this.countMatching(a);
            if (count == 0) continue;
            out.add(new HudBoundItem(stack.copy(), b.label(), count));
        }

        this.addSimpleHudBind(out, this.windChargeKey, Items.WIND_CHARGE);
        this.addSimpleHudBind(out, this.crossbowKey, Items.CROSSBOW);
        return out;
    }

    private int countMatching(Action a) {
        int count = 0;
        int size = mc.player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (this.matchesAction(s, a)) count += s.getCount();
        }
        return count;
    }

    private void addSimpleHudBind(List<HudBoundItem> out, BindSettings bind, Item item) {
        if (bind.key == -1 || mc.player == null) return;
        ItemStack found = ItemStack.EMPTY;
        int count = 0;
        int size = mc.player.getInventory().getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.getItem() == item) {
                if (found.isEmpty()) found = s;
                count += s.getCount();
            }
        }
        if (count == 0 || found.isEmpty()) return;
        out.add(new HudBoundItem(found.copy(), bind.label(), count));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // ═══ Enums & Records (SpookyTime) ══════════════════════════════════════════
    // ══════════════════════════════════════════════════════════════════════════

    /** Данные забинженного предмета для HUD: иконка, текст бинда, суммарное кол-во. */
    public record HudBoundItem(ItemStack stack, String bindKey, int count) {
    }

    private static class EffectRequirement {
        final Holder<MobEffect> effect;
        final int minAmplifier;

        EffectRequirement(Holder<MobEffect> effect, int minAmplifier) {
            this.effect = effect;
            this.minAmplifier = minAmplifier;
        }
    }

    private enum Action {
        DISORIENTATION("дезориентация", "дезориентации", Items.ENDER_EYE, "Использовал дезориентацию!", "Дезориентация не найдена!"),
        TRAP("трапка", "трапки", Items.NETHERITE_SCRAP, "Использовал трапку!", "Трапка не найдена!"),
        PLAST("пласт", "пласта", Items.DRIED_KELP, "Использовал пласт!", "Пласт не найден!"),
        DUST("явная пыль", "пыли", Items.SUGAR, "Использовал пыль!", "Пыль не найдена!"),
        FREEZE_SNOW("заморозка", "снега", Items.SNOWBALL, "Использовал снег!", "Снег не найден!"),
        AURA("божья", "ауры", Items.PHANTOM_MEMBRANE, "Использовал ауру!", "Аура не найдена!"),
        FIRE_TORNADO("огненный", "смерча", Items.FIRE_CHARGE, "Использовал огненный смерч!", "Огненный смерч не найден!"),
        ENDER_PEARL("", "эндер перла", Items.ENDER_PEARL, "Бросил эндер перл!", "Эндер перл не найден!"),
        JUMP_POTION("прыж", "зелья прыжка", Items.SPLASH_POTION, "Использовал зелье прыжка!", "Зелье прыжка не найдено!"),
        WEAKNESS_POTION("слабост", "зелья слабости", Items.SPLASH_POTION, "Использовал зелье слабости!", "Зелье слабости не найдено!"),
        HLOPUSHKA("хлопушка", "хлопушки", Items.SPLASH_POTION, "Использовал хлопушку!", "Хлопушка не найдена!",
                List.of(new EffectRequirement(MobEffects.SLOWNESS, 9), new EffectRequirement(MobEffects.SPEED, 4),
                        new EffectRequirement(MobEffects.BLINDNESS, 9), new EffectRequirement(MobEffects.GLOWING, 0))),
        HOLY_WATER("святая вода", "святой воды", Items.SPLASH_POTION, "Использовал святую воду!", "Святая вода не найдена!",
                List.of(new EffectRequirement(MobEffects.REGENERATION, 2), new EffectRequirement(MobEffects.INVISIBILITY, 1),
                        new EffectRequirement(MobEffects.INSTANT_HEALTH, 1))),
        GNEV("зелье гнева", "зелья гнева", Items.SPLASH_POTION, "Использовал зелье гнева!", "Зелье гнева не найдено!",
                List.of(new EffectRequirement(MobEffects.STRENGTH, 4), new EffectRequirement(MobEffects.SLOWNESS, 3))),
        PALADIN("зелье палладина", "зелья палладина", Items.SPLASH_POTION, "Использовал зелье палладина!", "Зелье палладина не найдено!",
                List.of(new EffectRequirement(MobEffects.RESISTANCE, 0), new EffectRequirement(MobEffects.FIRE_RESISTANCE, 0),
                        new EffectRequirement(MobEffects.INVISIBILITY, 0), new EffectRequirement(MobEffects.HEALTH_BOOST, 2))),
        ASSASSIN("зелье ассасина", "зелья ассасина", Items.SPLASH_POTION, "Использовал зелье ассасина!", "Зелье ассасина не найдено!",
                List.of(new EffectRequirement(MobEffects.STRENGTH, 3), new EffectRequirement(MobEffects.SPEED, 2),
                        new EffectRequirement(MobEffects.HASTE, 0), new EffectRequirement(MobEffects.INSTANT_DAMAGE, 1))),
        RADIATION("зелье радиации", "зелья радиации", Items.SPLASH_POTION, "Использовал зелье радиации!", "Зелье радиации не найдено!",
                List.of(new EffectRequirement(MobEffects.POISON, 1), new EffectRequirement(MobEffects.WITHER, 1),
                        new EffectRequirement(MobEffects.SLOWNESS, 2), new EffectRequirement(MobEffects.HUNGER, 4),
                        new EffectRequirement(MobEffects.GLOWING, 0))),
        SNOTVORNOE("снотворное", "снотворного", Items.SPLASH_POTION, "Использовал снотворное!", "Снотворное не найдено!",
                List.of(new EffectRequirement(MobEffects.WEAKNESS, 1), new EffectRequirement(MobEffects.MINING_FATIGUE, 1),
                        new EffectRequirement(MobEffects.WITHER, 2), new EffectRequirement(MobEffects.BLINDNESS, 0))),
        // Фейерверк — маршрутизация использования из ElytraSwap («elytra helper»).
        // query="" => матч по любому FIREWORK_ROCKET (имя не важно), как у ENDER_PEARL.
        FIREWORK("", "фейерверка", Items.FIREWORK_ROCKET, "Использовал фейерверк!", "Фейерверк не найден!");

        private final String query;
        private final String cooldownName;
        private final Item item;
        private final String successText;
        private final String failText;
        /** Если задан — матчинг идёт по эффектам зелья, а не по имени. */
        private final List<EffectRequirement> effects;

        Action(String query, String cooldownName, Item item, String successText, String failText) {
            this(query, cooldownName, item, successText, failText, null);
        }

        Action(String query, String cooldownName, Item item, String successText, String failText, List<EffectRequirement> effects) {
            this.query = query;
            this.cooldownName = cooldownName;
            this.item = item;
            this.successText = successText;
            this.failText = failText;
            this.effects = effects;
        }
    }

    @Override
    public void onDisable() {
        // ── Авто хорус + FunTime (исходный Assist) ──
        stopChorus();
        state = 0;
        pendingItem = null;
        for (int i = 0; i < wasPressed.length; i++) {
            wasPressed[i] = false;
        }

        MoveComponent.stop = false;
        MoveComponent.stopTicks = 0;

        if (hwState != HWState.IDLE && hwBypass.get()) {
            MoveComponent.stop = false;
            MoveComponent.stopTicks = 0;
        }
        resetHWState();

        // ── SpookyTime (исходный ServerHelper.onDisable) ──
        resetTestState();
        GrimBypass.ACTIVE = false;
        GrimBypass.reset();
        SUPPRESS_VANILLA_USE = false;

        super.onDisable();
    }
}
