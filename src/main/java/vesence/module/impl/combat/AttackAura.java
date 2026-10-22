package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.event.player.EventInput;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.auraComponent.Attack;
import vesence.module.impl.combat.auraComponent.AuraUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.module.impl.combat.rotation.UFastRotation;
import vesence.module.impl.combat.rotation.UFunTestRotation;
import vesence.module.impl.combat.rotation.UHolyWorldRotation;
import vesence.module.impl.combat.rotation.ULonyGriefRotation;
import vesence.module.impl.combat.rotation.USpookyTimingRotation;
import vesence.module.impl.combat.rotation.UEnergyAimAssistRotation;
import vesence.module.impl.combat.rotation.UEnergyAresFTRotation;
import vesence.module.impl.combat.rotation.UEnergyMLRotation;
import vesence.module.impl.combat.rotation.UEnergySnapRotation;
import vesence.module.impl.combat.rotation.UEnergySpookyRotation;
import vesence.module.impl.combat.rotation.UVulcanRotation;
import vesence.module.impl.combat.rotation.UFunTimeRotation;
import vesence.module.impl.combat.rotation.UReallyWorldRotation;
import vesence.module.impl.combat.rotation.UCakeWorldRotation;
import vesence.module.impl.combat.rotation.UHolyWorldNewRotation;
import vesence.module.impl.combat.rotation.UMatrixRotation;
import vesence.module.impl.combat.rotation.UHvHRotation;
import vesence.module.impl.combat.rotation.UShardRotation;
import vesence.module.impl.combat.rotation.USlothRotation;
import vesence.module.impl.combat.rotation.URotationMath;
import vesence.module.impl.combat.neuro.*;
import vesence.module.impl.movement.AutoSprint;
import vesence.renderengine.utils.Mth;
import vesence.utils.player.InventoryActionUtil;
import vesence.utils.player.MoveUtil;
import vesence.utils.player.MovementManager;
import vesence.utils.render.math.animation.anim2.Interpolator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@IModule(
        name = "AttackAura",
        description = "Атакует сущностей в радиусе атаки",
        category = Category.COMBAT,
        // 26.3: дефолтный бинд — SDL-сканкод клавиши R (InputConstants.KEY_R = 21).
        // Старое значение 82 было GLFW-кодом 'R' и в SDL означало бы стрелку вверх.
        bind = 21
)
@Environment(EnvType.CLIENT)
public class AttackAura extends Module {

    // ── Основные настройки ────────────────────────────────────────────────────
    public static SliderSetting attackRange = new SliderSetting("Радиус атаки", 3.0F, 3.0F, 6.0F, 0.1F, false);
    public static SliderSetting preRange = new SliderSetting("Радиус обнаружения", 1.0F, 0.0F, 5.0F, 0.1F, false);
    public static ModeSetting rotationType = new ModeSetting("Ротация", "Spooky",
            "Spooky", "FTNew", "FunTest", "RWNew", "CakeWorld", "HWNew",
            "HolyNew", "MatrixNew", "HvH", "SlothNew", "Legit",
            "AresFT", "Neuro", "Fast",
            "LonyGrief", "Vulcan", "Shard", "ML", "Snap", "AimAssist");


    // ── Neuro ─────────────────────────────────────────────────────────────────
    public static MultiBooleanSetting neuroTrain = new MultiBooleanSetting(
            "Neuro Train",
            new BooleanSetting("Yaw Speed", true),
            new BooleanSetting("Pitch Speed", true),
            new BooleanSetting("Rotation Point", true),
            new BooleanSetting("Hit Point", true),
            new BooleanSetting("Behavior", true)
    ).hidden(() -> !rotationType.is("Neuro"));
    public static BooleanSetting neuroReset = new BooleanSetting("Neuro Reset", false).hidden(() -> !rotationType.is("Neuro"));
    public static SliderSetting neuroMaxSamples = new SliderSetting("Макс. образцов", 30000F, 1000F, 100000F, 1000F, false).hidden(() -> !rotationType.is("Neuro"));
    public static BooleanSetting neuroShowProgress = new BooleanSetting("Прогресс обучения", false).hidden(() -> !rotationType.is("Neuro"));

    public static BooleanSetting neuroTrainYawSpeed;
    public static BooleanSetting neuroTrainPitchSpeed;
    public static BooleanSetting neuroTrainRotationPoint;
    public static BooleanSetting neuroTrainHitPoint;
    public static BooleanSetting neuroTrainBehavior;

    // ── LegitSnap FOV ─────────────────────────────────────────────────────────
    public static BooleanSetting showFov = new BooleanSetting("Отображать Fov", true).hidden(() -> !rotationType.is("Snap"));
    public static SliderSetting fovValue = new SliderSetting("Fov", 40.0F, 10.0F, 90, 1.0F, true).hidden(() -> !rotationType.is("Snap"));
    public static ModeSetting snapSetting = new ModeSetting("Режим снапа", "Fast", "Fast", "Smooth", "Random").hidden(() -> !rotationType.is("Snap"));

    // ── Цели ──────────────────────────────────────────────────────────────────
    public static MultiBooleanSetting targets = new MultiBooleanSetting(
            "Цели",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Голые", true),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Боты", false)
    );

    // ── Проверки до удара ─────────────────────────────────────────────────────
    public static MultiBooleanSetting misc = new MultiBooleanSetting(
            "Проверки до удара",
            new BooleanSetting("Бить через блоки", true),
            new BooleanSetting("Только с оружием", false),
            new BooleanSetting("Не бить если ешь", true),
            new BooleanSetting("Не бить в гуишках", false),
            new BooleanSetting("Пробивать щит", false),
            new BooleanSetting("Отжимать щит", false)
    );

    // ── Пробитие блоков (Deluxe: режим «Бить через блоки») ───────────────────
    public static ModeSetting wallMode = new ModeSetting("Пробитие блоков", "Обычные",
            "Обычные", "Неполноценные", "Shard")
            .hidden(() -> !misc.get("Бить через блоки"));

    // ── Доп. настройки ────────────────────────────────────────────────────────
    public static MultiBooleanSetting extraSettings = new MultiBooleanSetting(
            "Доп.настройка",
            new BooleanSetting("Сброс спринта", true),
            new BooleanSetting("Только криты", false),
            new BooleanSetting("Умные криты", false),
            new BooleanSetting("Смэш булавой", true)
    );

    // ── Тип спринта (Deluxe: «Как гасить спринт перед ударом для крита») ─────
    public static ModeSetting sprintType = new ModeSetting("Тип спринта", "Легитный",
            "Легитный", "Быстрый")
            .hidden(() -> !extraSettings.get("Сброс спринта"));

    // ── Максимальный урон (Deluxe: свап предмета в левую руку на удар) ────────
    public static BooleanSetting maxDamage = new BooleanSetting("Максимальный урон", false);
    public static vesence.module.api.setting.impl.StringSetting swapItemName =
            new vesence.module.api.setting.impl.StringSetting("Предмет для свапа", "карател")
                    .hidden(() -> !maxDamage.get());

    // ── Коррекция движения ────────────────────────────────────────────────────
    public static ModeSetting moveCorrection = new ModeSetting("Коррекция движения", "Сфокусированная", "Сфокусированная", "Свободная");

    // ── Авто-сфера урона ──────────────────────────────────────────────────────
    public static BooleanSetting autoDamageOrb = new BooleanSetting("Авто-сфера урона", false);

    // ── Режим атаки / CPS ─────────────────────────────────────────────────────
    public static ModeSetting attackMode = new ModeSetting("Режим атаки", "1.9+", "1.9+", "1.8");
    public static SliderSetting cps = new SliderSetting("CPS", 15.0F, 5.0F, 20.0F, 1.0F, false)
            .hidden(() -> !attackMode.is("1.8"));

    // ── Ранний удар ───────────────────────────────────────────────────────────
    public static ModeSetting earlyHit = new ModeSetting("Ранний удар", "Адаптивный", "Всегда", "Адаптивный", "Отключен");

    // ── Точность ──────────────────────────────────────────────────────────────
    public static SliderSetting accuracy = new SliderSetting("Точность %", 100.0F, 1.0F, 100.0F, 1.0F, true);

    // ── Сортировка целей ──────────────────────────────────────────────────────
    public static ModeSetting targetSorting = new ModeSetting("Сортировка целей", "По дистанции", "Прицел", "Адаптивный", "По дистанции", "По здоровью");

    // ── FOV цели ──────────────────────────────────────────────────────────────
    public static SliderSetting targetFov = new SliderSetting("FOV цели", 180.0F, 0.0F, 360.0F, 1.0F, false);

    // ── TPS Sync ──────────────────────────────────────────────────────────────
    public static BooleanSetting tpsSync = new BooleanSetting("TPS Sync", false);

    // ── Легитное кд ударов ────────────────────────────────────────────────────
    public static BooleanSetting legitCooldown = new BooleanSetting("Легитное кд", false);

    // ── Внутреннее состояние легитного кд ────────────────────────────────────
    private long nextHitAllowedAt = 0L;
    private float legitCdThreshold = 0.94f;
    private long legitHitDelayEnd = 0L;

    // ── Умные криты: состояние ────────────────────────────────────────────────
    private int smartCritTargetId = Integer.MIN_VALUE;
    private long smartCritOffset = 0L;
    private long smartCritNextCalc = 0L;

    // ── Авто-сфера урона: состояние ───────────────────────────────────────────
    private ItemStack savedOffhand = ItemStack.EMPTY;
    private boolean orbSwapped = false;
    private int orbReturnTicks = 0;
    private int bestOrbSlot = -1;

    // ── Авто-смэш булавой: состояние (Deluxe: Class62 + Class55) ─────────────
    private static boolean maceSwapped = false;
    private static boolean maceReturned = false;
    private static int macePrevSlot = -1;
    private static int maceInvSlot = -1;
    private static LivingEntity maceTarget;
    /** Гейт тайминга смэша (Deluxe: Class55#field2133). */
    private static int smashGateCounter;
    /** Фриз движения до момента (Deluxe: Class62#field2305) — пауза 200мс при свапе на SpookyTime. */
    private static long freezeUntilMs = 0L;
    // Кэши предикторов падения (Deluxe: Class55 кэширует по age игрока)
    private static int fallCacheAge = -1;
    private static double fallCache;
    private static int landingCacheAge = -1;
    private static double landingCacheY;

    // ── Адаптивный ранний удар: состояние ────────────────────────────────────
    private final java.util.Map<Integer, Integer> hitDelayHistogram = new java.util.HashMap<>();
    private boolean adaptiveReady = false;
    private boolean calibratingAdaptive = false;
    private boolean heardHitSound = false;
    private int silenceTicks = 0;
    private int quietTicks = 0;
    private int calibrationTicks = 0;
    private int learnedHitDelay = 0;
    private int packetsThisWindow = 0;

    // ── 1.8 CPS таймер ───────────────────────────────────────────────────────
    private long lastAttackMillis = 0L;

    /**
     * Гейт удара текущего тика, посчитанный один раз в {@code onEvent} и
     * переиспользованный ротацией. Профили не угадывают окно атаки сами.
     */
    private boolean attackGate;

    // ── Look-up поведение ─────────────────────────────────────────────────────
    public long lastLookUpTime = 0;
    public long nextLookUpDelay = ThreadLocalRandom.current().nextLong(36500L, 39200L);
    public boolean isLookingUp = false;
    public long lookUpStartTime = 0;
    public int lookUpDuration = 0;

    // ── Публичные поля ────────────────────────────────────────────────────────
    public static LivingEntity target;
    public static int count;

    // ── Neuro режим ───────────────────────────────────────────────────────────
    private final AuraModeNeuro neuroMode = new AuraModeNeuro();

    // ── SpookyTime preset точек хитбокса ─────────────────────────────────────
    private static final double[][] SPOOKY_PRESET = {
            {0.0, 0.94, 0.0}, {0.0, 0.86, 0.0}, {0.0, 0.78, 0.0}, {0.0, 0.7, 0.0},
            {0.0, 0.62, 0.0}, {0.0, 0.54, 0.0}, {0.0, 0.46, 0.0},
            {0.24, 0.86, 0.0}, {-0.24, 0.86, 0.0}, {0.0, 0.86, 0.24}, {0.0, 0.86, -0.24},
            {0.24, 0.78, 0.24}, {0.24, 0.78, -0.24}, {-0.24, 0.78, 0.24}, {-0.24, 0.78, -0.24},
            {0.36, 0.7, 0.0}, {-0.36, 0.7, 0.0}, {0.0, 0.7, 0.36}, {0.0, 0.7, -0.36},
            {0.22, 0.62, 0.22}, {0.22, 0.62, -0.22}, {-0.22, 0.62, 0.22}, {-0.22, 0.62, -0.22}
    };

    // ── FunTest крит-рандом ───────────────────────────────────────────────────
    private static final float FUNTEST_CRIT_RANDOM_BONUS = 12.0F;

    // ─────────────────────────────────────────────────────────────────────────
    //  Конструктор
    // ─────────────────────────────────────────────────────────────────────────

    public AttackAura() {
        this.addSettings(new Setting[]{
                attackRange, preRange, rotationType,
                neuroTrain, neuroReset, neuroMaxSamples, neuroShowProgress,
                showFov, fovValue,
                snapSetting, targets, misc, wallMode, extraSettings, sprintType, moveCorrection,
                autoDamageOrb, maxDamage, swapItemName,
                attackMode, cps, earlyHit, accuracy, targetSorting, targetFov, tpsSync,
                legitCooldown
        });

        neuroTrainYawSpeed     = neuroTrain.settings.get(0);
        neuroTrainPitchSpeed   = neuroTrain.settings.get(1);
        neuroTrainRotationPoint = neuroTrain.settings.get(2);
        neuroTrainHitPoint     = neuroTrain.settings.get(3);
        neuroTrainBehavior     = neuroTrain.settings.get(4);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Render 2D — FOV-круг для LegitSnap
    // ─────────────────────────────────────────────────────────────────────────

    @EventInit
    public void onRender2D(EventScreen event) {
        if (!this.enable || !rotationType.is("Snap") || !showFov.get()) return;
        if (mc.player == null || mc.level == null) return;

        float sw = event.viewportWidth() / 2.0f;
        float sh = event.viewportHeight() / 2.0f;
        float fov = mc.options.fov().get().floatValue();
        float baseFovValue = fovValue.get().floatValue();
        float radius = (float) (Math.tan(Math.toRadians(baseFovValue / 2.0))
                / Math.tan(Math.toRadians(fov / 2.0))
                * (event.viewportHeight() / 2.0f));

        event.renderer().circleOutline(sw, sh, radius, 0.0f, 1.0f, 0x80FFFFFF, 1.0f);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ClientTickEvent
    // ─────────────────────────────────────────────────────────────────────────

    @EventInit
    public void onEvent(ClientTickEvent e) {
        Attack.tickTimers();
        if (!this.enable) {
            if (orbSwapped) returnOrb();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EventUpdate (основной тик ауры)
    // ─────────────────────────────────────────────────────────────────────────

    @EventInit
    public void onEvent(EventUpdate e) {
        if (!this.enable) return;

        if (!mc.player.isAlive()) {
            if (orbSwapped) returnOrb();
            this.toggle();
            return;
        }

        // Deluxe (field2305): фриз движения на время свапа булавы.
        if (System.currentTimeMillis() < freezeUntilMs) {
            mc.player.setDeltaMovement(Vec3.ZERO);
        }

        tickAdaptiveTiming();

        if (orbSwapped && orbReturnTicks > 0) {
            orbReturnTicks--;
            if (orbReturnTicks <= 0) returnOrb();
        }

        handleMaceSmash();

        if (target == null || !this.isValidTarget(target, elytraEngaged())) {
            if (orbSwapped) returnOrb();
            this.updateTarget();
        }

        if (target != null && mc.player != null && mc.level != null) {
            // Гейт удара считается ОДИН раз за тик и переиспользуется ротацией:
            // иначе профиль решает «не бьём», глушит джиттер, а удар в этот же
            // тик всё равно уходит — джиттер ложится ровно на тик удара.
            this.attackGate = this.resolveAttackGate() && !this.checkToAttack();
            URotationMath.setAttackWindow(this.attackGate);

            // Ротация строго ДО удара и в том же тике: пакет удара уходит с уже
            // применённым углом, а не с прошлотиковым.
            this.updateRotation();

            if (this.attackGate) {
                this.attackEntity();
            }
            AutoSprint.onPacketSprintKeyTick();
        } else {
            // Цели нет — окно атаки закрыто, иначе профиль возврата увидит
            // прошлотиковое true и поведёт себя как в бою.
            URotationMath.setAttackWindow(false);
            this.reset();
            this.updateReturn();
        }
    }

    /**
     * Возврат камеры к натуральному взгляду, когда цели нет.
     *
     * <p>До этого {@code smoothBack()} профилей не вызывался вообще: ТЗ-описанный
     * «50ms hold → 500ms eased-lerp» (SpookyTime) и плавные отводы остальных
     * профилей были мёртвым кодом, а работал только грубый RESET из
     * {@code URotations}. Здесь они получают управление.
     */
    private void updateReturn() {
        switch (rotationType.get()) {
            case "FTNew":      UFunTimeRotation.smoothBack();      break;
            case "RWNew":      UReallyWorldRotation.smoothBack();      break;
            case "CakeWorld":      UCakeWorldRotation.smoothBack();      break;
            case "HWNew":      UHolyWorldNewRotation.smoothBack();      break;
            case "MatrixNew":  UMatrixRotation.smoothBack();  break;
            case "HvH":       UHvHRotation.smoothBack();       break;
            case "SlothNew":   USlothRotation.smoothBack();   break;
            case "Shard":      UShardRotation.smoothBack();   break;
            case "FunTest":      UFunTestRotation.smoothBack(); break;
            default: break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Packet listener — адаптивный ранний удар
    // ─────────────────────────────────────────────────────────────────────────

    @EventInit
    public void onPacket(vesence.event.impl.EventPacket event) {
        if (!this.enable) return;

        // Deluxe (field2309): во время фриза свапа булавы позиционные пакеты
        // движения глушатся.
        if (event.isSend() && System.currentTimeMillis() < freezeUntilMs
                && (event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Pos
                    || event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.PosRot)) {
            event.cancel();
            return;
        }

        if (event.getType() == vesence.event.impl.EventPacket.Type.RECEIVE) {
            if (event.getPacket() instanceof net.minecraft.network.protocol.game.ClientboundSoundPacket) {
                heardHitSound = true;
            }
            if (event.getPacket() instanceof net.minecraft.network.protocol.game.ClientboundLoginPacket) {
                resetAdaptiveTiming();
            }
            return;
        }

        if (earlyHit.is("Адаптивный")
                && event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundMovePlayerPacket) {
            packetsThisWindow++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  EventInput — коррекция движения + сброс спринта
    // ─────────────────────────────────────────────────────────────────────────

    @EventInit
    public void onMoveKeys(EventInput event) {
        // Deluxe (field2308): во время фриза свапа булавы ввод обнуляется.
        if (System.currentTimeMillis() < freezeUntilMs) {
            event.setForward(0.0F);
            event.setStrafe(0.0F);
            event.setJump(false);
            event.setSneak(false);
            return;
        }

        if (this.enable && moveCorrection.is("Свободная") && mc.player != null
                && (event.getForward() != 0.0F || event.getStrafe() != 0.0F)) {
            float intentYaw = Mth.wrapDegrees(FreeLookUtil.freeYaw);
            float applyYaw  = Mth.wrapDegrees(mc.player.getYRot());
            if (Math.abs(Mth.wrapDegrees(intentYaw - applyYaw)) > 1.0F) {
                MoveUtil.fixMovement(event, intentYaw, applyYaw);
            }
        }

        if (extraSettings.get("Сброс спринта") && Attack.resetSprintTick(target, getRanges())
                && !mc.player.onGround()) {
            event.setStrafe(0.0F);
            event.setForward(0.0F);
            if (mc.options != null && mc.options.keySprint != null) {
                mc.options.keySprint.setDown(false);
            }
            if (mc.player != null) {
                mc.player.setSprinting(false);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Module lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void toggle() {
        super.toggle();
        this.reset();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if ("Neuro".equals(rotationType.get())) {
            neuroMode.onActivate();
        }
    }

    @Override
    public void onDisable() {
        if (orbSwapped) returnOrb();
        restoreMace();
        if ("Neuro".equals(rotationType.get())) {
            neuroMode.onDeactivate();
        }
        // Сбрасываем «тихую» ротацию и всегда отпускаем свободную камеру,
        // иначе после выключения ауры камера останется расцеплённой с телом.
        
        UReallyWorldRotation.state();
        ULonyGriefRotation.state();
        USpookyTimingRotation.state();
        UEnergySpookyRotation.state();
        UEnergyMLRotation.state();
        UEnergySnapRotation.state();
        UEnergyAimAssistRotation.state();
        UEnergyAresFTRotation.state();
        UFunTimeRotation.state();
        UCakeWorldRotation.state();
        UHolyWorldNewRotation.state();
        UMatrixRotation.state();
        UHvHRotation.state();
        USlothRotation.state();
        UShardRotation.state();
        // НЕ рвём ротацию в IDLE и НЕ выключаем FreeLook сразу: видимая камера
        // в этот момент на freeYaw, а реальный yaw смотрит на цель — мгновенный
        // сброс и даёт тот самый резкий доворот на противника. Переводим движок
        // в RESET: он плавно доведёт тело до свободной камеры, и stopRotation()
        // сам отпустит FreeLook, когда углы сойдутся (рывка не будет).
        if (URotations.currentTask == URotations.RotationTask.AIM) {
            URotations.currentTask = URotations.RotationTask.RESET;
        } else if (URotations.currentTask == URotations.RotationTask.IDLE) {
            FreeLookUtil.active = false;
        }
        URotations.currentPriority = 0;
        super.onDisable();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Вспомогательные: публичные утилиты
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Дальности {удар, доворот, трекинг} (Deluxe: Class62#m991). В крит-состоянии
     * булавы — расширенные пресеты; при падении с булавой в инвентаре высоко над
     * точкой приземления — +10 к трекингу.
     */
    public static float[] getRanges() {
        float attack = attackRange.get().floatValue();
        float pre = preRange.get().floatValue();
        if (maceCritState()) {
            return heightAboveLanding() > 2.0
                    ? new float[]{3.0F, 25.0F, 28.0F}
                    : new float[]{3.0F, 15.0F, 18.0F};
        }
        if (extraSettings.get("Смэш булавой") && mc.player != null
                && InventoryActionUtil.findItem(Items.MACE, false) != -1 && heightAboveLanding() > 2.0) {
            return new float[]{attack, pre + 10.0F, attack + pre + 10.0F};
        }
        return new float[]{attack, pre, attack + pre};
    }

    public static float randomLerp(float min, float max) {
        return Interpolator.lerp(max, min, new SecureRandom().nextFloat());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Легитное кд ударов
    // ─────────────────────────────────────────────────────────────────────────

    private boolean canHitLegit() {
        if (!legitCooldown.get()) return true;
        if (mc.player == null) return false;

        long now = System.currentTimeMillis();
        if (now < nextHitAllowedAt) return false;

        float cd = mc.player.getAttackStrengthScale(0.5f);
        if (cd < legitCdThreshold) {
            legitHitDelayEnd = 0L;
            return false;
        }

        if (legitHitDelayEnd == 0L) {
            legitHitDelayEnd = now + ThreadLocalRandom.current().nextLong(8L, 45L);
            return false;
        }

        return now >= legitHitDelayEnd;
    }

    private void onHitLegit() {
        if (!legitCooldown.get()) return;
        long now = System.currentTimeMillis();
        nextHitAllowedAt  = now + ThreadLocalRandom.current().nextLong(20L, 80L);
        legitCdThreshold  = ThreadLocalRandom.current().nextFloat(0.9f, 0.985f);
        legitHitDelayEnd  = 0L;
    }

    private void resetLegitCooldown() {
        nextHitAllowedAt  = 0L;
        legitCdThreshold  = ThreadLocalRandom.current().nextFloat(0.9f, 0.985f);
        legitHitDelayEnd  = 0L;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Умные криты
    // ─────────────────────────────────────────────────────────────────────────

    private long computeSmartCritOffset() {
        if (!extraSettings.get("Умные криты") || mc.player == null || target == null) return 0L;

        long now = System.currentTimeMillis();
        int id = target.getId();

        if (id != smartCritTargetId || now >= smartCritNextCalc
                || Attack.msCooldownPC01() < 0.75f) {
            smartCritTargetId = id;
            smartCritOffset   = buildSmartCritOffset();
            smartCritNextCalc = now + ThreadLocalRandom.current().nextLong(95L, 180L);
        }

        return smartCritOffset;
    }

    private long buildSmartCritOffset() {
        if (mc.player == null || target == null) return 0L;

        long low  = -35L;
        long high =  28L;

        boolean falling = mc.player.fallDistance > 0.0f || mc.player.getDeltaMovement().y < -0.0784;
        if (falling) { low -= 18L; high -= 8L; }

        if (isOnSolidBlock()) { low -= 22L; high -= 6L; }
        else if (mc.player.onGround()) { low += 8L; high += 18L; }

        if (target.hurtTime > 0) {
            low  = Math.max(low, 4L);
            high += 34L;
        }

        float range = attackRange.get().floatValue();
        double gap = range - AuraUtil.getStrictDistance(target);
        if (gap < 0.35) { low += 10L; high += 22L; }
        else if (gap > 1.0) { low -= 8L; }

        if (high < low) high = low;
        return ThreadLocalRandom.current().nextLong(low, high + 1L);
    }

    private boolean isOnSolidBlock() {
        if (mc.player == null || mc.level == null) return false;
        net.minecraft.core.BlockPos below = net.minecraft.core.BlockPos.containing(
                mc.player.getX(),
                mc.player.getBoundingBox().minY - 0.05,
                mc.player.getZ());
        net.minecraft.core.BlockPos above = net.minecraft.core.BlockPos.containing(
                mc.player.getX(),
                mc.player.getBoundingBox().maxY + 0.2,
                mc.player.getZ());
        return !mc.level.getBlockState(below).getCollisionShape(mc.level, below).isEmpty()
                && !mc.level.getBlockState(above).getCollisionShape(mc.level, above).isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  FunTest: крит-рандом
    // ─────────────────────────────────────────────────────────────────────────

    private boolean funTestShouldCrit() {
        if (mc.player == null) return true;
        boolean critWindow = !mc.player.onGround()
                && mc.player.getDeltaMovement().y < 0.0
                && mc.player.fallDistance > 0.0F;
        if (critWindow) {
            float chance = FUNTEST_CRIT_RANDOM_BONUS;
            if (chance > 0.0F && ThreadLocalRandom.current().nextFloat() * 100.0F < chance) {
                return false;
            }
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Адаптивный тайминг
    // ─────────────────────────────────────────────────────────────────────────

    private void tickAdaptiveTiming() {
        if (!earlyHit.is("Адаптивный")) { resetAdaptiveTiming(); return; }
        if (!adaptiveReady) {
            if (heardHitSound) {
                quietTicks = 0;
                if (++silenceTicks >= 50) {
                    adaptiveReady = true;
                    calibratingAdaptive = true;
                    calibrationTicks = 0;
                    learnedHitDelay = 0;
                    hitDelayHistogram.clear();
                }
            } else if (++quietTicks >= 4) {
                silenceTicks = 0;
            }
        } else if (calibratingAdaptive) {
            hitDelayHistogram.merge(packetsThisWindow, 1, Integer::sum);
            if (++calibrationTicks >= 40) {
                learnedHitDelay = mostCommonHitDelay();
                calibratingAdaptive = false;
            }
        }
        packetsThisWindow = 0;
        heardHitSound = false;
    }

    private void resetAdaptiveTiming() {
        adaptiveReady = false;
        calibratingAdaptive = false;
        heardHitSound = false;
        silenceTicks = 0;
        quietTicks = 0;
        calibrationTicks = 0;
        learnedHitDelay = 0;
        packetsThisWindow = 0;
        hitDelayHistogram.clear();
    }

    private int mostCommonHitDelay() {
        int delay = 0, best = -1;
        for (java.util.Map.Entry<Integer, Integer> e : hitDelayHistogram.entrySet()) {
            int p = e.getKey(), c = e.getValue();
            if (c >= best && (c != best || p > delay)) { delay = p; best = c; }
        }
        return delay;
    }

    private long resolveEarlyHitOffset() {
        if (attackMode.is("1.8"))       return 0L;
        if (earlyHit.is("Отключен"))    return 0L;
        if (earlyHit.is("Всегда"))      return -80L;
        if (adaptiveReady) return learnedHitDelay > 0 ? Math.max(-80L, -learnedHitDelay * 10L) : -60L;
        return 0L;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Авто-сфера урона (offhand swap)
    // ─────────────────────────────────────────────────────────────────────────

    private double getDamageValue(ItemStack stack) {
        if (stack.isEmpty()) return 0.0;
        final double[] damage = {0.0};
        try {
            stack.forEachModifier(EquipmentSlot.OFFHAND, (attribute, modifier) -> {
                if (attribute == Attributes.ATTACK_DAMAGE) damage[0] += modifier.amount();
            });
        } catch (Exception ignored) {}
        return damage[0];
    }

    private int findBestDamageOrbSlot() {
        double currentDamage = getDamageValue(mc.player.getOffhandItem());
        int bestSlot = -1;
        double bestDamage = currentDamage;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            double dmg = getDamageValue(stack);
            if (dmg > bestDamage) { bestDamage = dmg; bestSlot = i; }
        }
        return bestSlot;
    }

    private void swapToDamageOrb(int slot) {
        if (slot < 0 || slot >= 36) return;
        savedOffhand = mc.player.getOffhandItem().copy();
        bestOrbSlot = slot;
        if (slot < 9) InventoryActionUtil.swapHotbarWithOffhandPacket(slot);
        else          InventoryActionUtil.swapWithOffhand(slot);
        orbSwapped = true;
    }

    private void returnOrb() {
        if (!orbSwapped) return;
        if (bestOrbSlot >= 0) {
            if (bestOrbSlot < 9) InventoryActionUtil.swapHotbarWithOffhandPacket(bestOrbSlot);
            else                  InventoryActionUtil.swapWithOffhand(bestOrbSlot);
        }
        orbSwapped = false;
        bestOrbSlot = -1;
        savedOffhand = ItemStack.EMPTY;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Атака
    // ─────────────────────────────────────────────────────────────────────────

    public void attackEntity() {
        if (target == null || mc.player == null || mc.level == null) {
            return;
        }

        if (autoDamageOrb.get() && !orbSwapped) {
            int orbSlot = findBestDamageOrbSlot();
            if (orbSlot >= 0) swapToDamageOrb(orbSlot);
        }

        if (!this.attackGate) {
            if (orbSwapped) returnOrb();
            return;
        }

        // Deluxe (m997): финальный гейт удара — луч взгляда реально на хитбоксе.
        if (!Attack.shouldAttack(target, true, true, true, resolveEarlyHitOffset(), getRanges())) {
            if (orbSwapped) returnOrb();
            return;
        }

        // Deluxe (m997): режим «Неполноценные» — бьём сквозь неполные блоки.
        if (wallMode.is("Неполноценные")
                && !Attack.hitThroughNonFull(target, attackRange.get().floatValue())) {
            if (orbSwapped) returnOrb();
            return;
        }

        // Deluxe (m997): режим «Shard» — ломаем полные блоки на луче до цели.
        if (wallMode.is("Shard") && mc.getConnection() != null
                && !Attack.clearLineOfSight(mc.player.getEyePosition(), target.getEyePosition())) {
            breakBlocksOnRay(target);
        }

        AutoSprint.onPacketAttackReset();

        boolean canPacket = !mc.player.hasEffect(MobEffects.BLINDNESS)
                && !mc.player.isFlyingVehicle()
                && !extraSettings.get("Легитный спринт");

        Runnable[] shieldBreak        = Attack.hitShieldBreakTaskForUse(target, misc.get("Пробивать щит"));
        Runnable[] shieldPressBypass  = Attack.resetShieldSilentTaskForUse(misc.get("Пробивать щит") || misc.get("Отжимать щит"));
        Runnable[] skipSilentSprint   = Attack.skipSilentSprintingTaskForUse(canPacket);

        // Deluxe (m997): «Максимальный урон» — на момент удара уводит названный
        // предмет в левую руку и сразу возвращает обратно.
        int swapSlot = maxDamage.get() && !swapItemName.get().isBlank()
                ? findInventorySlot(stack -> !stack.isEmpty() && stack.getHoverName().getString()
                        .toLowerCase().contains(swapItemName.get().toLowerCase().trim()))
                : -1;

        Runnable preHit = () -> {
            skipSilentSprint[0].run();
            shieldPressBypass[0].run();
            shieldBreak[0].run();
            if (swapSlot != -1) InventoryActionUtil.swapWithOffhand(swapSlot);
        };
        Runnable postHit = () -> {
            shieldBreak[1].run();
            shieldPressBypass[1].run();
            skipSilentSprint[1].run();
            if (swapSlot != -1) {
                mc.player.containerMenu.incrementStateId();
                InventoryActionUtil.swapWithOffhand(swapSlot);
            }
        };

        if (misc.get("Отжимать щит при ударе")
                && mc.player.getActiveItem().getItem().equals(Items.SHIELD)
                && mc.player.isUsingItem()) {
            mc.gameMode.releaseUsingItem(mc.player);
        }

        // Deluxe (m997): удар через m1183 с cpsBypass=false.
        if (Attack.useEntity(target, preHit, postHit, InteractionHand.MAIN_HAND, false)) {
            count++;
            lastAttackMillis = System.currentTimeMillis();
            onHitLegit();

            if ("Neuro".equals(rotationType.get()))      neuroMode.onAttack(target);
        }

        if (orbSwapped) {
            returnOrb();
        }
    }

    /** Class17#m633: поиск слота 0..35 по предикату (с конца инвентаря). */
    private static int findInventorySlot(java.util.function.Predicate<ItemStack> predicate) {
        if (mc.player == null) return -1;
        for (int i = 35; i >= 0; i--) {
            if (predicate.test(mc.player.getInventory().getItem(i))) return i;
        }
        return -1;
    }

    /**
     * Deluxe (m997, режим «Shard»): шагами 0.1 вдоль луча взгляда шлёт
     * START/STOP_DESTROY_BLOCK в каждый полный блок до точки попадания по цели.
     */
    private void breakBlocksOnRay(LivingEntity target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = Attack.directionVec(mc.player.getYRot(), mc.player.getXRot()).normalize();
        double hitDist = target.getBoundingBox()
                .clip(eye, eye.add(dir.scale(Math.max(mc.player.distanceTo(target),
                        attackRange.get().floatValue() + 2.0F))))
                .map(eye::distanceTo)
                .orElseGet(() -> boxDistance(target));

        List<net.minecraft.core.BlockPos> visited = new ArrayList<>();
        for (double d = 0.0; d <= hitDist; d += 0.1) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(eye.add(dir.scale(d)));
            if (visited.contains(pos)) continue;
            visited.add(pos);
            if (!mc.level.getBlockState(pos).isAir()
                    && !mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()) {
                net.minecraft.world.phys.BlockHitResult hitResult = mc.level.clip(new ClipContext(
                        eye.add(dir.scale(d - 0.2)), eye.add(dir.scale(d + 0.2)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
                net.minecraft.core.Direction side = hitResult.getType() == HitResult.Type.BLOCK
                        ? hitResult.getDirection() : net.minecraft.core.Direction.UP;
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, side));
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                        net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, side));
            }
        }
    }

    /** Class359#m3926: дистанция от глаз до хитбокса. */
    private static double boxDistance(LivingEntity entity) {
        Vec3 eye = mc.player.getEyePosition();
        AABB box = entity.getBoundingBox();
        double dx = Math.max(Math.max(box.minX - eye.x, eye.x - box.maxX), 0.0);
        double dy = Math.max(Math.max(box.minY - eye.y, eye.y - box.maxY), 0.0);
        double dz = Math.max(Math.max(box.minZ - eye.z, eye.z - box.maxZ), 0.0);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Ротация
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Единственное место, где решается «бьём в этом тике или нет».
     *
     * <p>Вызывается один раз за тик ДО ротации; результат кладётся в
     * {@link #attackGate} и отдаётся профилям через
     * {@link URotationMath#setAttackWindow(boolean)}. Считать это в двух местах
     * нельзя: проверки внутри {@code Attack.shouldAttack} читают таймеры, а
     * {@code accuracy} и 1.8-CPS вообще рандомные — две независимые оценки
     * рассинхронизируются и профиль загейчит джиттер не в тот тик.
     */
    private boolean resolveAttackGate() {
        if (target == null || mc.player == null || mc.level == null) {
            return false;
        }

        if (AuraUtil.getStrictDistance(target) >= attackRange.get()) {
            gateReason("дистанция " + String.format("%.2f", AuraUtil.getStrictDistance(target))
                    + " >= " + attackRange.get());
            return false;
        }

        // Точность
        if (accuracy.get().floatValue() < 100.0F
                && ThreadLocalRandom.current().nextFloat() * 100.0F > accuracy.get().floatValue()) {
            gateReason("точность");
            return false;
        }

        // 1.8 CPS
        if (attackMode.is("1.8")) {
            long interval = Math.max(1L, (long) (1000.0 / Math.max(5.0, cps.get().doubleValue())));
            if (System.currentTimeMillis() - lastAttackMillis < interval) {
                gateReason("CPS 1.8");
                return false;
            }
        }

        // Легитное кд
        if (!canHitLegit()) {
            gateReason("легитный кулдаун, scale="
                    + String.format("%.2f", mc.player.getAttackStrengthScale(0.5f))
                    + " порог=" + String.format("%.2f", legitCdThreshold));
            return false;
        }

        // Shard: бьём только когда прицел уже сошёлся с целью.
        //
        // Проверка направления удара — самая дешёвая для античита: пакет атаки
        // несёт угол, и если он расходится с направлением на цель, ничего
        // доказывать не нужно. Поэтому удар ждёт доводки, а не наоборот:
        // профиль публикует остаток, и пока он больше допуска, гейт закрыт.
        if ("Shard".equals(rotationType.get()) && !UShardRotation.isSettled()) {
            gateReason("прицел не на цели, остаток "
                    + String.format("%.1f", UShardRotation.residual()) + "°");
            return false;
        }

        long offset = resolveEarlyHitOffset();
        float[] ranges = getRanges();

        boolean result;
        switch (rotationType.get()) {
            case "Neuro":
                result = Attack.shouldAttack(target, false, true, true, offset, ranges)
                        && neuroMode.isReadyToAttack(this, target);
                break;
            case "FunTest":
                result = Attack.shouldAttack(target, false, true, true, offset, ranges)
                        && funTestShouldCrit();
                break;
            case "1.8":
                result = AuraUtil.validDistance(target, ranges[0], true)
                        && (misc.get("Бить через блоки") || mc.player.hasLineOfSight(target));
                break;
            default:
                result = Attack.shouldAttack(target, false, true, true, offset, ranges);
                break;
        }
        if (!result) {
            gateReason("Attack.shouldAttack: scale="
                    + String.format("%.2f", mc.player.getAttackStrengthScale(0.5f))
                    + " тиковСУдара=" + Attack.ticksSinceHitForDebug()
                    + " режим=" + rotationType.get()
                    + " дистанция=" + String.format("%.2f", AuraUtil.getStrictDistance(target)));
        }
        return result;
    }

    /** Диагностика гейта удара: включается {@code VESENCE_AURADEBUG}. */
    private static final boolean AURA_DEBUG = System.getenv("VESENCE_AURADEBUG") != null;
    private static long lastGateLog;

    private static void gateReason(String reason) {
        if (!AURA_DEBUG) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastGateLog < 1000L) {
            return;
        }
        lastGateLog = now;
        System.out.println("[aura] не бью: " + reason);
    }

    private void updateRotation() {
        String mode = rotationType.get();

        // Окно атаки для профилей — тот же гейт, по которому тик бьёт.
        boolean canAttack = this.attackGate;

        // Точка прицеливания: ближайшая ВИДИМАЯ точка хитбокса (ТЗ), а не центр
        // коробки. Считается ОДИН раз и передаётся всем профилям, принимающим
        // точку; профили без параметра берут её сами через URotationMath.
        Vec3 point = getAttackPoint(target, "UFunTimeRotation".equals(mode));

        // Rising-edge атаки: профили с флик-«тэллами» считают хиты только на
        // переходе false→true, иначе один тик удара посчитается многократно.
        if (canAttack) {
            URotations.markAttackTick();
        }

        switch (mode) {
            case "FTNew":
                UFunTimeRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "RWNew":
                UReallyWorldRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "CakeWorld":
                UCakeWorldRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "HWNew":
                UHolyWorldNewRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "MatrixNew":
                UMatrixRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "HvH":
                UHvHRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "SlothNew":
                USlothRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "Shard":
                // Профиль под Shard: ограниченный разгон, пауза после удара и
                // запрет удара до того, как прицел сошёлся с целью.
                UShardRotation.rotation(target, canAttack, attackRange.get().floatValue(),
                        preRange.get().floatValue());
                break;
            case "Neuro":
                neuroMode.updateRotation(this, target);
                break;
            case "Fast":
                UFastRotation.rotation(target, canAttack);
                break;
            case "Legit":
                // Профиль «лёгкий/легит»: мелкий фиксированный шаг, джиттер
                // строго вне окна атаки.
                USlothRotation.rotation(target, canAttack, attackRange.get().floatValue(),
                        attackRange.get().floatValue() + preRange.get().floatValue());
                break;
            case "LonyGrief":
                ULonyGriefRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "Vulcan":
                UVulcanRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "FunTest":
                UFunTestRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "Spooky":
                UEnergySpookyRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                USpookyTimingRotation.tick();
                break;
            case "HolyNew":
                // Профиль HolyWorld: полная нейро-ротация. Раньше ветка звала
                // только чат-бота клан-чека и НЕ ставила ротацию вообще —
                // аура била с замороженной камерой.
                UHolyWorldRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "ML":
                UEnergyMLRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
            case "Snap":
                UEnergySnapRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "AimAssist":
                UEnergyAimAssistRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            case "AresFT":
                UEnergyAresFTRotation.rotation(target, attackRange.get().floatValue(), preRange.get().floatValue(), point);
                break;
            default:
                // ТЗ: мгновенный снап yaw (>30°) палится на 100%. Даже фолбэк
                // обязан идти через сглаженный профиль, а не выставлять углы напрямую.
                USlothRotation.rotation(target, canAttack, attackRange.get().floatValue(), preRange.get().floatValue());
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Точка атаки (SpookyTime / FunTime preset)
    // ─────────────────────────────────────────────────────────────────────────

    private Vec3 getAttackPoint(LivingEntity target, boolean isFunTime) {
        if (target == null) return null;

        ElytraTarget elytra = ElytraTarget.INSTANCE;
        if (elytra != null && elytra.enable && elytra.overtake.get()
                && elytra.aimRadius() > 0.0D
                && mc.player != null && mc.player.isFallFlying()
                && target.isFallFlying() && target instanceof Player) {
            return elytra.getPredictedPoint(target);
        }

        AABB box = target.getBoundingBox();
        Vec3 center = box.getCenter();
        Vec3 eyePos = mc.player.getEyePosition();
        float range = attackRange.get().floatValue() + preRange.get().floatValue();
        boolean ignoreWalls = misc.get("Бить через блоки");

        List<Vec3> candidates = new ArrayList<>();

        if (isFunTime) {
            double step = Math.max(box.getYsize() / 10.0, 0.1);
            for (double y = box.minY; y <= box.maxY + 1e-4; y += step) {
                Vec3 p = new Vec3(center.x, y, center.z);
                if (isValidPoint(p, range, ignoreWalls)) candidates.add(p);
            }
        } else {
            double halfX   = box.getXsize() * 0.5;
            double halfZ   = box.getZsize() * 0.5;
            double heightY = box.getYsize();
            for (double[] preset : SPOOKY_PRESET) {
                double y = box.minY + heightY * preset[1];
                Vec3 p = new Vec3(center.x + halfX * preset[0], y, center.z + halfZ * preset[2]);
                if (isValidPoint(p, range, ignoreWalls)) candidates.add(p);
                if (preset[1] != 0.0) {
                    Vec3 low = new Vec3(
                            center.x + halfX * preset[0],
                            box.minY + (1.0 - preset[1]) * 0.25 * heightY,
                            center.z + halfZ * preset[2]);
                    if (isValidPoint(low, range, ignoreWalls)) candidates.add(low);
                }
            }
        }

        if (candidates.isEmpty()) return target.getEyePosition();

        float currentYaw   = mc.player.getYRot();
        float currentPitch = mc.player.getXRot();

        Vec3 chosen = candidates.stream()
                .min(Comparator.comparingDouble(p -> {
                    Vec3 dir = p.subtract(eyePos);
                    float yaw   = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
                    float pitch = (float) Math.toDegrees(-Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));
                    float dy = net.minecraft.util.Mth.wrapDegrees(yaw - currentYaw);
                    float dp = pitch - currentPitch;
                    return Math.hypot(dy, dp);
                }))
                .orElse(target.getEyePosition());

        // Гистерезис: без него «минимальный угол» каждый тик выбирает соседнюю
        // точку пресета, и на быстрых профилях камера мотается между ними.
        return URotationMath.stabilizeAim(target.getId(), chosen, range, ignoreWalls);
    }

    private boolean isValidPoint(Vec3 point, float range, boolean ignoreWalls) {
        Vec3 eyePos = mc.player.getEyePosition();
        if (eyePos.distanceTo(point) > range) return false;
        if (!ignoreWalls) {
            return mc.level.clip(new ClipContext(
                    eyePos, point,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
            )).getType() == HitResult.Type.MISS;
        }
        return true;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Поиск цели
    // ─────────────────────────────────────────────────────────────────────────

    private void updateTarget() {
        ElytraTarget elytra = ElytraTarget.INSTANCE;
        if (elytra != null && elytra.enable && elytra.overtake.get()
                && elytra.aimRadius() > 0.0D
                && mc.player != null && mc.player.isFallFlying()) {
            LivingEntity elytraTarget = findElytraTarget(elytra);
            if (elytraTarget != null) { target = elytraTarget; return; }
        }

        Vec3 eyePos = mc.player.getEyePosition();
        float camYaw   = mc.gameRenderer.mainCamera().yRot();
        float camPitch = mc.gameRenderer.mainCamera().xRot();
        Vec3 lookVec  = Vec3.directionFromRotation(camPitch, camYaw).normalize();
        double maxFov  = Math.max(1.0, targetFov.get().doubleValue()) * 0.5;

        List<LivingEntity> valid = new ArrayList<>();
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || !this.isValidTarget(living)) continue;

            Vec3 targetPos = new Vec3(
                    living.getX(),
                    living.getY() + living.getBbHeight() * 0.5,
                    living.getZ());
            Vec3 delta = targetPos.subtract(eyePos);
            if (delta.lengthSqr() < 1e-8) continue;

            double angle = Math.toDegrees(Math.acos(
                    net.minecraft.util.Mth.clamp(
                            lookVec.dot(delta.normalize()), -1.0, 1.0)));
            if (angle > maxFov) continue;

            valid.add(living);
        }

        if (valid.isEmpty()) {
            target = null;
            return;
        }

        // Deluxe (m1015): компараторы по режиму сортировки.
        Comparator<LivingEntity> comparator = switch (targetSorting.get()) {
            case "Адаптивный" -> Comparator.comparingDouble((LivingEntity e) -> -e.getArmorValue())
                    .thenComparingDouble(this::angleTo)
                    .thenComparingDouble(AttackAura::boxDistance)
                    .thenComparingDouble((LivingEntity e) -> e.getHealth() + e.getAbsorptionAmount());
            case "По здоровью" -> Comparator.comparingDouble((LivingEntity e) -> e.getHealth() + e.getAbsorptionAmount())
                    .thenComparingDouble(AttackAura::boxDistance);
            case "По дистанции" -> Comparator.comparingDouble(AttackAura::boxDistance)
                    .thenComparingDouble((LivingEntity e) -> e.getHealth() + e.getAbsorptionAmount());
            default -> Comparator.comparingDouble(this::angleTo)
                    .thenComparingDouble(AttackAura::boxDistance)
                    .thenComparingDouble((LivingEntity e) -> e.getHealth() + e.getAbsorptionAmount())
                    .thenComparingDouble((LivingEntity e) -> -e.getArmorValue());
        };
        valid.sort(comparator);
        target = valid.get(0);
    }

    /** Deluxe: Class62#m1004 — угол между взглядом игрока и направлением на глаза цели. */
    private double angleTo(LivingEntity entity) {
        Vec3 dir = entity.getEyePosition().subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));
        return Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw - mc.player.getYRot()),
                pitch - mc.player.getXRot());
    }

    private LivingEntity findElytraTarget(ElytraTarget elytra) {
        if (mc.player == null || mc.level == null) return null;
        double maxDist = elytra.aimRadius();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof Player player)) continue;
            if (player == mc.player) continue;
            if (!player.isAlive() || !player.isFallFlying()) continue;
            if (!elytra.isElytraTarget(player)) continue;
            if (!isValidTarget(player, true)) continue;
            double dist = mc.player.distanceTo(player);
            if (dist > maxDist) continue;
            if (dist < bestDist) { bestDist = dist; best = player; }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Дистанция наводки / ElytraTarget интеграция
    // ─────────────────────────────────────────────────────────────────────────

    public float aimRange() {
        ElytraTarget elytra = ElytraTarget.INSTANCE;
        if (elytra != null && elytra.enable && elytra.overtake.get()
                && mc.player != null && mc.player.isFallFlying()
                && elytra.aimDistance.get().doubleValue() > 0.0D) {
            return elytra.aimDistance.get().floatValue();
        }
        return auraDist();
    }

    private float auraDist() {
        return attackRange.get().floatValue() + preRange.get().floatValue();
    }

    private boolean elytraEngaged() {
        ElytraTarget elytra = ElytraTarget.INSTANCE;
        return elytra != null && elytra.enable && elytra.overtake.get()
                && elytra.aimRadius() > 0.0D
                && mc.player != null && mc.player.isFallFlying()
                && target != null && target.isFallFlying() && target instanceof Player;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Валидация цели
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isValidTarget(LivingEntity entity) {
        return isValidTarget(entity, false);
    }

    private boolean isValidTarget(LivingEntity entity, boolean ignoreDist) {
        if (entity instanceof vesence.module.impl.visuals.custompet.CustomPetEntity) return false;
        if (entity instanceof LocalPlayer) return false;
        // Deluxe (m998): свежезаспавненные сущности не трогаем.
        if (entity.tickCount < 1) return false;
        // Deluxe (m998): дистанция до цели — по трекинг-диапазону m991()[2].
        if (!ignoreDist) {
            float trackRange = getRanges().length > 2 ? getRanges()[2] : aimRange();
            if (mc.player.distanceToSqr(entity) > (double) trackRange * trackRange) return false;
        }
        if (!misc.get("Бить через блоки") && !ignoreDist && !mc.player.hasLineOfSight(entity)) return false;
        if (entity instanceof Player p) {
            if (NoFriendDamage.isFriend(p)) return false;
            // Deluxe (m1008): боты бьются только при включённой опции «Боты».
            if (isDeluxeBot(p)) return targets.get("Боты") && !p.isInvulnerable() && p.isAlive();
            if (AntiBot.isBot(p)) return false;
            if (!targets.get("Игроки")) return false;
            if (p.getArmorValue() == 0 && !targets.get("Голые")) return false;
            if (p.isCreative()) return false;
        }
        if ((entity instanceof Monster || entity instanceof Slime
                || entity instanceof Villager || entity instanceof Animal)
                && !targets.get("Мобы")) return false;
        return !entity.isInvulnerable() && entity.isAlive() && !(entity instanceof ArmorStand);
    }

    /**
     * Deluxe (m1008): бот — нет записи в табе или имя не совпадает с профилем
     * (не соответствует \\w{3,16}).
     */
    private static boolean isDeluxeBot(Player player) {
        if (mc.getConnection() == null) return false;
        net.minecraft.client.multiplayer.PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
        String name = player.getGameProfile().name();
        return entry == null || name == null || !name.matches("\\w{3,16}")
                || !name.equals(entry.getProfile().name());
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Проверки перед атакой
    // ─────────────────────────────────────────────────────────────────────────

    private boolean checkToAttack() {
        // Deluxe: «Не бить если ешь» глушит удар только когда активный предмет —
        // еда или зелье (щит/сферы под спам не попадают).
        ItemStack active = mc.player.getActiveItem();
        boolean eating = mc.player.isUsingItem() && !active.isEmpty()
                && (active.get(net.minecraft.core.component.DataComponents.FOOD) != null
                    || active.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS) != null);
        return eating && misc.get("Не бить если ешь")
                || mc.gui.screen() != null && misc.get("Не бить в гуишках")
                || !mc.player.getMainHandItem().is(ItemTags.SWORDS)
                && !mc.player.getMainHandItem().is(ItemTags.AXES)
                && !(mc.player.getMainHandItem().getItem() instanceof MaceItem)
                && misc.get("Только с оружием");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Авто-смэш булавой (Deluxe: Class62#m985 / m977 / m988)
    // ─────────────────────────────────────────────────────────────────────────

    private void handleMaceSmash() {
        if (!extraSettings.get("Смэш булавой") || mc.player == null || mc.level == null) {
            maceTarget = null;
            restoreMace();
            return;
        }
        // Deluxe: флаг «булавa уже возвращена» сбрасывается на земле/на взлёте.
        if (mc.player.onGround() || mc.player.getDeltaMovement().y >= 0.0) {
            maceReturned = false;
        }

        maceTarget = findSmashTarget();

        if (canMaceSmash()) {
            int hotbar = InventoryActionUtil.findItem(Items.MACE, true);
            int slot = hotbar != -1 ? hotbar : InventoryActionUtil.findItem(Items.MACE, false);
            if (slot != -1 && !maceSwapped && !maceReturned
                    && !mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(slot))) {
                maceSwapped = true;
                if (mc.player.getMainHandItem().is(Items.MACE)) {
                    return;
                }
                // Deluxe: фриз на 200мс при свапе, если под ногами пусто на 3
                // блока, но не на 8 (смэш безопасен) — только в режиме Spooky.
                if (rotationType.is("Spooky")
                        && mc.level.noCollision(mc.player, mc.player.getBoundingBox().expandTowards(0.0, -3.0, 0.0))
                        && !mc.level.noCollision(mc.player, mc.player.getBoundingBox().expandTowards(0.0, -8.0, 0.0))) {
                    freezeUntilMs = System.currentTimeMillis() + 200L;
                }
                macePrevSlot = mc.player.getInventory().getSelectedSlot();
                maceInvSlot = slot;
                if (slot > 8) InventoryActionUtil.swapWithHotbar(slot, macePrevSlot);
                else InventoryActionUtil.selectSlot(slot);
            }
        } else {
            restoreMace();
        }
    }

    /** Deluxe: Class62#m977 — можно ли сейчас смэшить (по физике падения). */
    private boolean canMaceSmash() {
        if (mc.player.isInWater() || mc.player.isInLava()) {
            return false;
        }
        if (maceSwapped && !mc.player.onGround()) {
            return true;
        }
        return !mc.player.onGround() && predictedMaxFall() >= 3.5;
    }

    /** Deluxe: Class62#m988 — вернуть прежний предмет после смэша. */
    private static void restoreMace() {
        if (!maceSwapped || mc.player == null) return;
        maceSwapped = false;
        maceReturned = true;
        if (maceInvSlot > 8) InventoryActionUtil.swapWithHotbar(maceInvSlot, macePrevSlot);
        else if (macePrevSlot >= 0 && macePrevSlot <= 8) InventoryActionUtil.selectSlot(macePrevSlot);
        macePrevSlot = -1;
        maceInvSlot = -1;
    }

    /** Deluxe: Class62#m1016 — ближайшая цель для смэша (до 6 блоков по горизонтали). */
    private LivingEntity findSmashTarget() {
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (living == mc.player || living.tickCount < 1 || living instanceof ArmorStand) continue;
            if (!living.isAlive() || living.isInvulnerable() || living.isBlocking()) continue;
            if (living instanceof Player p) {
                if (NoFriendDamage.isFriend(p) || p.isCreative() || AntiBot.isBot(p)) continue;
                if (!targets.get("Игроки")) continue;
            } else if (!targets.get("Мобы")) continue;
            if (Math.hypot(living.getX() - mc.player.getX(), living.getZ() - mc.player.getZ()) > 6.0) continue;
            double sq = mc.player.distanceToSqr(living);
            if (sq < bestSq) {
                bestSq = sq;
                best = living;
            }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Предикторы падения (Deluxe: Class55 — без полного симулятора Class154)
    // ─────────────────────────────────────────────────────────────────────────

    /** Deluxe: Class55#m929/#m941 — максимальный fallDistance за падение (до 60 тиков вперёд). */
    private static double predictedMaxFall() {
        if (mc.player == null || mc.level == null || mc.player.onGround()) return 0.0;
        if (mc.player.getAbilities().flying || mc.player.isFallFlying() || mc.player.isPassenger() || mc.player.onClimbable()) return 0.0;
        if (mc.player.hasEffect(MobEffects.LEVITATION) || mc.player.isInWater() || mc.player.isInLava()) return 0.0;
        if (mc.player.tickCount == fallCacheAge) return fallCache;

        double gravity = mc.player.getGravity();
        double ox = 0.0, oy = 0.0, oz = 0.0;
        AABB box = mc.player.getBoundingBox();
        Vec3 vel = mc.player.getDeltaMovement();
        double fall = mc.player.fallDistance;
        double maxFall = fall;
        for (int i = 0; i < 60; i++) {
            double mx = vel.x * 0.98, my = (vel.y - gravity) * 0.98, mz = vel.z * 0.98;
            Vec3 adjusted = Entity.collideBoundingBox(mc.player, new Vec3(mx, my, mz),
                    box.move(ox, oy, oz), mc.level, java.util.Collections.emptyList());
            if (adjusted.y < 0.0) fall += -adjusted.y;
            if (fall > maxFall) maxFall = fall;
            if (my < -1.0E-4 && adjusted.y > my + 1.0E-4) break;  // приземлились
            ox += adjusted.x; oy += adjusted.y; oz += adjusted.z;
            if (fluidInBox(box.move(ox, oy, oz))) break;
            vel = new Vec3(Math.abs(adjusted.x - mx) > 1.0E-4 ? 0.0 : mx,
                            Math.abs(adjusted.y - my) > 1.0E-4 ? 0.0 : my,
                            Math.abs(adjusted.z - mz) > 1.0E-4 ? 0.0 : mz);
            if (vel.lengthSqr() < 1.0E-12 && my >= -1.0E-4) break;
        }
        fallCache = maxFall;
        fallCacheAge = mc.player.tickCount;
        return maxFall;
    }

    /** Deluxe: Class55#m946/#m942 — Y предсказанной точки приземления (до 400 тиков). */
    private static double predictedLandingY() {
        if (mc.player == null || mc.level == null || mc.player.onGround()) return Double.NaN;
        if (mc.player.getAbilities().flying || mc.player.isFallFlying() || mc.player.isPassenger() || mc.player.onClimbable()) return Double.NaN;
        if (mc.player.hasEffect(MobEffects.LEVITATION) || mc.player.isInWater() || mc.player.isInLava()) return Double.NaN;
        if (mc.player.tickCount == landingCacheAge) return landingCacheY;

        double gravity = mc.player.getGravity();
        double ox = 0.0, oy = 0.0, oz = 0.0;
        AABB box = mc.player.getBoundingBox();
        Vec3 vel = mc.player.getDeltaMovement();
        double result = Double.NaN;
        for (int i = 0; i < 400; i++) {
            double mx = vel.x * 0.98, my = (vel.y - gravity) * 0.98, mz = vel.z * 0.98;
            Vec3 adjusted = Entity.collideBoundingBox(mc.player, new Vec3(mx, my, mz),
                    box.move(ox, oy, oz), mc.level, java.util.Collections.emptyList());
            if (my < -1.0E-4 && adjusted.y > my + 1.0E-4) {
                result = mc.player.getY() + oy + adjusted.y;
                break;
            }
            ox += adjusted.x; oy += adjusted.y; oz += adjusted.z;
            if (fluidInBox(box.move(ox, oy, oz))) {
                result = mc.player.getY() + oy;
                break;
            }
            vel = new Vec3(Math.abs(adjusted.x - mx) > 1.0E-4 ? 0.0 : mx,
                            Math.abs(adjusted.y - my) > 1.0E-4 ? 0.0 : my,
                            Math.abs(adjusted.z - mz) > 1.0E-4 ? 0.0 : mz);
            if (vel.lengthSqr() < 1.0E-12 && my >= -1.0E-4) break;
        }
        landingCacheY = result;
        landingCacheAge = mc.player.tickCount;
        return result;
    }

    /** Deluxe: Class55#m937 — высота над предсказанной точкой приземления (-1 если неизвестно). */
    private static double heightAboveLanding() {
        double landing = predictedLandingY();
        return Double.isNaN(landing) || mc.player == null ? -1.0 : mc.player.getY() - landing;
    }

    /** Deluxe: Class55#m933 — есть ли вода/лава в боксе. */
    private static boolean fluidInBox(AABB box) {
        for (net.minecraft.core.BlockPos pos : net.minecraft.core.BlockPos.betweenClosed(
                net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ),
                net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            net.minecraft.world.level.material.FluidState fluid = mc.level.getBlockState(pos).getFluidState();
            if (fluid.is(net.minecraft.tags.FluidTags.WATER) || fluid.is(net.minecraft.tags.FluidTags.LAVA)) {
                return true;
            }
        }
        return false;
    }

    /** Deluxe: Class55#m928 — приземлимся на следующем тике. */
    private static boolean willLandSoon() {
        if (mc.player == null || mc.player.fallDistance <= 1.0F) return false;
        double landing = predictedLandingY();
        if (Double.isNaN(landing)) return false;
        double gravity = mc.player.getGravity();
        double nextDrop = Math.max(0.0, -(mc.player.getDeltaMovement().y - gravity) * 0.98);
        return mc.player.getY() - landing <= nextDrop + 1.0E-4;
    }

    /** Deluxe: Class55#m940 — цель выше игрока (до 1.5) и падает. */
    private static boolean targetAboveFalling(LivingEntity target) {
        if (target == null || mc.player == null) return false;
        double dy = target.getY() - mc.player.getY();
        return dy > 0.0 && dy <= 1.5 && target.getY() - target.yOld < 0.0;
    }

    /** Deluxe: Class55#m931(1) — fallDistance на следующем тике. */
    private static float fallDistanceNextTick() {
        if (mc.player == null) return 0.0F;
        double gravity = mc.player.getGravity();
        double my = (mc.player.getDeltaMovement().y - gravity) * 0.98;
        return (float) (mc.player.fallDistance + (my < 0.0 ? -my : 0.0));
    }

    /** Deluxe: Class55#m945 — цель достижима со следующей предсказанной позиции. */
    private static boolean targetReachableNextTick(LivingEntity target) {
        if (target == null || mc.player == null || mc.level == null) return false;
        double gravity = mc.player.getGravity();
        Vec3 nextPos = mc.player.position().add(mc.player.getDeltaMovement().x,
                (mc.player.getDeltaMovement().y - gravity) * 0.98, mc.player.getDeltaMovement().z);
        Vec3 eye = nextPos.add(0.0, mc.player.getEyeHeight(), 0.0);
        Vec3 targetVel = new Vec3((target.getX() - target.xOld) * 2.0,
                (target.getY() - target.yOld) * 2.0, (target.getZ() - target.zOld) * 2.0);
        AABB box = target.getBoundingBox().move(targetVel);
        if (!Attack.clearLineOfSight(eye, box.getCenter())) return false;
        double range = mc.player.getMainHandItem().is(Items.MACE)
                ? 3.0 : Math.max(3.0, attackRange.get().floatValue());
        double dx = Math.max(Math.max(box.minX - eye.x, eye.x - box.maxX), 0.0);
        double dy = Math.max(Math.max(box.minY - eye.y, eye.y - box.maxY), 0.0);
        double dz = Math.max(Math.max(box.minZ - eye.z, eye.z - box.maxZ), 0.0);
        return dx * dx + dy * dy + dz * dz < range * range;
    }

    /** Deluxe: Class55#m944 — тайминг-гейт смэша (true = удар пока НЕ давать). */
    public static boolean smashTimingBlocked(LivingEntity target) {
        if (mc.player == null || !maceFallingState()) {
            smashGateCounter = 0;
            return false;
        }
        if (predictedMaxFall() <= 2.0) {
            smashGateCounter = 0;
            return false;
        }
        if (!willLandSoon() && !targetAboveFalling(target)) {
            if (fallDistanceNextTick() >= 1.0F && !targetReachableNextTick(target)) {
                smashGateCounter = 0;
                return false;
            }
            if (++smashGateCounter >= 10) {
                smashGateCounter = 0;
                return false;
            }
            return true;
        }
        smashGateCounter = 0;
        return false;
    }

    /** Deluxe: Class55#m935 — булава доступна и игрок падает. */
    public static boolean maceFallingState() {
        if (mc.player == null) return false;
        if (mc.player.getMainHandItem().is(Items.MACE)) return true;
        return mc.player.fallDistance >= 1.0F && extraSettings.get("Смэш булавой")
                && InventoryActionUtil.findItem(Items.MACE, false) != -1;
    }

    /** Deluxe: Class62#m1021 — булава в руке или доставлена аурой. */
    public static boolean maceReady() {
        return extraSettings.get("Смэш булавой") && maceSwapped
                || mc.player != null && mc.player.getMainHandItem().is(Items.MACE);
    }

    /** Deluxe: Class62#m1022 — крит-состояние булавы (в падении с булавой). */
    public static boolean maceCritState() {
        return maceReady() && mc.player != null && !mc.player.onGround();
    }

    /** Deluxe: Class62#m982 — смэш реально нанесёт доп. урон в этот тик. */
    public static boolean maceSmashActive() {
        return mc.player != null
                && (maceCritState() || maceFallingState() && !mc.player.onGround())
                && MaceItem.canSmashAttack(mc.player);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Сброс состояния
    // ─────────────────────────────────────────────────────────────────────────

    private void reset() {
        target = null;
        if (orbSwapped) returnOrb();
        restoreMace();
        maceReturned = false;
        maceTarget = null;
        smashGateCounter = 0;
        freezeUntilMs = 0L;
        fallCacheAge = -1;
        landingCacheAge = -1;
        if (mc.player != null) {
            isLookingUp    = false;
            lookUpStartTime = 0;
        }
        resetAdaptiveTiming();
        resetLegitCooldown();
        lastAttackMillis  = 0L;
        smartCritTargetId = Integer.MIN_VALUE;
        smartCritOffset   = 0L;
        smartCritNextCalc = 0L;
        MovementManager.getInstance().unlockMovement("AttackAura");
    }
}
