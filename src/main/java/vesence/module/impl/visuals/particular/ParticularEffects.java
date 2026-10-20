package vesence.module.impl.visuals.particular;

import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.block.state.properties.ChestType;
import vesence.Vesence;
import vesence.module.api.Module;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticularEffects {
    private static final float[] FIREFLY_DAILY_RANDOM = new float[]{0.0F, 0.0F, 0.0F, 0.33F, 0.66F, 1.0F};
    private static final float[] CAVE_DUST_COLOR = new float[]{128.0F / 255.0F, 128.0F / 255.0F, 128.0F / 255.0F};
    private static final ConcurrentHashMap<BlockPos, Integer> CASCADES = new ConcurrentHashMap<>();
    private static final Map<Block, LeafData> LEAVES = new HashMap<>();
    private static final Map<Integer, ArrayDeque<Double>> ENTITY_SPLASH_SPEEDS = new HashMap<>();
    private static final Map<Integer, Boolean> ENTITY_IN_WATER = new HashMap<>();
    private static final net.minecraft.core.BlockPos.MutableBlockPos CASCADE_NEIGHBOR = new net.minecraft.core.BlockPos.MutableBlockPos();
    private static final Map<String, ChestSoulState> CHEST_SOUL_STATES = new HashMap<>();
    private static final Map<String, ChestSoulState> ENDER_CHEST_SOUL_STATES = new HashMap<>();

    private static Identifier currentDimension;
    private static float fireflyFrequency = 1.0F;
    private static long lastWorldTime = Long.MIN_VALUE;

    static {
        LEAVES.put(Blocks.OAK_LEAVES, new LeafData(ParticularParticles.OAK_LEAF, null));
        LEAVES.put(Blocks.BIRCH_LEAVES, new LeafData(ParticularParticles.BIRCH_LEAF, new Color(FoliageColor.FOLIAGE_BIRCH)));
        LEAVES.put(Blocks.SPRUCE_LEAVES, new LeafData(ParticularParticles.SPRUCE_LEAF, new Color(FoliageColor.FOLIAGE_EVERGREEN)));
        LEAVES.put(Blocks.JUNGLE_LEAVES, new LeafData(ParticularParticles.JUNGLE_LEAF, null));
        LEAVES.put(Blocks.ACACIA_LEAVES, new LeafData(ParticularParticles.ACACIA_LEAF, null));
        LEAVES.put(Blocks.DARK_OAK_LEAVES, new LeafData(ParticularParticles.DARK_OAK_LEAF, null));
    }

    private ParticularEffects() {
    }

    public static boolean isEnabled() {
        try {
            Vesence vesence = Vesence.get;
            if (vesence == null || vesence.manager == null) {
                return false;
            }

            Module module = vesence.manager.get(Particular.class);
            return module != null && module.enable;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || mc.level == null) {
            return;
        }

        ClientLevel level = mc.level;
        Identifier dimension = level.dimension().identifier();
        if (currentDimension == null || !currentDimension.equals(dimension)) {
            currentDimension = dimension;
            CASCADES.clear();
            CHEST_SOUL_STATES.clear();
            ENDER_CHEST_SOUL_STATES.clear();
            ENTITY_SPLASH_SPEEDS.clear();
            ENTITY_IN_WATER.clear();
            lastWorldTime = Long.MIN_VALUE;
        }

        long dayTime = level.getOverworldClockTime();
        if (dayTime != lastWorldTime) {
            if (dayTime % 24000L == 12000) {
                fireflyFrequency = FIREFLY_DAILY_RANDOM[level.getRandom().nextInt(FIREFLY_DAILY_RANDOM.length)];
            }

            lastWorldTime = dayTime;
        }

        if (mc.player == null) {
            return;
        }

        emitRandomDisplayTicks(mc, level);
        emitCascades(mc, level);
        trackEntitySplashSpeeds(level);
        emitEntitySplashes(level);
        vesence.module.impl.visuals.particular.splashes.WaterCrownRenderer.tick(level);
    }

    /**
     * Воспроизводит ванильный цикл {@code ClientLevel.doRandomBlockDisplayTicks}: выбирает
     * случайные блоки вокруг игрока и вызывает соответствующие обработчики Particular
     * (листья, светлячки, пыль пещер, брызги водопадов, каскады). Так эффекты работают
     * без миксинов в ванильные классы блоков.
     */
    private static void emitRandomDisplayTicks(Minecraft mc, ClientLevel level) {
        if (mc.player == null) {
            return;
        }

        int px = Mth.floor(mc.player.getX());
        int py = Mth.floor(mc.player.getY());
        int pz = Mth.floor(mc.player.getZ());
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 667; ++i) {
            randomDisplayTickAt(level, mutable, px, py, pz, 16);
            randomDisplayTickAt(level, mutable, px, py, pz, 32);
        }
    }

    private static void randomDisplayTickAt(ClientLevel level, BlockPos.MutableBlockPos mutable, int px, int py, int pz, int radius) {
        int x = px + level.getRandom().nextInt(radius) - level.getRandom().nextInt(radius);
        int y = py + level.getRandom().nextInt(radius) - level.getRandom().nextInt(radius);
        int z = pz + level.getRandom().nextInt(radius) - level.getRandom().nextInt(radius);
        mutable.set(x, y, z);
        BlockState state = level.getBlockState(mutable);
        // Передаём mutable напрямую (обработчики читают позицию сразу или копируют её),
        // чтобы не аллоцировать BlockPos на каждую из ~1334 итераций за тик.
        onLeavesAnimateTick(state, level, mutable);
        onBlockAnimateTick(state, level, mutable);
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) {
            onWaterAnimateTick(level, mutable, fluidState);
            updateCascade(level, mutable, fluidState);
        }
    }

    /**
     * Отслеживает вход сущностей в воду и порождает всплеск. Заменяет ванильный хук
     * splash-звука/частиц: если сущность в этом тике пересекла поверхность воды сверху
     * вниз (была выше, стала в воде), спавним эмиттер всплеска.
     */
    private static void emitEntitySplashes(ClientLevel level) {
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            processEntitySplash(entity, seen);
        }

        // Явно обрабатываем клиентского игрока: в некоторых версиях он не входит в
        // level.entitiesForRendering(), а всплеск при собственном падении в воду важнее всего.
        processEntitySplash(Minecraft.getInstance().player, seen);

        ENTITY_IN_WATER.keySet().retainAll(seen);
    }

    private static void processEntitySplash(Entity entity, Set<Integer> seen) {
        if (entity == null) {
            return;
        }

        int id = entity.getId();
        if (!seen.add(id)) {
            return;
        }

        boolean inWater = entity.isInWater();
        Boolean previous = ENTITY_IN_WATER.put(id, inWater);
        boolean wasInWater = previous != null && previous;
        if (!wasInWater && inWater) {
            onEntityWaterSplash(entity);
        }
    }

    public static void clear() {
        CASCADES.clear();
        ENTITY_SPLASH_SPEEDS.clear();
        ENTITY_IN_WATER.clear();
        CHEST_SOUL_STATES.clear();
        ENDER_CHEST_SOUL_STATES.clear();
        currentDimension = null;
        fireflyFrequency = 1.0F;
        lastWorldTime = Long.MIN_VALUE;
        vesence.module.impl.visuals.particular.splashes.WaterCrownRenderer.clear();
    }

    public static boolean isLeafSpawnRippleEnabled() {
        return true;
    }

    public static boolean isLeafLayFlatEnabled() {
        return true;
    }

    public static boolean isLeafRightAngleSnapEnabled() {
        return false;
    }

    public static int getCaveDustBaseMaxAge() {
        return 200;
    }

    public static int getCaveDustFadeDuration() {
        return 20;
    }

    public static int getCaveDustAccelChangeChance() {
        return 180;
    }

    public static float getCaveDustMaxAcceleration() {
        return 0.03F;
    }

    public static float[] getCaveDustColor() {
        return Arrays.copyOf(CAVE_DUST_COLOR, CAVE_DUST_COLOR.length);
    }

    public static void onLeavesAnimateTick(BlockState state, Level level, BlockPos pos) {
        if (!isEnabled() || level == null || !level.isClientSide()) {
            return;
        }

        if (level.getRandom().nextInt(60) != 0) {
            return;
        }

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        if (Block.isFaceFull(belowState.getCollisionShape(level, belowPos), Direction.UP)) {
            return;
        }

        LeafData leafData = LEAVES.get(state.getBlock());
        if (leafData == null || leafData.particle == null) {
            return;
        }

        double x = pos.getX() + 0.02D + level.getRandom().nextDouble() * 0.96D;
        double y = pos.getY() - 0.05D;
        double z = pos.getZ() + 0.02D + level.getRandom().nextDouble() * 0.96D;
        Particle particle = Minecraft.getInstance().particleEngine.createParticle(leafData.particle, x, y, z, 0.0D, 0.0D, 0.0D);
        if (particle instanceof SingleQuadParticle billboard) {
            Color color = leafData.fixedColor != null ? leafData.fixedColor : new Color(BiomeColors.getAverageFoliageColor((net.minecraft.client.renderer.block.BlockAndTintGetter) level, pos));
            billboard.setColor(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
        }
    }

    public static void onBlockAnimateTick(BlockState state, Level level, BlockPos pos) {
        if (!isEnabled() || level == null || !level.isClientSide()) {
            return;
        }

        Block block = state.getBlock();
        if (trySpawnFireflies(level, pos, block)) {
            return;
        }

        if ((block == Blocks.AIR || block == Blocks.CAVE_AIR) && level.getRandom().nextInt(700) == 0 && pos.getY() < level.getSeaLevel()) {
            float lightChance = 1.0F - Math.min(8, level.getBrightness(LightLayer.SKY, pos)) / 8.0F;
            float depthChance = Math.min(1.0F, (level.getSeaLevel() - pos.getY()) / 96.0F);
            if (level.getRandom().nextFloat() < lightChance * depthChance) {
                level.addParticle(ParticularParticles.CAVE_DUST, pos.getX() + level.getRandom().nextDouble(), pos.getY() + level.getRandom().nextDouble(), pos.getZ() + level.getRandom().nextDouble(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    public static void onWaterAnimateTick(Level level, BlockPos pos, FluidState state) {
        if (!isEnabled() || level == null || !level.isClientSide()) {
            return;
        }

        if (state.isSource() || !level.getFluidState(pos.below()).is(FluidTags.WATER)) {
            return;
        }

        for (int i = 0; i < 2; ++i) {
            if (state.getValue(BlockStateProperties.FALLING)) {
                double x = pos.getX();
                double y = pos.getY() + level.getRandom().nextDouble();
                double z = pos.getZ();
                if (level.getRandom().nextBoolean()) {
                    x += level.getRandom().nextDouble();
                    z += level.getRandom().nextInt(2);
                } else {
                    x += level.getRandom().nextInt(2);
                    z += level.getRandom().nextDouble();
                }

                level.addParticle(ParticularParticles.WATERFALL_SPRAY, x, y, z, 0.0D, 0.0D, 0.0D);
            } else {
                Vec3 flow = state.getFlow(level, pos).scale(0.075D);
                double x = pos.getX() + level.getRandom().nextDouble();
                double y = pos.getY() + level.getRandom().nextDouble() * state.getOwnHeight();
                double z = pos.getZ() + level.getRandom().nextDouble();
                level.addParticle(ParticularParticles.WATERFALL_SPRAY, x, y, z, flow.x, 0.0D, flow.z);
            }
        }
    }

    public static void updateCascade(Level level, BlockPos pos, FluidState state) {
        if (!isEnabled() || level == null || !level.isClientSide()) {
            return;
        }

        // Проверки соседей используют общий Mutable (клиентский поток однопоточный),
        // чтобы не аллоцировать по ~10 BlockPos на каждый блок воды за тик.
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockPos.MutableBlockPos m = CASCADE_NEIGHBOR;

        if (state.getType() == Fluids.WATER &&
                level.getFluidState(m.set(x, y + 1, z)).getType() == Fluids.FLOWING_WATER &&
                level.getFluidState(m.set(x, y - 1, z)).getType() == Fluids.WATER) {
            int strength = 0;
            if (level.getFluidState(m.set(x, y, z - 1)).getType() == Fluids.WATER) {
                ++strength;
            }
            if (level.getFluidState(m.set(x + 1, y, z)).getType() == Fluids.WATER) {
                ++strength;
            }
            if (level.getFluidState(m.set(x, y, z + 1)).getType() == Fluids.WATER) {
                ++strength;
            }
            if (level.getFluidState(m.set(x - 1, y, z)).getType() == Fluids.WATER) {
                ++strength;
            }

            if (strength > 0) {
                if (!level.getBlockState(m.set(x, y + 1, z - 1)).isAir() &&
                        !level.getBlockState(m.set(x + 1, y + 1, z)).isAir() &&
                        !level.getBlockState(m.set(x, y + 1, z + 1)).isAir() &&
                        !level.getBlockState(m.set(x - 1, y + 1, z)).isAir()) {
                    return;
                }

                CASCADES.put(new BlockPos(x, y, z), strength);
                return;
            }
        }

        CASCADES.remove(pos);
    }

    public static void onEntityWaterSplash(Entity entity) {
        if (!isEnabled() || entity == null || !entity.level().isClientSide() || entity instanceof AbstractArrow) {
            return;
        }

        float baseY = Mth.floor(entity.getY());
        boolean foundSurface = false;
        FluidState previous = Fluids.EMPTY.defaultFluidState();
        BlockPos basePos = entity.blockPosition();
        for (int i = 0; i < 5; ++i) {
            FluidState next = entity.level().getFluidState(basePos.above(i));
            if (previous.getType() == Fluids.WATER && next.isEmpty()) {
                baseY += i - 1;
                foundSurface = true;
                break;
            }
            previous = next;
        }

        if (!foundSurface || previous.isEmpty()) {
            return;
        }

        ArrayDeque<Double> speeds = ENTITY_SPLASH_SPEEDS.get(entity.getId());
        double maxSpeed = Math.abs(entity.getDeltaMovement().y);
        if (speeds != null) {
            for (Double speed : speeds) {
                if (speed != null && speed > maxSpeed) {
                    maxSpeed = speed;
                }
            }
        }

        entity.level().addParticle(ParticularParticles.WATER_SPLASH_EMITTER, entity.getX(), baseY + previous.getOwnHeight(), entity.getZ(), entity.getBbWidth(), maxSpeed, 0.0D);
    }

    public static void onCakeEat(Level level, BlockPos pos, Entity entity) {
        if (!isEnabled() || level == null || !level.isClientSide() || entity == null) {
            return;
        }

        level.playLocalSound(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        for (int i = 0; i < 5; ++i) {
            double x = entity.getX() + (level.getRandom().nextDouble() - 0.5D) * entity.getBbWidth();
            double y = entity.getEyeY() - 0.3D;
            double z = entity.getZ() + (level.getRandom().nextDouble() - 0.5D) * entity.getBbWidth();
            level.addParticle(new ItemParticleOption(ParticleTypes.ITEM, Items.CAKE), x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static ParticleOptions getRainParticle(FluidState fluidState, BlockState blockState) {
        if (fluidState.is(FluidTags.LAVA) || blockState.is(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(blockState)) {
            return ParticleTypes.SMOKE;
        }

        if (isEnabled() && fluidState.is(FluidTags.WATER)) {
            return ParticularParticles.WATER_RIPPLE;
        }

        return ParticleTypes.RAIN;
    }

    public static void spawnWaterDripRipple(Level level, double x, double y, double z, BlockPos pos, FluidState fluidState) {
        if (!isEnabled() || level == null || !level.isClientSide() || !fluidState.is(FluidTags.WATER)) {
            return;
        }

        float waterY = pos.getY() + fluidState.getHeight(level, pos);
        if (y < waterY) {
            level.addParticle(ParticularParticles.WATER_RIPPLE, x, waterY, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void onChestActivate(BlockState state, Level level, BlockPos pos) {
        if (!isEnabled() || level == null || !level.isClientSide() || !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return;
        }

        if (!state.getValue(BlockStateProperties.WATERLOGGED)) {
            return;
        }

        if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
            spawnChestBubbles(ParticleTypes.BUBBLE_COLUMN_UP, level, pos);
        } else {
            spawnDoubleChestBubbles(ParticleTypes.BUBBLE_COLUMN_UP, level, pos, state);
        }
    }

    public static void onBarrelActivate(BlockState state, Level level, BlockPos pos) {
        if (!isEnabled() || level == null || !level.isClientSide()) {
            return;
        }

        Direction direction = state.getValue(BlockStateProperties.FACING);
        if (direction == Direction.DOWN || !level.getFluidState(pos.relative(direction)).is(FluidTags.WATER)) {
            return;
        }

        for (int i = 0; i < 10; ++i) {
            double x = pos.getX() + 0.5D + direction.getStepX() * 0.45D + (level.getRandom().nextDouble() - 0.5D) * 0.4D;
            double y = pos.getY() + 0.5D + direction.getStepY() * 0.45D + (level.getRandom().nextDouble() - 0.5D) * 0.4D;
            double z = pos.getZ() + 0.5D + direction.getStepZ() * 0.45D + (level.getRandom().nextDouble() - 0.5D) * 0.4D;
            level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void onEnderChestActivate(BlockState state, Level level, BlockPos pos) {
        if (!isEnabled() || level == null || !level.isClientSide() || !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return;
        }

        if (!state.getValue(BlockStateProperties.WATERLOGGED) || level.getBlockState(pos.above()).isSolidRender()) {
            return;
        }

        spawnChestBubbles(ParticularParticles.ENDER_BUBBLE, level, pos);
    }

    public static int getChestSoulSandUsers(Level level, BlockPos pos, BlockState state) {
        if (!isEnabled() || level == null || !level.isClientSide() || !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return -1;
        }

        if (!state.getValue(BlockStateProperties.WATERLOGGED) || state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.LEFT || !hasChestSoulSand(level, pos, state)) {
            forgetSoulState(CHEST_SOUL_STATES, level, pos);
            return -1;
        }

        ChestSoulState soulState = getOrCreateSoulState(CHEST_SOUL_STATES, level, pos);
        boolean wasOpen = soulState.open;
        if (--soulState.ticksUntilNextSwitch <= 0) {
            soulState.open = !soulState.open;
            soulState.ticksUntilNextSwitch = soulState.open ? randomBetween(level, (20 * 2), (20 * 3)) : randomBetween(level, (20 * 8), (20 * 24));
            if (soulState.open) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.AMBIENT, 1.0F, 1.0F, true);
            }
        }

        if (soulState.open && soulState.ticksUntilNextSwitch > 10 && soulState.ticksUntilNextSwitch % 2 == 0) {
            if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.SINGLE) {
                spawnBubble(ParticleTypes.BUBBLE_COLUMN_UP, level, pos);
            } else {
                spawnDoubleBubbles(ParticleTypes.BUBBLE_COLUMN_UP, level, pos, state);
            }
        }

        if (soulState.open) {
            return 1;
        }

        return wasOpen ? 0 : -1;
    }

    public static int getEnderChestSoulSandUsers(Level level, BlockPos pos, BlockState state) {
        if (!isEnabled() || level == null || !level.isClientSide() || !state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            return -1;
        }

        if (!state.getValue(BlockStateProperties.WATERLOGGED) || level.getBlockState(pos.below()).getBlock() != Blocks.SOUL_SAND || level.getBlockState(pos.above()).isSolidRender()) {
            forgetSoulState(ENDER_CHEST_SOUL_STATES, level, pos);
            return -1;
        }

        ChestSoulState soulState = getOrCreateSoulState(ENDER_CHEST_SOUL_STATES, level, pos);
        boolean wasOpen = soulState.open;
        if (--soulState.ticksUntilNextSwitch <= 0) {
            soulState.open = !soulState.open;
            soulState.ticksUntilNextSwitch = soulState.open ? randomBetween(level, (20 * 2), (20 * 3)) : randomBetween(level, (20 * 8), (20 * 24));
            if (soulState.open) {
                level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.AMBIENT, 1.0F, 1.0F, true);
            }
        }

        if (soulState.open && soulState.ticksUntilNextSwitch > 10 && soulState.ticksUntilNextSwitch % 2 == 0) {
            spawnBubble(ParticularParticles.ENDER_BUBBLE, level, pos);
        }

        if (soulState.open) {
            return 1;
        }

        return wasOpen ? 0 : -1;
    }

    public static void spawnBubble(ParticleOptions particle, Level level, BlockPos pos) {
        double x = pos.getX() + 0.25D + level.getRandom().nextDouble() * 0.5D;
        double y = pos.getY() + 0.25D + level.getRandom().nextDouble() * 0.5D;
        double z = pos.getZ() + 0.25D + level.getRandom().nextDouble() * 0.5D;
        level.addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    public static void spawnDoubleBubbles(ParticleOptions particle, Level level, BlockPos pos, BlockState state) {
        Bounds bounds = getChestBounds(state);
        for (int i = 0; i < 2; ++i) {
            double x = pos.getX() + 0.25D + level.getRandom().nextDouble() * (0.5D + bounds.xLength) + bounds.xOffset;
            double y = pos.getY() + 0.25D + level.getRandom().nextDouble() * 0.5D;
            double z = pos.getZ() + 0.25D + level.getRandom().nextDouble() * (0.5D + bounds.zLength) + bounds.zOffset;
            level.addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void spawnChestBubbles(ParticleOptions particle, Level level, BlockPos pos) {
        for (int i = 0; i < 10; ++i) {
            spawnBubble(particle, level, pos);
        }
    }

    public static void spawnDoubleChestBubbles(ParticleOptions particle, Level level, BlockPos pos, BlockState state) {
        Bounds bounds = getChestBounds(state);
        for (int i = 0; i < 20; ++i) {
            double x = pos.getX() + 0.25D + level.getRandom().nextDouble() * (0.5D + bounds.xLength) + bounds.xOffset;
            double y = pos.getY() + 0.25D + level.getRandom().nextDouble() * 0.5D;
            double z = pos.getZ() + 0.25D + level.getRandom().nextDouble() * (0.5D + bounds.zLength) + bounds.zOffset;
            level.addParticle(particle, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void emitCascades(Minecraft mc, Level level) {
        CASCADES.forEach((pos, strength) -> {
            float height = level.getFluidState(pos.above()).getOwnHeight();
            double x = pos.getX();
            double y = pos.getY() + level.getRandom().nextDouble() * height + 1.0D;
            double z = pos.getZ();

            if (level.getRandom().nextBoolean()) {
                x += level.getRandom().nextDouble();
                z += level.getRandom().nextInt(2);
            } else {
                x += level.getRandom().nextInt(2);
                z += level.getRandom().nextDouble();
            }

            Particle particle = mc.particleEngine.createParticle(ParticularParticles.CASCADE, x, y, z, 0.0D, 0.0D, 0.0D);
            if (particle != null) {
                float size = strength / 4.0F * height;
                particle.scale(1.0F - (1.0F - size) / 2.0F);
            }
        });
    }

    private static void trackEntitySplashSpeeds(ClientLevel level) {
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == null) {
                continue;
            }

            seen.add(entity.getId());
            ArrayDeque<Double> deque = ENTITY_SPLASH_SPEEDS.computeIfAbsent(entity.getId(), key -> new ArrayDeque<>());
            deque.offer(Math.abs(entity.getDeltaMovement().y));
            while (deque.size() > 4) {
                deque.poll();
            }
        }

        Iterator<Map.Entry<Integer, ArrayDeque<Double>>> iterator = ENTITY_SPLASH_SPEEDS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!seen.contains(iterator.next().getKey())) {
                iterator.remove();
            }
        }
    }

    private static boolean trySpawnFireflies(Level level, BlockPos pos, Block block) {
        double chance = level.getRandom().nextDouble();
        if ((block == Blocks.SHORT_GRASS && chance < (1.0F / 6.0F)) ||
                (block == Blocks.TALL_GRASS && chance < (1.0F / 12.0F)) ||
                (block instanceof FlowerBlock && chance < 1.0F) ||
                (block instanceof TallFlowerBlock && chance < 0.5F)) {
            spawnFirefly(level, pos);
            return true;
        }

        return false;
    }

    private static void spawnFirefly(Level level, BlockPos pos) {
        if (level.getRandom().nextDouble() > fireflyFrequency) {
            return;
        }

        Biome biome = level.getBiome(pos).value();
        float downfall = biome.hasPrecipitation() ? 0.5F : 0.0F;
        if (level.isRaining() || level.getRandom().nextInt(Math.max(1, 30 - (int) (10.0F * downfall))) != 0) {
            return;
        }

        long time = level.getOverworldClockTime() % 24000L;
        float temperature = biome.getBaseTemperature();
        if (time >= 12000 && time <= 23000 && temperature >= 0.5F && temperature <= 0.99F) {
            level.addParticle(ParticularParticles.FIREFLY, pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F, 0.0D, 0.0D, 0.0D);
        }
    }

    private static Bounds getChestBounds(BlockState state) {
        ChestType chestType = state.getValue(BlockStateProperties.CHEST_TYPE);
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        int xLength = 0;
        int zLength = 0;
        int xOffset = 0;
        int zOffset = 0;

        switch (facing) {
            case NORTH:
                xLength = 1;
                if (chestType == ChestType.RIGHT) {
                    xOffset = -1;
                }
                break;
            case SOUTH:
                xLength = 1;
                if (chestType == ChestType.LEFT) {
                    xOffset = -1;
                }
                break;
            case EAST:
                zLength = 1;
                if (chestType == ChestType.RIGHT) {
                    zOffset = -1;
                }
                break;
            case WEST:
                zLength = 1;
                if (chestType == ChestType.LEFT) {
                    zOffset = -1;
                }
                break;
            default:
                break;
        }

        return new Bounds(xLength, zLength, xOffset, zOffset);
    }

    private static boolean hasChestSoulSand(Level level, BlockPos pos, BlockState state) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).getBlock() == Blocks.SOUL_SAND) {
            return true;
        }

        if (state.getValue(BlockStateProperties.CHEST_TYPE) == ChestType.RIGHT) {
            BlockPos otherHalf = below.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getCounterClockWise());
            return level.getBlockState(otherHalf).getBlock() == Blocks.SOUL_SAND;
        }

        return false;
    }

    private static ChestSoulState getOrCreateSoulState(Map<String, ChestSoulState> map, Level level, BlockPos pos) {
        String key = soulKey(level, pos);
        ChestSoulState state = map.get(key);
        if (state == null) {
            state = new ChestSoulState();
            map.put(key, state);
        }
        return state;
    }

    private static void forgetSoulState(Map<String, ChestSoulState> map, Level level, BlockPos pos) {
        map.remove(soulKey(level, pos));
    }

    private static String soulKey(Level level, BlockPos pos) {
        Identifier dimensionId = level.dimension().identifier();
        return dimensionId + ":" + pos.asLong();
    }

    private static int randomBetween(Level level, int min, int max) {
        return min + level.getRandom().nextInt(Math.max(1, max - min + 1));
    }

    private static final class LeafData {
        private final ParticleOptions particle;
        private final Color fixedColor;

        private LeafData(ParticleOptions particle, Color fixedColor) {
            this.particle = particle;
            this.fixedColor = fixedColor;
        }
    }

    private static final class Bounds {
        private final int xLength;
        private final int zLength;
        private final int xOffset;
        private final int zOffset;

        private Bounds(int xLength, int zLength, int xOffset, int zOffset) {
            this.xLength = xLength;
            this.zLength = zLength;
            this.xOffset = xOffset;
            this.zOffset = zOffset;
        }
    }

    private static final class ChestSoulState {
        private int ticksUntilNextSwitch = 20;
        private boolean open;
    }
}
