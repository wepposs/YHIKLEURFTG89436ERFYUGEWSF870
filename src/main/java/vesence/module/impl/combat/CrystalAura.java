package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import vesence.event.EventInit;
import vesence.event.lifecycle.ClientTickEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.other.TimerUtil;

/**
 * CrystalAura - автоматически размещает и ломает кристаллы
 * Портировано из Nightix 1.21
 */
@IModule(
        name = "CrystalAura",
        description = "Автоматически размещает и ломает кристаллы",
        category = Category.COMBAT,
        bind = 0
)
@Environment(EnvType.CLIENT)
public class CrystalAura extends Module {

    private final SliderSetting range = new SliderSetting(
            "Дистанция",
            5.0F,
            1.0F,
            6.0F,
            0.1F,
            false
    );

    private final SliderSetting placeDelay = new SliderSetting(
            "Задержка размещения",
            150.0F,
            0.0F,
            1000.0F,
            10.0F,
            false
    );

    private final SliderSetting breakDelay = new SliderSetting(
            "Задержка ломания",
            100.0F,
            0.0F,
            1000.0F,
            10.0F,
            false
    );

    private final BooleanSetting autoSwitch = new BooleanSetting(
            "Автосвап кристалла",
            true
    );

    private final BooleanSetting rotate = new BooleanSetting(
            "Ротация",
            true
    );

    private final BooleanSetting autoPlace = new BooleanSetting(
            "Автопостановка обсидиана",
            true
    );

    private final BooleanSetting obsidianOnly = new BooleanSetting(
            "Только обсидиан",
            false
    );

    private final BooleanSetting onlyNearEnemy = new BooleanSetting(
            "Только рядом с врагом",
            true
    );

    private final SliderSetting enemyRange = new SliderSetting(
            "Радиус врага",
            3.0F,
            1.0F,
            6.0F,
            0.5F,
            false
    ).hidden(() -> !onlyNearEnemy.get());

    private final TimerUtil placeTimer = new TimerUtil();
    private final TimerUtil breakTimer = new TimerUtil();
    private final TimerUtil obsidianTimer = new TimerUtil();
    private int prevSlot = -1;

    public CrystalAura() {
        addSettings(new Setting[]{
                range,
                placeDelay,
                breakDelay,
                autoSwitch,
                rotate,
                autoPlace,
                obsidianOnly,
                onlyNearEnemy,
                enemyRange
        });
    }

    @EventInit
    public void onTick(ClientTickEvent event) {
        if (mc.player == null || mc.world == null) {
            return;
        }

        breakCrystal();
        placeObsidianIfNeeded();
        placeCrystalIfNeeded();
    }

    private void placeObsidianIfNeeded() {
        if (!autoPlace.get()) {
            return;
        }

        if (!obsidianTimer.isReached(100L)) {
            return;
        }

        BlockPos pos = findBestObsidianPlacement();
        if (pos == null) {
            return;
        }

        int obsidianSlot = findObsidianSlot();
        if (obsidianSlot == -1) {
            return;
        }

        if (autoSwitch.get()) {
            selectSlot(obsidianSlot);
        } else if (mc.player.getInventory().getSelectedSlot() != obsidianSlot) {
            return;
        }

        if (rotate.get()) {
            aimAtBlock(pos.down());
        }

        placeObsidian(pos);
        obsidianTimer.reset();

        if (autoSwitch.get()) {
            restoreSlot();
        }
    }

    private void placeCrystalIfNeeded() {
        if (!placeTimer.isReached(placeDelay.get().longValue())) {
            return;
        }

        BlockPos pos = findNearestObsidian();
        if (pos == null) {
            return;
        }

        int crystalSlot = findCrystalSlot();
        if (crystalSlot == -1) {
            return;
        }

        if (autoSwitch.get()) {
            selectSlot(crystalSlot);
        } else if (mc.player.getInventory().getSelectedSlot() != crystalSlot) {
            return;
        }

        if (rotate.get()) {
            aimAtBlock(pos);
        }

        placeCrystal(pos);
        placeTimer.reset();

        if (autoSwitch.get()) {
            restoreSlot();
        }
    }

    private void breakCrystal() {
        if (!breakTimer.isReached(breakDelay.get().longValue())) {
            return;
        }

        EndCrystalEntity nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof EndCrystalEntity crystal)) continue;
            double dist = mc.player.getEyePos().distanceTo(crystal.getEyePos());
            if (dist > range.get().doubleValue()) continue;
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = crystal;
            }
        }

        if (nearest != null) {
            mc.interactionManager.attackEntity(mc.player, nearest);
            mc.player.swingHand(Hand.MAIN_HAND);
            breakTimer.reset();
        }
    }

    private BlockPos findNearestObsidian() {
        int r = range.get().intValue();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    var block = mc.world.getBlockState(pos).getBlock();
                    boolean isObsidian = block == Blocks.OBSIDIAN;
                    boolean isBedrock = !obsidianOnly.get() && block == Blocks.BEDROCK;
                    if (!isObsidian && !isBedrock) continue;

                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    if (!mc.world.getBlockState(pos.up(2)).isAir()) continue;

                    if (onlyNearEnemy.get() && !hasEnemyNear(pos)) continue;

                    double dist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    private boolean hasEnemyNear(BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        float maxDist = enemyRange.get().floatValue();
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.getEyePos().distanceTo(center) <= maxDist) return true;
        }
        return false;
    }

    private int findCrystalSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.END_CRYSTAL) return i;
        }
        return -1;
    }

    private int findObsidianSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.OBSIDIAN) return i;
        }
        return -1;
    }

    private BlockPos findBestObsidianPlacement() {
        int r = range.get().intValue();
        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = playerPos.add(x, y, z);

                    // Должен быть воздух
                    if (!mc.world.getBlockState(pos).isAir()) continue;

                    // Должна быть твердая поверхность снизу
                    if (!mc.world.getBlockState(pos.down()).isSolidBlock(mc.world, pos.down())) continue;

                    // Должно быть 2 блока воздуха сверху для кристалла
                    if (!mc.world.getBlockState(pos.up()).isAir()) continue;
                    if (!mc.world.getBlockState(pos.up(2)).isAir()) continue;

                    // Проверка врагов
                    if (onlyNearEnemy.get() && !hasEnemyNear(pos)) continue;

                    double dist = mc.player.getEyePos().distanceTo(Vec3d.ofCenter(pos));
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = pos;
                    }
                }
            }
        }
        return nearest;
    }

    private void selectSlot(int slot) {
        if (mc.player.getInventory().getSelectedSlot() != slot) {
            if (prevSlot == -1) {
                prevSlot = mc.player.getInventory().getSelectedSlot();
            }
            mc.player.getInventory().setSelectedSlot(slot);
        }
    }

    private void restoreSlot() {
        if (prevSlot != -1) {
            mc.player.getInventory().setSelectedSlot(prevSlot);
            prevSlot = -1;
        }
    }

    private void aimAtBlock(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mc.player.getX();
        double dy = pos.getY() + 1.0 - (mc.player.getY() + mc.player.getStandingEyeHeight());
        double dz = pos.getZ() + 0.5 - mc.player.getZ();
        double dxz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dxz)));
        
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private void placeCrystal(BlockPos pos) {
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos, false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void placeObsidian(BlockPos pos) {
        Vec3d hitVec = new Vec3d(pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, pos.down(), false);
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    @Override
    public void onDisable() {
        restoreSlot();
        placeTimer.reset();
        breakTimer.reset();
        obsidianTimer.reset();
        super.onDisable();
    }
}
