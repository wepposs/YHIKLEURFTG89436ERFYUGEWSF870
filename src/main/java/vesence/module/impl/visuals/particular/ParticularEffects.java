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
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.LightType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.biome.FoliageColors;
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
    private static final net.minecraft.util.math.BlockPos.Mutable CASCADE_NEIGHBOR = new net.minecraft.util.math.BlockPos.Mutable();
    private static final Map<String, ChestSoulState> CHEST_SOUL_STATES = new HashMap<>();
    private static final Map<String, ChestSoulState> ENDER_CHEST_SOUL_STATES = new HashMap<>();

    private static ResourceLocation currentDimension;
    private static float fireflyFrequency = 1.0F;
    private static long lastWorldTime = Long.MIN_VALUE;

    static {
        LEAVES.put(Blocks.OAK_LEAVES, new LeafData(ParticularParticles.OAK_LEAF, null));
        LEAVES.put(Blocks.BIRCH_LEAVES, new LeafData(ParticularParticles.BIRCH_LEAF, new Color(FoliageColors.BIRCH)));
        LEAVES.put(Blocks.SPRUCE_LEAVES, new LeafData(ParticularParticles.SPRUCE_LEAF, new Color(FoliageColors.SPRUCE)));
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

        ClientLevel world = mc.level;
        ResourceLocation dimension = world.getRegistryKey().getValue();
        if (currentDimension == null || !currentDimension.equals(dimension)) {
            currentDimension = dimension;
            CASCADES.clear();
            CHEST_SOUL_STATES.clear();
            ENDER_CHEST_SOUL_STATES.clear();
            ENTITY_SPLASH_SPEEDS.clear();
            ENTITY_IN_WATER.clear();
            lastWorldTime = Long.MIN_VALUE;
        }

        long dayTime = world.getTimeOfDay();
        if (dayTime != lastWorldTime) {
            if (dayTime % 24000L == 12000) {
                fireflyFrequency = FIREFLY_DAILY_RANDOM[world.random.nextInt(FIREFLY_DAILY_RANDOM.length)];
            }

            lastWorldTime = dayTime;
        }

        if (mc.player == null) {
            return;
        }

        emitRandomDisplayTicks(mc, world);
        emitCascades(mc, world);
        trackEntitySplashSpeeds(world);
        emitEntitySplashes(world);
        vesence.module.impl.visuals.particular.splashes.WaterCrownRenderer.tick(world);
    }

    /**
     * Воспроизводит ванильный цикл {@code ClientLevel.doRandomBlockDisplayTicks}: выбирает
     * случайные блоки вокруг игрока и вызывает соответствующие обработчики Particular
     * (листья, светлячки, пыль пещер, брызги водопадов, каскады). Так эффекты работают
     * без миксинов в ванильные классы блоков.
     */
    private static void emitRandomDisplayTicks(Minecraft mc, ClientLevel world) {
        if (mc.player == null) {
            return;
        }

        int px = Mth.floor(mc.player.getX());
        int py = Mth.floor(mc.player.getY());
        int pz = Mth.floor(mc.player.getZ());
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int i = 0; i < 667; ++i) {
            randomDisplayTickAt(world, mutable, px, py, pz, 16);
            randomDisplayTickAt(world, mutable, px, py, pz, 32);
        }
    }

    private static void randomDisplayTickAt(ClientLevel world, BlockPos.Mutable mutable, int px, int py, int pz, int radius) {
        int x = px + world.random.nextInt(radius) - world.random.nextInt(radius);
        int y = py + world.random.nextInt(radius) - world.random.nextInt(radius);
        int z = pz + world.random.nextInt(radius) - world.random.nextInt(radius);
        mutable.set(x, y, z);
        BlockState state = world.getBlockState(mutable);
        // Передаём mutable напрямую (обработчики читают позицию сразу или копируют её),
        // чтобы не аллоцировать BlockPos на каждую из ~1334 итераций за тик.
        onLeavesAnimateTick(state, world, mutable);
        onBlockAnimateTick(state, world, mutable);
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty()) {
            onWaterAnimateTick(world, mutable, fluidState);
            updateCascade(world, mutable, fluidState);
        }
    }

    /**
     * Отслеживает вход сущностей в воду и порождает всплеск. Заменяет ванильный хук
     * splash-звука/частиц: если сущность в этом тике пересекла поверхность воды сверху
     * вниз (была выше, стала в воде), спавним эмиттер всплеска.
     */
    private static void emitEntitySplashes(ClientLevel world) {
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : world.getEntities()) {
            processEntitySplash(entity, seen);
        }

        // Явно обрабатываем клиентского игрока: в некоторых версиях он не входит в
        // world.getEntities(), а всплеск при собственном падении в воду важнее всего.
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

        boolean inWater = entity.isTouchingWater();
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

    public static void onLeavesAnimateTick(BlockState state, Level world, BlockPos pos) {
        if (!isEnabled() || world == null || !world.isClient()) {
            return;
        }

        if (world.random.nextInt(60) != 0) {
            return;
        }

        BlockPos belowPos = pos.down();
        BlockState belowState = world.getBlockState(belowPos);
        if (Block.isFaceFullSquare(belowState.getCollisionShape(world, belowPos), Direction.UP)) {
            return;
        }

        LeafData leafData = LEAVES.get(state.getBlock());
        if (leafData == null || leafData.particle == null) {
            return;
        }

        double x = pos.getX() + 0.02D + world.random.nextDouble() * 0.96D;
        double y = pos.getY() - 0.05D;
        double z = pos.getZ() + 0.02D + world.random.nextDouble() * 0.96D;
        Particle particle = Minecraft.getInstance().particleManager.addParticle(leafData.particle, x, y, z, 0.0D, 0.0D, 0.0D);
        if (particle instanceof BillboardParticle billboard) {
            Color color = leafData.fixedColor != null ? leafData.fixedColor : new Color(BiomeColors.getFoliageColor(world, pos));
            billboard.setColor(color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F);
        }
    }

    public static void onBlockAnimateTick(BlockState state, Level world, BlockPos pos) {
        if (!isEnabled() || world == null || !world.isClient()) {
            return;
        }

        Block block = state.getBlock();
        if (trySpawnFireflies(world, pos, block)) {
            return;
        }

        if ((block == Blocks.AIR || block == Blocks.CAVE_AIR) && world.random.nextInt(700) == 0 && pos.getY() < world.getSeaLevel()) {
            float lightChance = 1.0F - Math.min(8, world.getLightLevel(LightType.SKY, pos)) / 8.0F;
            float depthChance = Math.min(1.0F, (world.getSeaLevel() - pos.getY()) / 96.0F);
            if (world.random.nextFloat() < lightChance * depthChance) {
                world.addParticleClient(ParticularParticles.CAVE_DUST, pos.getX() + world.random.nextDouble(), pos.getY() + world.random.nextDouble(), pos.getZ() + world.random.nextDouble(), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    public static void onWaterAnimateTick(Level world, BlockPos pos, FluidState state) {
        if (!isEnabled() || world == null || !world.isClient()) {
            return;
        }

        if (state.isStill() || !world.getFluidState(pos.down()).isIn(FluidTags.WATER)) {
            return;
        }

        for (int i = 0; i < 2; ++i) {
            if (state.get(Properties.FALLING)) {
                double x = pos.getX();
                double y = pos.getY() + world.random.nextDouble();
                double z = pos.getZ();
                if (world.random.nextBoolean()) {
                    x += world.random.nextDouble();
                    z += world.random.nextInt(2);
                } else {
                    x += world.random.nextInt(2);
                    z += world.random.nextDouble();
                }

                world.addParticleClient(ParticularParticles.WATERFALL_SPRAY, x, y, z, 0.0D, 0.0D, 0.0D);
            } else {
                Vec3 flow = state.getVelocity(world, pos).multiply(0.075D);
                double x = pos.getX() + world.random.nextDouble();
                double y = pos.getY() + world.random.nextDouble() * state.getHeight();
                double z = pos.getZ() + world.random.nextDouble();
                world.addParticleClient(ParticularParticles.WATERFALL_SPRAY, x, y, z, flow.x, 0.0D, flow.z);
            }
        }
    }

    public static void updateCascade(Level world, BlockPos pos, FluidState state) {
        if (!isEnabled() || world == null || !world.isClient()) {
            return;
        }

        // Проверки соседей используют общий Mutable (клиентский поток однопоточный),
        // чтобы не аллоцировать по ~10 BlockPos на каждый блок воды за тик.
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        BlockPos.Mutable m = CASCADE_NEIGHBOR;

        if (state.getFluid() == Fluids.WATER &&
                world.getFluidState(m.set(x, y + 1, z)).getFluid() == Fluids.FLOWING_WATER &&
                world.getFluidState(m.set(x, y - 1, z)).getFluid() == Fluids.WATER) {
            int strength = 0;
            if (world.getFluidState(m.set(x, y, z - 1)).getFluid() == Fluids.WATER) {
                ++strength;
            }
            if (world.getFluidState(m.set(x + 1, y, z)).getFluid() == Fluids.WATER) {
                ++strength;
            }
            if (world.getFluidState(m.set(x, y, z + 1)).getFluid() == Fluids.WATER) {
                ++strength;
            }
            if (world.getFluidState(m.set(x - 1, y, z)).getFluid() == Fluids.WATER) {
                ++strength;
            }

            if (strength > 0) {
                if (!world.getBlockState(m.set(x, y + 1, z - 1)).isAir() &&
                        !world.getBlockState(m.set(x + 1, y + 1, z)).isAir() &&
                        !world.getBlockState(m.set(x, y + 1, z + 1)).isAir() &&
                        !world.getBlockState(m.set(x - 1, y + 1, z)).isAir()) {
                    return;
                }

                CASCADES.put(new BlockPos(x, y, z), strength);
                return;
            }
        }

        CASCADES.remove(pos);
    }

    public static void onEntityWaterSplash(Entity entity) {
        if (!isEnabled() || entity == null || !entity.getEntityWorld().isClient() || entity instanceof PersistentProjectileEntity) {
            return;
        }

        float baseY = Mth.floor(entity.getY());
        boolean foundSurface = false;
        FluidState previous = Fluids.EMPTY.getDefaultState();
        BlockPos basePos = entity.getBlockPos();
        for (int i = 0; i < 5; ++i) {
            FluidState next = entity.getEntityWorld().getFluidState(basePos.up(i));
            if (previous.getFluid() == Fluids.WATER && next.isEmpty()) {
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

        entity.getEntityWorld().addParticleClient(ParticularParticles.WATER_SPLASH_EMITTER, entity.getX(), baseY + previous.getHeight(), entity.getZ(), entity.getWidth(), maxSpeed, 0.0D);
    }

    public static void onCakeEat(Level world, BlockPos pos, Entity entity) {
        if (!isEnabled() || world == null || !world.isClient() || entity == null) {
            return;
        }

        world.playSoundClient(entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        for (int i = 0; i < 5; ++i) {
            double x = entity.getX() + (world.random.nextDouble() - 0.5D) * entity.getWidth();
            double y = entity.getEyeY() - 0.3D;
            double z = entity.getZ() + (world.random.nextDouble() - 0.5D) * entity.getWidth();
            world.addParticleClient(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(Items.CAKE)), x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static ParticleEffect getRainParticle(FluidState fluidState, BlockState blockState) {
        if (fluidState.isIn(FluidTags.LAVA) || blockState.isOf(Blocks.MAGMA_BLOCK) || CampfireBlock.isLitCampfire(blockState)) {
            return ParticleTypes.SMOKE;
        }

        if (isEnabled() && fluidState.isIn(FluidTags.WATER)) {
            return ParticularParticles.WATER_RIPPLE;
        }

        return ParticleTypes.RAIN;
    }

    public static void spawnWaterDripRipple(Level world, double x, double y, double z, BlockPos pos, FluidState fluidState) {
        if (!isEnabled() || world == null || !world.isClient() || !fluidState.isIn(FluidTags.WATER)) {
            return;
        }

        float waterY = pos.getY() + fluidState.getHeight(world, pos);
        if (y < waterY) {
            world.addParticleClient(ParticularParticles.WATER_RIPPLE, x, waterY, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void onChestActivate(BlockState state, Level world, BlockPos pos) {
        if (!isEnabled() || world == null || !world.isClient() || !state.contains(Properties.WATERLOGGED)) {
            return;
        }

        if (!state.get(Properties.WATERLOGGED)) {
            return;
        }

        if (state.get(Properties.CHEST_TYPE) == ChestType.SINGLE) {
            spawnChestBubbles(ParticleTypes.BUBBLE_COLUMN_UP, world, pos);
        } else {
            spawnDoubleChestBubbles(ParticleTypes.BUBBLE_COLUMN_UP, world, pos, state);
        }
    }

    public static void onBarrelActivate(BlockState state, Level world, BlockPos pos) {
        if (!isEnabled() || world == null || !world.isClient()) {
            return;
        }

        Direction direction = state.get(Properties.FACING);
        if (direction == Direction.DOWN || !world.getFluidState(pos.offset(direction)).isIn(FluidTags.WATER)) {
            return;
        }

        for (int i = 0; i < 10; ++i) {
            double x = pos.getX() + 0.5D + direction.getOffsetX() * 0.45D + (world.random.nextDouble() - 0.5D) * 0.4D;
            double y = pos.getY() + 0.5D + direction.getOffsetY() * 0.45D + (world.random.nextDouble() - 0.5D) * 0.4D;
            double z = pos.getZ() + 0.5D + direction.getOffsetZ() * 0.45D + (world.random.nextDouble() - 0.5D) * 0.4D;
            world.addParticleClient(ParticleTypes.BUBBLE_COLUMN_UP, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void onEnderChestActivate(BlockState state, Level world, BlockPos pos) {
        if (!isEnabled() || world == null || !world.isClient() || !state.contains(Properties.WATERLOGGED)) {
            return;
        }

        if (!state.get(Properties.WATERLOGGED) || world.getBlockState(pos.up()).isSolidBlock(world, pos.up())) {
            return;
        }

        spawnChestBubbles(ParticularParticles.ENDER_BUBBLE, world, pos);
    }

    public static int getChestSoulSandUsers(Level world, BlockPos pos, BlockState state) {
        if (!isEnabled() || world == null || !world.isClient() || !state.contains(Properties.WATERLOGGED)) {
            return -1;
        }

        if (!state.get(Properties.WATERLOGGED) || state.get(Properties.CHEST_TYPE) == ChestType.LEFT || !hasChestSoulSand(world, pos, state)) {
            forgetSoulState(CHEST_SOUL_STATES, world, pos);
            return -1;
        }

        ChestSoulState soulState = getOrCreateSoulState(CHEST_SOUL_STATES, world, pos);
        boolean wasOpen = soulState.open;
        if (--soulState.ticksUntilNextSwitch <= 0) {
            soulState.open = !soulState.open;
            soulState.ticksUntilNextSwitch = soulState.open ? randomBetween(world, (20 * 2), (20 * 3)) : randomBetween(world, (20 * 8), (20 * 24));
            if (soulState.open) {
                world.playSoundClient(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.AMBIENT, 1.0F, 1.0F, true);
            }
        }

        if (soulState.open && soulState.ticksUntilNextSwitch > 10 && soulState.ticksUntilNextSwitch % 2 == 0) {
            if (state.get(Properties.CHEST_TYPE) == ChestType.SINGLE) {
                spawnBubble(ParticleTypes.BUBBLE_COLUMN_UP, world, pos);
            } else {
                spawnDoubleBubbles(ParticleTypes.BUBBLE_COLUMN_UP, world, pos, state);
            }
        }

        if (soulState.open) {
            return 1;
        }

        return wasOpen ? 0 : -1;
    }

    public static int getEnderChestSoulSandUsers(Level world, BlockPos pos, BlockState state) {
        if (!isEnabled() || world == null || !world.isClient() || !state.contains(Properties.WATERLOGGED)) {
            return -1;
        }

        if (!state.get(Properties.WATERLOGGED) || world.getBlockState(pos.down()).getBlock() != Blocks.SOUL_SAND || world.getBlockState(pos.up()).isSolidBlock(world, pos.up())) {
            forgetSoulState(ENDER_CHEST_SOUL_STATES, world, pos);
            return -1;
        }

        ChestSoulState soulState = getOrCreateSoulState(ENDER_CHEST_SOUL_STATES, world, pos);
        boolean wasOpen = soulState.open;
        if (--soulState.ticksUntilNextSwitch <= 0) {
            soulState.open = !soulState.open;
            soulState.ticksUntilNextSwitch = soulState.open ? randomBetween(world, (20 * 2), (20 * 3)) : randomBetween(world, (20 * 8), (20 * 24));
            if (soulState.open) {
                world.playSoundClient(pos.getX(), pos.getY(), pos.getZ(), SoundEvents.BLOCK_BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.AMBIENT, 1.0F, 1.0F, true);
            }
        }

        if (soulState.open && soulState.ticksUntilNextSwitch > 10 && soulState.ticksUntilNextSwitch % 2 == 0) {
            spawnBubble(ParticularParticles.ENDER_BUBBLE, world, pos);
        }

        if (soulState.open) {
            return 1;
        }

        return wasOpen ? 0 : -1;
    }

    public static void spawnBubble(ParticleEffect particle, Level world, BlockPos pos) {
        double x = pos.getX() + 0.25D + world.random.nextDouble() * 0.5D;
        double y = pos.getY() + 0.25D + world.random.nextDouble() * 0.5D;
        double z = pos.getZ() + 0.25D + world.random.nextDouble() * 0.5D;
        world.addParticleClient(particle, x, y, z, 0.0D, 0.0D, 0.0D);
    }

    public static void spawnDoubleBubbles(ParticleEffect particle, Level world, BlockPos pos, BlockState state) {
        Bounds bounds = getChestBounds(state);
        for (int i = 0; i < 2; ++i) {
            double x = pos.getX() + 0.25D + world.random.nextDouble() * (0.5D + bounds.xLength) + bounds.xOffset;
            double y = pos.getY() + 0.25D + world.random.nextDouble() * 0.5D;
            double z = pos.getZ() + 0.25D + world.random.nextDouble() * (0.5D + bounds.zLength) + bounds.zOffset;
            world.addParticleClient(particle, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    public static void spawnChestBubbles(ParticleEffect particle, Level world, BlockPos pos) {
        for (int i = 0; i < 10; ++i) {
            spawnBubble(particle, world, pos);
        }
    }

    public static void spawnDoubleChestBubbles(ParticleEffect particle, Level world, BlockPos pos, BlockState state) {
        Bounds bounds = getChestBounds(state);
        for (int i = 0; i < 20; ++i) {
            double x = pos.getX() + 0.25D + world.random.nextDouble() * (0.5D + bounds.xLength) + bounds.xOffset;
            double y = pos.getY() + 0.25D + world.random.nextDouble() * 0.5D;
            double z = pos.getZ() + 0.25D + world.random.nextDouble() * (0.5D + bounds.zLength) + bounds.zOffset;
            world.addParticleClient(particle, x, y, z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void emitCascades(Minecraft mc, Level world) {
        CASCADES.forEach((pos, strength) -> {
            float height = world.getFluidState(pos.up()).getHeight();
            double x = pos.getX();
            double y = pos.getY() + world.random.nextDouble() * height + 1.0D;
            double z = pos.getZ();

            if (world.random.nextBoolean()) {
                x += world.random.nextDouble();
                z += world.random.nextInt(2);
            } else {
                x += world.random.nextInt(2);
                z += world.random.nextDouble();
            }

            Particle particle = mc.particleManager.addParticle(ParticularParticles.CASCADE, x, y, z, 0.0D, 0.0D, 0.0D);
            if (particle != null) {
                float size = strength / 4.0F * height;
                particle.scale(1.0F - (1.0F - size) / 2.0F);
            }
        });
    }

    private static void trackEntitySplashSpeeds(ClientLevel world) {
        Set<Integer> seen = new HashSet<>();
        for (Entity entity : world.getEntities()) {
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

    private static boolean trySpawnFireflies(Level world, BlockPos pos, Block block) {
        double chance = world.random.nextDouble();
        if ((block == Blocks.SHORT_GRASS && chance < (1.0F / 6.0F)) ||
                (block == Blocks.TALL_GRASS && chance < (1.0F / 12.0F)) ||
                (block instanceof FlowerBlock && chance < 1.0F) ||
                (block instanceof TallFlowerBlock && chance < 0.5F)) {
            spawnFirefly(world, pos);
            return true;
        }

        return false;
    }

    private static void spawnFirefly(Level world, BlockPos pos) {
        if (world.random.nextDouble() > fireflyFrequency) {
            return;
        }

        Biome biome = world.getBiome(pos).value();
        float downfall = biome.hasPrecipitation() ? 0.5F : 0.0F;
        if (world.isRaining() || world.random.nextInt(Math.max(1, 30 - (int) (10.0F * downfall))) != 0) {
            return;
        }

        long time = world.getTimeOfDay() % 24000L;
        float temperature = biome.getTemperature();
        if (time >= 12000 && time <= 23000 && temperature >= 0.5F && temperature <= 0.99F) {
            world.addParticleClient(ParticularParticles.FIREFLY, pos.getX() + 0.5F, pos.getY() + 0.5F, pos.getZ() + 0.5F, 0.0D, 0.0D, 0.0D);
        }
    }

    private static Bounds getChestBounds(BlockState state) {
        ChestType chestType = state.get(Properties.CHEST_TYPE);
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
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

    private static boolean hasChestSoulSand(Level world, BlockPos pos, BlockState state) {
        BlockPos below = pos.down();
        if (world.getBlockState(below).getBlock() == Blocks.SOUL_SAND) {
            return true;
        }

        if (state.get(Properties.CHEST_TYPE) == ChestType.RIGHT) {
            BlockPos otherHalf = below.offset(state.get(Properties.HORIZONTAL_FACING).rotateYCounterclockwise());
            return world.getBlockState(otherHalf).getBlock() == Blocks.SOUL_SAND;
        }

        return false;
    }

    private static ChestSoulState getOrCreateSoulState(Map<String, ChestSoulState> map, Level world, BlockPos pos) {
        String key = soulKey(world, pos);
        ChestSoulState state = map.get(key);
        if (state == null) {
            state = new ChestSoulState();
            map.put(key, state);
        }
        return state;
    }

    private static void forgetSoulState(Map<String, ChestSoulState> map, Level world, BlockPos pos) {
        map.remove(soulKey(world, pos));
    }

    private static String soulKey(Level world, BlockPos pos) {
        ResourceLocation dimensionId = world.getRegistryKey().getValue();
        return dimensionId + ":" + pos.asLong();
    }

    private static int randomBetween(Level world, int min, int max) {
        return min + world.random.nextInt(Math.max(1, max - min + 1));
    }

    private static final class LeafData {
        private final ParticleEffect particle;
        private final Color fixedColor;

        private LeafData(ParticleEffect particle, Color fixedColor) {
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
