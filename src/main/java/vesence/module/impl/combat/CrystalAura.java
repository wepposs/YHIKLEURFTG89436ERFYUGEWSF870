package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.impl.combat.auraComponent.GCDUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.TimerUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CrystalAura — умный авто-плейс/брейк кристаллов.
 *
 * <p>Что делает:
 * <ul>
 *   <li>считает реальный урон взрыва по ванильной формуле (дистанция × exposure
 *       по сетке лучей) для каждого врага и для себя;</li>
 *   <li>позицию кристалла/обсидиана выбирает по максимальному урону врагу при
 *       ограничении на урон себе, а не «ближайшую»;</li>
 *   <li>ротация — silent: углы игрока доводятся плавно (лимит °/тик, ease-out),
 *       снапятся на GCD-сетку мыши, камера остаётся у пользователя через
 *       {@link FreeLookUtil}; действие уходит на сервер только когда луч реально
 *       попадает в цель;</li>
 *   <li>порядок пакетов как требует античит: флаш ротации (sendChanges) →
 *       пакет слота → interact/useItemOn → swing → возврат слота;</li>
 *   <li>задержки с рандомным джиттером, чтобы тайминги не были периодическими.</li>
 * </ul>
 */
@IModule(
        name = "CrystalAura",
        description = "Автоматически размещает и ломает кристаллы",
        category = Category.COMBAT,
        bind = 0
)
@Environment(EnvType.CLIENT)
public class CrystalAura extends Module {

    /**
     * HvH — агрессивный режим: обход античита сохранён (GCD-ротация, порядок
     * пакетов, джиттер задержек), но напор выше — ротация вдвое быстрее и без
     * ease-out, задержки минимальные, порог урона врагу вдвое ниже, урон себе
     * ограничен только защитой от суицида.
     *
     * RW — максимальный обход: плавная ротация с ease-out, джиттер задержек,
     * лимит урона себе по настройке, одно действие за тик.
     */
    private final ModeSetting mode = new ModeSetting("Режим", "RW", "HvH", "RW");

    private final SliderSetting range = new SliderSetting(
            "Дистанция", 5.0F, 1.0F, 6.0F, 0.1F, false);

    private final SliderSetting placeDelay = new SliderSetting(
            "Задержка размещения", 50.0F, 0.0F, 1000.0F, 5.0F, false
    ).hidden(() -> mode.is("HvH"));

    private final SliderSetting breakDelay = new SliderSetting(
            "Задержка ломания", 25.0F, 0.0F, 1000.0F, 5.0F, false
    ).hidden(() -> mode.is("HvH"));

    private final SliderSetting minDamage = new SliderSetting(
            "Мин. урон врагу", 6.0F, 1.0F, 20.0F, 0.5F, false);

    private final SliderSetting maxSelfDamage = new SliderSetting(
            "Макс. урон себе", 8.0F, 1.0F, 36.0F, 0.5F, false
    ).hidden(() -> mode.is("HvH"));

    private final SliderSetting predict = new SliderSetting(
            "Предсказание (тиков)", 1.0F, 0.0F, 4.0F, 0.5F, false);

    private final SliderSetting rotationSpeed = new SliderSetting(
            "Скорость ротации", 45.0F, 5.0F, 180.0F, 5.0F, false);

    private final BooleanSetting instantBreak = new BooleanSetting(
            "Мгновенный разрыв", true);

    private final BooleanSetting autoSwitch = new BooleanSetting(
            "Автосвап кристалла", true);

    private final BooleanSetting rotate = new BooleanSetting(
            "Ротация", true);

    private final BooleanSetting autoPlace = new BooleanSetting(
            "Автопостановка обсидиана", true);

    private final BooleanSetting obsidianOnly = new BooleanSetting(
            "Только обсидиан", false);

    private final BooleanSetting onlyNearEnemy = new BooleanSetting(
            "Только рядом с врагом", true);

    private final SliderSetting enemyRange = new SliderSetting(
            "Радиус врага", 3.0F, 1.0F, 6.0F, 0.5F, false
    ).hidden(() -> !onlyNearEnemy.get());

    private final TimerUtil placeTimer = new TimerUtil();
    private final TimerUtil breakTimer = new TimerUtil();
    private final TimerUtil obsidianTimer = new TimerUtil();

    /** Позиция и время последнего поставленного нами кристалла — для instant break. */
    private Vec3 lastPlacePos;
    private long lastPlaceMs;

    /** Silent-ротация: true, если FreeLook сейчас под нашим управлением. */
    private boolean aimOwned = false;

    public CrystalAura() {
        addSettings(new Setting[]{
                mode,
                range,
                placeDelay,
                breakDelay,
                minDamage,
                maxSelfDamage,
                predict,
                rotationSpeed,
                instantBreak,
                autoSwitch,
                rotate,
                autoPlace,
                obsidianOnly,
                onlyNearEnemy,
                enemyRange
        });
    }

    private boolean hvh() {
        return mode.is("HvH");
    }

    /** В HvH порог урона вдвое ниже — бьём агрессивнее. */
    private double effectiveMinDamage() {
        float base = minDamage.get().floatValue();
        return hvh() ? Math.max(2.0, base * 0.5) : base;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Тик
    // ─────────────────────────────────────────────────────────────────────────

    @EventInit
    public void onTick(ClientTickEvent event) {
        if (!this.enable) {
            releaseAim();
            return;
        }
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            return;
        }
        // С открытым инвентарём действия не ванильны — античит это видит.
        if (mc.gui.screen() != null && !(mc.gui.screen() instanceof ChatScreen)) {
            return;
        }

        List<Player> enemies = collectEnemies();
        if (enemies.isEmpty()) {
            returnAim();
            return;
        }

        boolean acted = tryBreakCrystal(enemies);
        if (!acted) {
            acted = tryPlaceCrystal(enemies);
        }
        if (!acted) {
            placeObsidianIfNeeded(enemies);
        }

        // Действий в этом тике нет — плавно возвращаем камеру пользователю.
        if (!acted) {
            returnAim();
        }
    }

    private List<Player> collectEnemies() {
        List<Player> result = new ArrayList<>(3);
        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (player.isSpectator()) continue;
            if (player.distanceTo(mc.player) > 16.0F) continue;
            result.add(player);
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Урон взрыва (ванильная формула кристалла: сила 6, радиус поражения 12)
    // ─────────────────────────────────────────────────────────────────────────

    private double crystalDamage(Vec3 source, LivingEntity target) {
        if (target == null || !target.isAlive()) return 0.0;

        double ticks = predict.get().floatValue();
        Vec3 pos = ticks > 0.0
                ? target.position().add(target.getDeltaMovement().scale(ticks))
                : target.position();
        double dist = pos.distanceTo(source);
        if (dist > 12.0) return 0.0;

        AABB box = target.getBoundingBox()
                .move(pos.x - target.getX(), pos.y - target.getY(), pos.z - target.getZ());
        float exposure = exposure(source, box, target);
        double impact = (1.0 - dist / 12.0) * exposure;
        if (impact <= 0.0) return 0.0;
        return (impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0;
    }

    /** Доля лучей из сетки по AABB цели, доходящих до точки взрыва (как в ванили). */
    private float exposure(Vec3 source, AABB box, Entity entity) {
        double xStep = 1.0 / (box.getXsize() * 2.0 + 1.0);
        double yStep = 1.0 / (box.getYsize() * 2.0 + 1.0);
        double zStep = 1.0 / (box.getZsize() * 2.0 + 1.0);
        if (xStep <= 0.0 || yStep <= 0.0 || zStep <= 0.0) return 0.0F;

        int total = 0;
        int exposed = 0;
        for (double x = 0.0; x <= 1.0; x += xStep) {
            for (double y = 0.0; y <= 1.0; y += yStep) {
                for (double z = 0.0; z <= 1.0; z += zStep) {
                    Vec3 sample = new Vec3(
                            box.minX + (box.maxX - box.minX) * x,
                            box.minY + (box.maxY - box.minY) * y,
                            box.minZ + (box.maxZ - box.minZ) * z);
                    HitResult hit = mc.level.clip(new ClipContext(
                            sample, source, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
                    if (hit.getType() == HitResult.Type.MISS) exposed++;
                    total++;
                }
            }
        }
        return total == 0 ? 0.0F : (float) exposed / total;
    }

    /** Лучший урон по любому врагу и урон себе для точки взрыва. */
    private double bestEnemyDamage(Vec3 source, List<Player> enemies) {
        double best = 0.0;
        for (Player enemy : enemies) {
            double dmg = crystalDamage(source, enemy);
            if (dmg > best) best = dmg;
        }
        return best;
    }

    private double selfDamage(Vec3 source) {
        return crystalDamage(source, mc.player);
    }

    private boolean selfSafe(double dmg) {
        // В HvH слайдер самоурона игнорируется — остаётся только защита от суицида.
        if (!hvh() && dmg > maxSelfDamage.get().floatValue()) return false;
        return dmg < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1.0F;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Ломание кристаллов
    // ─────────────────────────────────────────────────────────────────────────

    private boolean tryBreakCrystal(List<Player> enemies) {
        EndCrystal best = null;
        double bestScore = 0.0;
        double rangeSq = range.get().floatValue() * range.get().floatValue();
        Vec3 eye = mc.player.getEyePosition();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (crystal.distanceToSqr(mc.player) > rangeSq) continue;
            if (!hasLineOfSight(eye, crystal.position().add(0.0, 1.0, 0.0))) continue;

            Vec3 source = crystal.position();
            double dmg = bestEnemyDamage(source, enemies);
            double self = selfDamage(source);
            if (dmg < 1.0 || !selfSafe(self)) continue;
            if (dmg < effectiveMinDamage() && !isOwnFreshCrystal(crystal)) continue;

            double score = dmg - self * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = crystal;
            }
        }

        if (best == null) {
            return false;
        }

        // Мгновенный разрыв своих кристаллов обходит задержку (но не чаще 30мс).
        boolean own = isOwnFreshCrystal(best);
        long delay = hvh()
                ? 10L + ThreadLocalRandom.current().nextLong(0L, 16L)
                : breakDelay.get().longValue() + ThreadLocalRandom.current().nextLong(0L, 26L);
        if (own && instantBreak.get()) {
            if (System.currentTimeMillis() - lastPlaceMs < 30L) return true;
        } else if (!breakTimer.isReached(delay)) {
            return true; // цель есть, ждём кулдаун — Platz/брейк в этом тике не нужны
        }

        Vec3 aimPoint = best.position().add(0.0, 1.0, 0.0);
        if (!aimReady(aimPoint)) {
            return true;
        }

        // Порядок пакетов: ротация на сервер → атака → свинг.
        flushRotation();
        mc.gameMode.attack(mc.player, best);
        mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        breakTimer.reset();
        return true;
    }

    private boolean isOwnFreshCrystal(EndCrystal crystal) {
        return lastPlacePos != null
                && System.currentTimeMillis() - lastPlaceMs < 2000L
                && crystal.position().distanceToSqr(lastPlacePos) < 2.25;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Постановка кристалла
    // ─────────────────────────────────────────────────────────────────────────

    private boolean tryPlaceCrystal(List<Player> enemies) {
        long placeWait = hvh()
                ? 20L + ThreadLocalRandom.current().nextLong(0L, 16L)
                : placeDelay.get().longValue() + ThreadLocalRandom.current().nextLong(0L, 26L);
        if (!placeTimer.isReached(placeWait)) {
            return false;
        }

        BlockPos support = findBestCrystalSupport(enemies);
        if (support == null) {
            return false;
        }

        int crystalSlot = findSlot(Items.END_CRYSTAL);
        if (crystalSlot == -1) {
            return false;
        }
        if (!autoSwitch.get() && mc.player.getInventory().getSelectedSlot() != crystalSlot) {
            return false;
        }

        Vec3 hitVec = new Vec3(support.getX() + 0.5, support.getY() + 1.0, support.getZ() + 0.5);
        if (!aimReady(hitVec)) {
            return true;
        }

        int restore = mc.player.getInventory().getSelectedSlot();
        flushRotation();
        if (autoSwitch.get()) {
            sendSlot(crystalSlot);
        }
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, support, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        if (autoSwitch.get()) {
            sendSlot(restore);
        }

        lastPlacePos = hitVec;
        lastPlaceMs = System.currentTimeMillis();
        placeTimer.reset();
        return true;
    }

    /** obsidian/bedrock с двумя блоками воздуха сверху, максимум урона врагу. */
    private BlockPos findBestCrystalSupport(List<Player> enemies) {
        int r = range.get().intValue();
        double maxDist = range.get().floatValue();
        BlockPos origin = mc.player.blockPosition();
        Vec3 eye = mc.player.getEyePosition();

        BlockPos best = null;
        double bestScore = 0.0;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);

                    var block = mc.level.getBlockState(pos).getBlock();
                    boolean obsidian = block == Blocks.OBSIDIAN;
                    boolean bedrock = !obsidianOnly.get() && block == Blocks.BEDROCK;
                    if (!obsidian && !bedrock) continue;

                    if (!mc.level.getBlockState(pos.above()).isAir()) continue;
                    if (!mc.level.getBlockState(pos.above(2)).isAir()) continue;

                    Vec3 crystalPos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    if (eye.distanceTo(crystalPos) > maxDist) continue;
                    if (onlyNearEnemy.get() && !hasEnemyNear(pos, enemies)) continue;
                    if (!hasLineOfSight(eye, crystalPos)) continue;

                    // Не дублируем уже стоящий кристалл с таким же эффектом.
                    if (crystalAlreadyCovers(crystalPos, enemies)) continue;

                    double dmg = bestEnemyDamage(crystalPos, enemies);
                    if (dmg < effectiveMinDamage()) continue;
                    double self = selfDamage(crystalPos);
                    if (!selfSafe(self)) continue;

                    double score = dmg - self * 0.8;
                    if (score > bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean crystalAlreadyCovers(Vec3 crystalPos, List<Player> enemies) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal)) continue;
            if (crystal.position().distanceToSqr(crystalPos) > 6.25) continue;
            if (bestEnemyDamage(crystal.position(), enemies)
                    >= bestEnemyDamage(crystalPos, enemies) - 0.5) {
                return true;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Автопостановка обсидиана
    // ─────────────────────────────────────────────────────────────────────────

    private void placeObsidianIfNeeded(List<Player> enemies) {
        if (!autoPlace.get()) {
            return;
        }
        if (!obsidianTimer.isReached(hvh()
                ? 60L + ThreadLocalRandom.current().nextLong(0L, 31L)
                : 150L + ThreadLocalRandom.current().nextLong(0L, 60L))) {
            return;
        }

        BlockPos pos = findBestObsidianPlacement(enemies);
        if (pos == null) {
            return;
        }

        int obsidianSlot = findSlot(Items.OBSIDIAN);
        if (obsidianSlot == -1) {
            return;
        }
        if (!autoSwitch.get() && mc.player.getInventory().getSelectedSlot() != obsidianSlot) {
            return;
        }

        Vec3 hitVec = new Vec3(pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5);
        if (!aimReady(hitVec)) {
            return;
        }

        int restore = mc.player.getInventory().getSelectedSlot();
        flushRotation();
        if (autoSwitch.get()) {
            sendSlot(obsidianSlot);
        }
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos.below(), false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        if (autoSwitch.get()) {
            sendSlot(restore);
        }
        obsidianTimer.reset();
    }

    /** Обсидиан ставим туда, где кристалл на нём даст максимум урона. */
    private BlockPos findBestObsidianPlacement(List<Player> enemies) {
        int r = range.get().intValue();
        double maxDist = range.get().floatValue();
        BlockPos origin = mc.player.blockPosition();
        Vec3 eye = mc.player.getEyePosition();

        BlockPos best = null;
        double bestScore = 0.0;

        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = origin.offset(dx, dy, dz);

                    if (!mc.level.getBlockState(pos).isAir()) continue;
                    if (!mc.level.getBlockState(pos.below()).isSolidRender()) continue;
                    if (!mc.level.getBlockState(pos.above()).isAir()) continue;
                    if (!mc.level.getBlockState(pos.above(2)).isAir()) continue;

                    Vec3 crystalPos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
                    if (eye.distanceTo(crystalPos) > maxDist + 1.0) continue;
                    if (onlyNearEnemy.get() && !hasEnemyNear(pos, enemies)) continue;

                    double dmg = bestEnemyDamage(crystalPos, enemies);
                    if (dmg < effectiveMinDamage()) continue;
                    double self = selfDamage(crystalPos);
                    if (!selfSafe(self)) continue;

                    double score = dmg - self * 0.8;
                    if (score > bestScore) {
                        bestScore = score;
                        best = pos;
                    }
                }
            }
        }
        return best;
    }

    private boolean hasEnemyNear(BlockPos pos, List<Player> enemies) {
        Vec3 center = Vec3.atCenterOf(pos);
        float maxDist = enemyRange.get().floatValue();
        for (Player player : enemies) {
            if (player.getEyePosition().distanceTo(center) <= maxDist) return true;
        }
        return false;
    }

    private boolean hasLineOfSight(Vec3 from, Vec3 to) {
        HitResult hit = mc.level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Silent-ротация (плавная, GCD-снап, камера остаётся у игрока)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Доводит углы игрока до точки и возвращает true, когда можно действовать.
     * Если ротация выключена — сразу true. Если углами владеет KillAura
     * (URotations), не мешаем и ждём свой тик.
     */
    private boolean aimReady(Vec3 point) {
        if (!rotate.get()) {
            return true;
        }
        if (URotations.isRotating()) {
            return false;
        }

        if (!aimOwned) {
            if (!FreeLookUtil.active) {
                // Камера остаётся у пользователя: фиксируем её текущие углы.
                FreeLookUtil.freeYaw = mc.player.getYRot();
                FreeLookUtil.freePitch = mc.player.getXRot();
            }
            FreeLookUtil.active = true;
            aimOwned = true;
        }

        Vec3 eye = mc.player.getEyePosition();
        Vec3 dir = point.subtract(eye);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dir.y, Math.hypot(dir.x, dir.z)));

        float yawDelta = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float pitchDelta = targetPitch - mc.player.getXRot();
        float dist = (float) Math.hypot(yawDelta, pitchDelta);

        // HvH: допуск чуть шире и скорость вдвое выше, но шаги по-прежнему
        // снапсятся на GCD-сетку — ротация остаётся «мышиной».
        float tolerance = hvh() ? 4.5F : 3.0F;
        if (dist < tolerance) {
            // Финальный снап — маленький доворот, неотличим от мышиного.
            mc.player.setYRot(Mth.wrapDegrees(targetYaw));
            mc.player.setXRot(Mth.clamp(targetPitch, -90.0F, 90.0F));
            return true;
        }

        // Ease-out только в RW: вдали от цели — полная скорость, у цели — мельче шаги.
        float speed = rotationSpeed.get().floatValue() * (hvh() ? 2.0F : 1.0F);
        float cap = hvh()
                ? speed
                : speed * Math.max(0.35F, Math.min(1.0F, dist / 30.0F));
        float step = Math.min(cap, dist);
        float ratio = step / dist;

        float yawStep = GCDUtil.getSensitivity(yawDelta * ratio);
        float pitchStep = GCDUtil.getSensitivity(pitchDelta * ratio);
        if (yawStep == 0.0F && pitchStep == 0.0F) {
            float quantum = GCDUtil.getGCDValue();
            yawStep = Math.signum(yawDelta) * quantum;
            pitchStep = Math.signum(pitchDelta) * quantum;
        }
        // GCD-квант не должен проскакивать цель.
        if (Math.abs(yawStep) > Math.abs(yawDelta)) yawStep = yawDelta;
        if (Math.abs(pitchStep) > Math.abs(pitchDelta)) pitchStep = pitchDelta;

        mc.player.setYRot(Mth.wrapDegrees(mc.player.getYRot() + yawStep));
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + pitchStep, -90.0F, 90.0F));
        return false;
    }

    /** Плавный возврат углов к камере пользователя, затем отпускаем FreeLook. */
    private void returnAim() {
        if (!aimOwned || !rotate.get()) {
            releaseAim();
            return;
        }
        float yawDelta = Mth.wrapDegrees(FreeLookUtil.freeYaw - mc.player.getYRot());
        float pitchDelta = FreeLookUtil.freePitch - mc.player.getXRot();
        float dist = (float) Math.hypot(yawDelta, pitchDelta);
        if (dist < 2.0F) {
            releaseAim();
            return;
        }
        float speed = rotationSpeed.get().floatValue() * (hvh() ? 3.0F : 1.5F);
        float step = Math.min(speed, dist);
        float ratio = step / dist;
        mc.player.setYRot(Mth.wrapDegrees(mc.player.getYRot() + yawDelta * ratio));
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + pitchDelta * ratio, -90.0F, 90.0F));
    }

    private void releaseAim() {
        if (aimOwned) {
            // Отдаём камеру обратно только если KillAura не перехватил управление.
            if (!URotations.isRotating()) {
                FreeLookUtil.active = false;
            }
            aimOwned = false;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Пакеты
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Флаш ротации на сервер ДО пакета действия: в 26.3 ванильные пакеты
     * движения уходят из Minecraft.tick после тика игрока, без флаша сервер
     * увидел бы действие с углами прошлого тика.
     */
    private void flushRotation() {
        try {
            mc.player.sendChanges();
        } catch (Throwable ignored) {
        }
    }

    /** Смена слота с явным пакетом — иначе сервер её не увидит до конца тика. */
    private void sendSlot(int slot) {
        if (mc.player.getInventory().getSelectedSlot() == slot) {
            return;
        }
        mc.player.getInventory().setSelectedSlot(slot);
        if (mc.getConnection() != null) {
            mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
        }
    }

    private int findSlot(net.minecraft.world.item.Item item) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) return i;
        }
        return -1;
    }

    @Override
    public void onDisable() {
        releaseAim();
        placeTimer.reset();
        breakTimer.reset();
        obsidianTimer.reset();
        lastPlacePos = null;
        super.onDisable();
    }
}
