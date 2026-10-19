package vesence.module.impl.visuals;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import net.minecraft.client.render.MultiBufferSource.Immediate;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;

/**
 * Trails — полный порт ru.whylol.client.modules.impl.render.Trails.
 * Три режима: Classic (лента-след), Dash (текстурные кубики+искры+bloom),
 * Шипучка (частицы из-под ног с физикой).
 */
@IModule(name = "Trails", description = "Красивый след за игроком", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Trails extends Module {

    private static final String MODE_CLASSIC = "Classic";
    private static final String MODE_DASH = "Dash";
    private static final String MODE_FIZZ = "Шипучка";

    public final ModeSetting mode = new ModeSetting("Режим", MODE_CLASSIC, MODE_CLASSIC, MODE_DASH, MODE_FIZZ);
    public final SliderSetting duration = new SliderSetting("Длительность", 300.0, 100.0, 1000.0, 10.0)
            .hidden(() -> !mode.is(MODE_CLASSIC));

    // ── Classic ring-buffer ──
    private static final int MAX_POINTS = 1024;
    private final Point[] pointBuffer = new Point[MAX_POINTS];
    private int pointHead = 0;
    private int pointSize = 0;
    private final List<Point> pointsView = new ArrayList<>(MAX_POINTS);

    private final DashTrail dash = new DashTrail();
    private final FizzTrail fizz = new FizzTrail();
    private ByteBufferBuilder trailAllocClassic = null;

    // ── Classic pipeline ──
    private static final RenderPipeline STRIP_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("vesence", "pipeline/world/trails_strip"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.TRIANGLE_STRIP)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());
    private static final RenderType STRIP_LAYER = RenderType.of("vesence_trails_strip",
            RenderSetup.builder(STRIP_PIPELINE).expectedBufferSize(262144).translucent().build());
    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("minecraft", "rendertype_lines"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.DEBUG_LINE_STRIP)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build());
    private static final RenderType LINE_LAYER = RenderType.of("vesence_trails_line",
            RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(131072).translucent().build());

    public Trails() {
        this.addSettings(this.mode, this.duration);
        for (int i = 0; i < MAX_POINTS; i++) this.pointBuffer[i] = new Point();
    }

    @Override
    public void onEnable() {
        super.onEnable();
        this.dash.reset();
        this.fizz.reset();
    }

    @Override
    public void onDisable() {
        this.pointSize = 0;
        this.pointHead = 0;
        this.dash.reset();
        this.fizz.reset();
        if (trailAllocClassic != null) { trailAllocClassic.close(); trailAllocClassic = null; }
        super.onDisable();
    }

    @EventInit
    public void onTick(EventUpdate event) {
        if (mc.player == null || mc.level == null) return;
        if (this.mode.is(MODE_DASH)) this.dash.onTick();
        else if (this.mode.is(MODE_FIZZ)) this.fizz.onTick();
    }

    @EventInit
    public void onRender(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;
        if (this.mode.is(MODE_DASH)) { this.dash.onRender(event); return; }
        if (this.mode.is(MODE_FIZZ)) { this.fizz.onRender(event); return; }

        // Classic
        if (mc.options.getPerspective().isFirstPerson()) return;
        long now = System.currentTimeMillis();
        float lifetime = (float) (double) this.duration.get();
        evictExpired(now, lifetime);
        appendCurrentPosition(now, event.getTickDelta());
        render3DPoints(event.getMatrixStack());
    }

    // ── Classic ring-buffer ──
    private void evictExpired(long now, float lifetimeMs) {
        while (this.pointSize > 0) {
            Point oldest = this.pointBuffer[logicalIndex(0)];
            if ((float) (now - oldest.time) > lifetimeMs) {
                this.pointHead = (this.pointHead + 1) % MAX_POINTS;
                this.pointSize--;
            } else break;
        }
    }

    private int logicalIndex(int i) {
        return (this.pointHead + i) % MAX_POINTS;
    }

    private void appendCurrentPosition(long now, float partialTicks) {
        int writeIdx;
        if (this.pointSize < MAX_POINTS) {
            writeIdx = (this.pointHead + this.pointSize) % MAX_POINTS;
            this.pointSize++;
        } else {
            writeIdx = this.pointHead;
            this.pointHead = (this.pointHead + 1) % MAX_POINTS;
        }
        Point p = this.pointBuffer[writeIdx];
        p.x = Mth.lerp(partialTicks, mc.player.lastRenderX, mc.player.getX());
        p.y = Mth.lerp(partialTicks, mc.player.lastRenderY, mc.player.getY());
        p.z = Mth.lerp(partialTicks, mc.player.lastRenderZ, mc.player.getZ());
        p.time = now;
    }

    private void buildPointsView() {
        this.pointsView.clear();
        for (int i = 0; i < this.pointSize; i++) this.pointsView.add(this.pointBuffer[logicalIndex(i)]);
    }

    private void render3DPoints(PoseStack matrixStack) {
        if (this.pointSize < 2) return;
        buildPointsView();

        Vec3 view = mc.gameRenderer.getCamera().getCameraPos();
        matrixStack.pushPose();
        matrixStack.translate(-view.x, -view.y, -view.z);
        Matrix4f matrix = matrixStack.last().getPositionMatrix();

        int themeColor = ColorUtil.fade();
        int r = (themeColor >> 16) & 0xFF;
        int g = (themeColor >> 8) & 0xFF;
        int b = themeColor & 0xFF;
        float height = mc.player.getHeight();

        if (trailAllocClassic == null) { trailAllocClassic = new ByteBufferBuilder(262144); }
        Immediate immediate = MultiBufferSource.immediate(trailAllocClassic);
        try {
            VertexConsumer strip = immediate.getBuffer(STRIP_LAYER);
            int total = this.pointsView.size();
            int index = 0;
            for (Point p : this.pointsView) {
                int a = (int) ((float) index / total * 0.7F * 255.0F);
                strip.vertex(matrix, (float) p.x, (float) (p.y + height), (float) p.z).color(r, g, b, a);
                strip.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a);
                index++;
            }

            VertexConsumer lineTop = immediate.getBuffer(LINE_LAYER);
            index = 0;
            for (Point p : this.pointsView) {
                int a = (int) (Math.min((float) index / total * 1.5F, 1.0F) * 255.0F);
                lineTop.vertex(matrix, (float) p.x, (float) (p.y + height), (float) p.z).color(r, g, b, a).normal(0, 1, 0);
                index++;
            }
            immediate.draw();

            // Нижняя линия — отдельный immediate (DEBUG_LINE_STRIP нельзя дважды в одном буфере без разрыва)
            Immediate imm2 = MultiBufferSource.immediate(trailAllocClassic);
            VertexConsumer lineBot = imm2.getBuffer(LINE_LAYER);
            index = 0;
            for (Point p : this.pointsView) {
                int a = (int) (Math.min((float) index / total * 1.5F, 1.0F) * 255.0F);
                lineBot.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).normal(0, 1, 0);
                index++;
            }
            imm2.draw();
        } finally {
            // reused across frames
        }
        matrixStack.popPose();
    }

    private static class Point {
        double x, y, z;
        long time;
    }

    private static boolean shouldTrail(Player player, float range) {
        if (mc.player == null) return false;
        if (player == mc.player) return true;
        if (mc.player.distanceTo(player) > range) return false;
        try {
            return FriendStorage.isFriend(player.getName().getString());
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static int themeColor() {
        int c = ColorUtil.fade();
        if ((c >> 24 & 0xFF) == 0) c |= 0xFF000000;
        return c;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DASH TRAIL
    // ═══════════════════════════════════════════════════════════════════════
    private static final class DashTrail {
        private static final ResourceLocation BLOOM_TEX = ResourceLocation.of("vesence", "textures/dashtrail/dashbloomsample.png");
        private static final RenderPipeline TEX_PIPELINE = RenderPipelines.register(
                RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                        .withLocation(ResourceLocation.of("vesence", "pipeline/world/dashtrail_tex"))
                        .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                        .withCull(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withBlend(BlendFunction.LIGHTNING)
                        .build());
        private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
                RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                        .withLocation(ResourceLocation.of("minecraft", "rendertype_lines"))
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.DEBUG_LINES)
                        .withCull(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .build());
        private static final RenderType BLOOM_LAYER = RenderType.of("vesence_dashtrail_bloom",
                RenderSetup.builder(TEX_PIPELINE).expectedBufferSize(262144).translucent().texture("Sampler0", BLOOM_TEX).build());
        private static final RenderType LINE_LAYER = RenderType.of("vesence_dashtrail_line",
                RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(131072).translucent().build());

        /** Кэш RenderType'ов по текстуре кубика (main-слой, additive). */
        private static final Map<ResourceLocation, RenderType> CUBIC_LAYERS = new HashMap<>();

        private static RenderType cubicLayer(ResourceLocation tex) {
            return CUBIC_LAYERS.computeIfAbsent(tex, t -> RenderType.of(
                    "vesence_dashtrail_cubic_" + t.getPath().replace('/', '_').replace('.', '_'),
                    RenderSetup.builder(TEX_PIPELINE).expectedBufferSize(16384).translucent().texture("Sampler0", t).build()));
        }

        static ResourceLocation staticTex(int n) {
            return ResourceLocation.of("vesence", "textures/dashtrail/dashcubics/dashcubic" + n + ".png");
        }

        static ResourceLocation groupTex(int group, int frame) {
            return ResourceLocation.of("vesence", "textures/dashtrail/dashcubics/group_dashs/group" + group + "/dashcubic" + frame + ".png");
        }

        private final Random random = new Random(1234567891L);
        private final List<DashCubic> cubics = new ArrayList<>();
        private final Map<UUID, Vec3> lastPositions = new HashMap<>();
        private float stateAnim = 0f;
        private ByteBufferBuilder trailAlloc = null;

        void reset() {
            this.cubics.clear();
            this.lastPositions.clear();
            this.stateAnim = 0f;
            if (this.trailAlloc != null) { this.trailAlloc.close(); this.trailAlloc = null; }
        }

        void onTick() {
            if (mc.player == null || mc.level == null) return;
            for (Player player : mc.level.getPlayers()) {
                if (player == null) continue;
                UUID id = player.getUuid();
                Vec3 cur = player.getEntityPos();
                Vec3 prev = this.lastPositions.get(id);
                this.lastPositions.put(id, cur);
                if (prev == null || !shouldTrail(player, 25.0F)) continue;
                spawnForMovement(player, prev, cur);
            }
            this.lastPositions.keySet().removeIf(uuid -> mc.level.getPlayerByUuid(uuid) == null);
            updateCubics();
        }

        private void spawnForMovement(Player entity, Vec3 prev, Vec3 cur) {
            double dx = cur.x - prev.x, dy = cur.y - prev.y, dz = cur.z - prev.z;
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double speedXZ = Math.sqrt(dx * dx + dz * dz);
            if (speedXZ < 0.08) return;
            int countMax = Mth.clamp((int) (speed / 0.08), 1, 16);
            int color = Color.getHSBColor(this.random.nextInt(361) / 360.0f, 1.0f, 1.0f).getRGB();
            for (int c = 0; c < countMax; c++) {
                this.cubics.add(new DashCubic(entity, prev, c / (float) countMax, color, this.random, new DashTexture(true, this.random)));
            }
        }

        private void updateCubics() {
            this.stateAnim = Math.min(1.0f, this.stateAnim + 0.08f);
            for (DashCubic c : this.cubics) {
                if (c.getTimePC() >= 1.0f && c.alphaTo != 0.0f) c.alphaTo = 0.0f;
            }
            this.cubics.removeIf(c -> c.getTimePC() >= 1.0f && c.alphaTo == 0.0f && c.alpha < 0.02f);
            for (DashCubic c : this.cubics) {
                c.alpha += (c.alphaTo - c.alpha) * 0.15f;
                if (c.alpha > 0.05f) c.motionProcess(this.random);
            }
        }

        void onRender(EventRender3D event) {
            if (mc.player == null) return;
            if (this.stateAnim < 0.05f) return;

            PoseStack matrices = event.getMatrixStack();
            float tickDelta = event.getTickDelta();
            Camera camera = mc.gameRenderer.getCamera();
            Vec3 camPos = camera.getCameraPos();

            List<DashCubic> filtered = new ArrayList<>();
            for (DashCubic c : this.cubics) if (c.alpha > 0.05f) filtered.add(c);
            if (filtered.isEmpty()) return;

            float alphaPC = this.stateAnim;

            if (this.trailAlloc == null) { this.trailAlloc = new ByteBufferBuilder(524288); }
            Immediate immediate = MultiBufferSource.immediate(this.trailAlloc);
            try {
                // Искры
                VertexConsumer lines = immediate.getBuffer(LINE_LAYER);
                Matrix4f baseMatrix = matrices.last().getPositionMatrix();
                for (DashCubic c : filtered) {
                    double rx = c.getRenderPosX(tickDelta) - camPos.x;
                    double ry = c.getRenderPosY(tickDelta) - camPos.y;
                    double rz = c.getRenderPosZ(tickDelta) - camPos.z;
                    for (DashSpark spark : c.sparks) {
                        double sx = spark.getRenderPosX(tickDelta);
                        double sy = spark.getRenderPosY(tickDelta);
                        double sz = spark.getRenderPosZ(tickDelta);
                        float aPC = (float) (spark.alphaPC() * c.alpha);
                        float wave = (float) easeInOutQuadWave(aPC);
                        int base = overlay(c.color, 0xFFFFFF, wave);
                        int srcAlpha = (c.color >>> 24) == 0 ? 255 : (c.color >>> 24);
                        int alpha = Mth.clamp((int) (srcAlpha * wave / 1.33333f), 0, 255);
                        float fa = Math.min(1.0f, alpha / 255.0f * alphaPC);
                        int aa = Mth.clamp((int) (fa * 255.0f), 0, 255);
                        if (aa <= 1) continue;
                        int rr = (base >> 16) & 0xFF, gg = (base >> 8) & 0xFF, bb = base & 0xFF;
                        lines.vertex(baseMatrix, (float) (sx + rx), (float) (sy + ry), (float) (sz + rz)).color(rr, gg, bb, aa).normal(0, 1, 0);
                        lines.vertex(baseMatrix, (float) (-sx + rx), (float) (-sy + ry), (float) (-sz + rz)).color(rr, gg, bb, aa).normal(0, 1, 0);
                    }
                }

                // Main-кубики (текстуры) — группируем по resolved-текстуре,
                // каждая текстура = свой RenderType (additive).
                Map<ResourceLocation, List<DashCubic>> byTex = new HashMap<>();
                for (DashCubic c : filtered) {
                    ResourceLocation tex = c.texture.resolve();
                    byTex.computeIfAbsent(tex, k -> new ArrayList<>()).add(c);
                }
                for (Map.Entry<ResourceLocation, List<DashCubic>> e : byTex.entrySet()) {
                    VertexConsumer buf = immediate.getBuffer(cubicLayer(e.getKey()));
                    for (DashCubic c : e.getValue()) {
                        c.drawMain(buf, matrices, camera, camPos, tickDelta, alphaPC);
                    }
                }

                // Bloom-кубики
                VertexConsumer bloom = immediate.getBuffer(BLOOM_LAYER);
                for (DashCubic c : filtered) {
                    c.appendBloom(bloom, matrices, camera, camPos, tickDelta, alphaPC);
                }
                immediate.draw();
            } finally {
                // reused across frames
            }
        }
    }

    private static final class DashCubic {
        final Player entity;
        double motionX, motionY, motionZ;
        double posX, posY, posZ;
        double prevPosX, prevPosY, prevPosZ;
        final int rMTime;
        final long startTime;
        final int color;
        final DashTexture texture;
        float alpha = 0f;
        float alphaTo = 1f;
        final float[] rotate = new float[]{0f, 0f};
        final List<DashSpark> sparks = new ArrayList<>();

        DashCubic(Player entity, Vec3 prevTickPos, float offsetTickPC, int color, Random random, DashTexture texture) {
            this.entity = entity;
            this.color = color;
            this.texture = texture;
            this.rMTime = (int) ((550 + random.nextInt(300)) * 0.75F);
            this.startTime = System.currentTimeMillis();
            this.motionX = entity.getX() - prevTickPos.x;
            this.motionY = entity.getY() - prevTickPos.y;
            this.motionZ = entity.getZ() - prevTickPos.z;
            double h = entity.getHeight();
            this.posX = prevTickPos.x - this.motionX * offsetTickPC + (-0.0875 + 0.175 * Math.random());
            this.posY = prevTickPos.y - this.motionY * offsetTickPC + (h / 3.0 + h / 4.0 * Math.random() * 0.7);
            this.posZ = prevTickPos.z - this.motionZ * offsetTickPC + (-0.0875 + 0.175 * Math.random());
            this.prevPosX = this.posX; this.prevPosY = this.posY; this.prevPosZ = this.posZ;
            this.motionX *= 0.04f; this.motionY *= 0.04f; this.motionZ *= 0.04f;
            updateRotation();
        }

        private int getMotionYaw() {
            int motionYaw = (int) Math.toDegrees(Math.atan2(this.motionZ, this.motionX) - 90.0);
            return motionYaw < 0 ? motionYaw + 360 : motionYaw;
        }

        private void updateRotation() {
            float viewX = mc.gameRenderer != null && mc.gameRenderer.getCamera() != null ? mc.gameRenderer.getCamera().getXRot() : 0f;
            if (Math.sqrt(motionX * motionX + motionZ * motionZ) < 5.0E-4) {
                rotate[0] = (float) (360.0 * Math.random());
                rotate[1] = viewX;
            } else {
                float motionYaw = getMotionYaw();
                rotate[0] = motionYaw - 45.0f - 15.0f;
                float yawDiff = Math.abs(Mth.wrapDegrees(this.entity.getYRot() - (motionYaw + 26.3f)));
                rotate[1] = (yawDiff < 10.0f || yawDiff > 160.0f) ? -90.0f : viewX;
            }
        }

        double getRenderPosX(float pt) { return prevPosX + (posX - prevPosX) * pt; }
        double getRenderPosY(float pt) { return prevPosY + (posY - prevPosY) * pt; }
        double getRenderPosZ(float pt) { return prevPosZ + (posZ - prevPosZ) * pt; }
        float getTimePC() { return Math.min((System.currentTimeMillis() - startTime) / (float) rMTime, 1.0f); }

        void motionProcess(Random random) {
            prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
            motionX /= 1.05; posX += 5.0 * motionX;
            motionY /= 1.05; posY += 5.0 * motionY / (motionY < 0.0 ? 1.0 : 3.5);
            motionZ /= 1.05; posZ += 5.0 * motionZ;
            updateRotation();
            if (getTimePC() < 0.3f && random.nextInt(12) > 5) {
                for (int i = 0; i < 2; i++) sparks.add(new DashSpark());
            }
            sparks.forEach(DashSpark::motionSparkProcess);
            if (!sparks.isEmpty()) sparks.removeIf(DashSpark::toRemove);
        }

        void drawMain(VertexConsumer buffer, PoseStack matrices, Camera camera, Vec3 camPos, float partialTicks, float alphaPC) {
            float aPC = this.alpha * alphaPC;
            float scale = 0.033f * aPC * 16.0f;
            double rx = getRenderPosX(partialTicks);
            double ry = getRenderPosY(partialTicks);
            double rz = getRenderPosZ(partialTicks);
            int col = overlay(this.color, 0xFFFFFF, 0.45f);
            int argb = opaque(col, alphaPC * this.alpha);
            int a = (argb >>> 24);
            if (a <= 1) return;
            int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
            float half = scale * 0.5f;
            matrices.pushPose();
            matrices.translate(rx - camPos.x, ry - camPos.y, rz - camPos.z);
            matrices.multiply(camera.getRotation());
            if (this.rotate[0] != 0.0f) {
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(this.rotate[0]));
            }
            Matrix4f m = matrices.last().getPositionMatrix();
            buffer.vertex(m, -half, -half, 0.0F).texture(0.0F, 1.0F).color(r, g, b, a);
            buffer.vertex(m, -half, half, 0.0F).texture(0.0F, 0.0F).color(r, g, b, a);
            buffer.vertex(m, half, half, 0.0F).texture(1.0F, 0.0F).color(r, g, b, a);
            buffer.vertex(m, half, -half, 0.0F).texture(1.0F, 1.0F).color(r, g, b, a);
            matrices.popPose();
        }

        private int opaque(int color, float alpha) {
            int a = Mth.clamp((int) (255 * Mth.clamp(alpha, 0.0f, 1.0f)), 0, 255);
            return (a << 24) | (color & 0xFFFFFF);
        }

        void appendBloom(VertexConsumer buffer, PoseStack matrices, Camera camera, Vec3 camPos, float partialTicks, float alphaPC) {
            float aPC = this.alpha * alphaPC;
            float scale = 0.033f * aPC * 16.0f;
            double rx = getRenderPosX(partialTicks);
            double ry = getRenderPosY(partialTicks);
            double rz = getRenderPosZ(partialTicks);
            float ext = (float) Math.sqrt(scale * scale + scale * scale);
            float timePcOf = 1.0f - getTimePC();
            int col = overlay(this.color, 0xFFFFFF, 0.15f);
            appendFacingQuad(buffer, matrices, camera, camPos, rx, ry, rz, ext / 1.75f, ext / 1.75f, withAlpha(col, 55.0f * aPC));
            float aMul = aPC;
            float ext2 = ext * (1.0f + 6.0f * timePcOf * aMul);
            appendFacingQuad(buffer, matrices, camera, camPos, rx, ry, rz, ext2 / 2.0f, ext2 / 2.0f, withAlpha(toDark(col, aMul / 4.0f), 90.0f * aMul));
        }
    }

    /** Текстура кубика: анимированная (группа кадров) или статичная (dashcubic1..21). */
    private static final class DashTexture {
        private final boolean animated;
        private final int group;
        private final int staticNum;
        private final long spawnTime;
        private final int animPerTime;

        DashTexture(boolean allowAnimated, Random random) {
            this.animated = allowAnimated && random.nextInt(100) > 40;
            if (this.animated) {
                this.group = random.nextInt(5);
                this.staticNum = 0;
                this.spawnTime = System.currentTimeMillis();
                this.animPerTime = Math.max(1, (int) ((550 + random.nextInt(300)) * 0.75F));
            } else {
                this.group = -1;
                this.staticNum = random.nextInt(21) + 1;
                this.spawnTime = 0L;
                this.animPerTime = 0;
            }
        }

        ResourceLocation resolve() {
            if (this.animated) {
                int frames = switch (this.group) {
                    case 0 -> 11;
                    case 1 -> 23;
                    case 2 -> 32;
                    case 3 -> 16;
                    default -> 32;
                };
                int elapsed = (int) (System.currentTimeMillis() - this.spawnTime);
                float timePC = (elapsed % this.animPerTime) / (float) this.animPerTime;
                int frame = (int) Mth.clamp(timePC * frames, 0, frames - 1) + 1;
                return DashTrail.groupTex(this.group + 1, frame);
            }
            return DashTrail.staticTex(this.staticNum);
        }
    }

    private static final class DashSpark {
        double posX, posY, posZ, prevPosX, prevPosY, prevPosZ;
        final double speed, radianYaw, radianPitch;
        final long startTime;

        DashSpark() {
            this.speed = Math.random() / 50.0;
            this.radianYaw = Math.random() * 360.0;
            this.radianPitch = -90.0 + Math.random() * 180.0;
            this.startTime = System.currentTimeMillis();
        }

        double timePC() { return Mth.clamp((System.currentTimeMillis() - startTime) / 1000.0f, 0.0f, 1.0f); }
        double alphaPC() { return 1.0 - timePC(); }
        boolean toRemove() { return timePC() >= 1.0; }

        void motionSparkProcess() {
            double radYaw = Math.toRadians(radianYaw);
            prevPosX = posX; prevPosY = posY; prevPosZ = posZ;
            posX += Math.sin(radYaw) * speed;
            posY += Math.cos(Math.toRadians(radianPitch - 90.0)) * speed;
            posZ += Math.cos(radYaw) * speed;
        }

        double getRenderPosX(float pt) { return prevPosX + (posX - prevPosX) * pt; }
        double getRenderPosY(float pt) { return prevPosY + (posY - prevPosY) * pt; }
        double getRenderPosZ(float pt) { return prevPosZ + (posZ - prevPosZ) * pt; }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIZZ TRAIL (Шипучка)
    // ═══════════════════════════════════════════════════════════════════════
    private static final class FizzTrail {
        private static final ResourceLocation BLOOM_TEX = ResourceLocation.of("vesence", "textures/targetesp/bloom.png");
        private static final long LIFETIME_MS = 3000L;
        private static final double WALK_THRESHOLD = 0.045;
        private static final float FRIEND_RANGE = 25.0F;

        private static final RenderPipeline TEX_PIPELINE = RenderPipelines.register(
                RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                        .withLocation(ResourceLocation.of("vesence", "pipeline/world/fizztrail_tex"))
                        .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                        .withCull(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withBlend(BlendFunction.LIGHTNING)
                        .build());
        private static final RenderType BLOOM_LAYER = RenderType.of("vesence_fizztrail_bloom",
                RenderSetup.builder(TEX_PIPELINE).expectedBufferSize(262144).translucent().texture("Sampler0", BLOOM_TEX).build());

        private final Random random = new Random();
        private final List<FizzParticle> particles = new ArrayList<>();
        private final Map<UUID, Vec3> lastPositions = new HashMap<>();
        private ByteBufferBuilder trailAlloc = null;

        void reset() {
            this.particles.clear();
            this.lastPositions.clear();
            if (this.trailAlloc != null) { this.trailAlloc.close(); this.trailAlloc = null; }
        }

        void onTick() {
            if (mc.player == null || mc.level == null) return;
            for (Player player : mc.level.getPlayers()) {
                if (player == null) continue;
                UUID id = player.getUuid();
                Vec3 cur = player.getEntityPos();
                Vec3 prev = this.lastPositions.get(id);
                this.lastPositions.put(id, cur);
                if (prev == null || !shouldTrail(player, FRIEND_RANGE)) continue;
                double dx = cur.x - prev.x, dz = cur.z - prev.z;
                double speedXZ = Math.sqrt(dx * dx + dz * dz);
                if (speedXZ < WALK_THRESHOLD || !player.onGround()) continue;
                spawnFromFeet(player, dx, dz, speedXZ);
            }
            this.lastPositions.keySet().removeIf(uuid -> mc.level.getPlayerByUuid(uuid) == null);

            long now = System.currentTimeMillis();
            Iterator<FizzParticle> it = this.particles.iterator();
            while (it.hasNext()) {
                FizzParticle p = it.next();
                if (now - p.startTime >= LIFETIME_MS) it.remove();
                else p.tick();
            }
        }

        private void spawnFromFeet(Player player, double dx, double dz, double speedXZ) {
            double invLen = 1.0 / speedXZ;
            double backX = -dx * invLen, backZ = -dz * invLen;
            int count = Mth.clamp((int) (speedXZ / WALK_THRESHOLD), 1, 5);
            double feetX = player.getX(), feetY = player.getY() + 0.05, feetZ = player.getZ();
            float width = player.getWidth();
            for (int i = 0; i < count; i++) {
                double ox = (this.random.nextDouble() - 0.5) * width;
                double oz = (this.random.nextDouble() - 0.5) * width;
                double spreadAngle = (this.random.nextDouble() - 0.5) * 0.9;
                double cos = Math.cos(spreadAngle), sin = Math.sin(spreadAngle);
                double dirX = backX * cos - backZ * sin;
                double dirZ = backX * sin + backZ * cos;
                double horizSpeed = (0.04 + this.random.nextDouble() * 0.06) + speedXZ * 0.5;
                double vx = dirX * horizSpeed, vz = dirZ * horizSpeed;
                double vy = 0.06 + this.random.nextDouble() * 0.08;
                this.particles.add(new FizzParticle(feetX + ox, feetY, feetZ + oz, vx, vy, vz,
                        0.18F + this.random.nextFloat() * 0.12F, this.random.nextLong()));
            }
        }

        void onRender(EventRender3D event) {
            if (mc.player == null || this.particles.isEmpty()) return;
            float tickDelta = event.getTickDelta();
            Camera camera = mc.gameRenderer.getCamera();
            Vec3 camPos = camera.getCameraPos();
            float camYaw = camera.getYRot(), camPitch = camera.getXRot();
            PoseStack matrices = event.getMatrixStack();

            int theme = themeColor();
            int tr = (theme >> 16) & 0xFF, tg = (theme >> 8) & 0xFF, tb = theme & 0xFF;
            long now = System.currentTimeMillis();

            if (this.trailAlloc == null) { this.trailAlloc = new ByteBufferBuilder(524288); }
            Immediate immediate = MultiBufferSource.immediate(this.trailAlloc);
            try {
                VertexConsumer buffer = immediate.getBuffer(BLOOM_LAYER);
                // Проход 1: внешний glow
                for (FizzParticle p : this.particles) {
                    float life = p.lifePC(now);
                    if (life >= 1.0F) continue;
                    float fade = 1.0F - life;
                    double x = p.lerpX(tickDelta) - camPos.x, y = p.lerpY(tickDelta) - camPos.y, z = p.lerpZ(tickDelta) - camPos.z;
                    float pulse = 0.8F + 0.2F * (float) Math.sin(now * 0.012 + p.seed);
                    float size = p.size * 2.1F * pulse * (0.4F + 0.6F * fade);
                    int alpha = (int) (150 * fade);
                    appendBillboard(buffer, matrices, camYaw, camPitch, x, y, z, size, tr, tg, tb, alpha);
                }
                // Проход 2: белая сердцевина
                for (FizzParticle p : this.particles) {
                    float life = p.lifePC(now);
                    if (life >= 1.0F) continue;
                    float fade = 1.0F - life;
                    double x = p.lerpX(tickDelta) - camPos.x, y = p.lerpY(tickDelta) - camPos.y, z = p.lerpZ(tickDelta) - camPos.z;
                    float pulse = 0.7F + 0.3F * (float) Math.sin(now * 0.02 + p.seed * 1.7);
                    float size = p.size * pulse * (0.3F + 0.7F * fade);
                    int alpha = (int) (230 * fade * pulse);
                    appendBillboard(buffer, matrices, camYaw, camPitch, x, y, z, size, 255, 255, 255, alpha);
                }
                immediate.draw();
            } finally {
                // reused across frames
            }
        }
    }

    private static final class FizzParticle {
        private static final double GRAVITY = 0.012, DRAG = 0.96, BOUNCE = 0.45;
        double x, y, z, prevX, prevY, prevZ, vx, vy, vz;
        final float size, seed;
        final long startTime;

        FizzParticle(double x, double y, double z, double vx, double vy, double vz, float size, long seed) {
            this.x = this.prevX = x; this.y = this.prevY = y; this.z = this.prevZ = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.size = size;
            this.seed = (seed & 0xFFFF) / 6553.6F;
            this.startTime = System.currentTimeMillis();
        }

        void tick() {
            this.prevX = x; this.prevY = y; this.prevZ = z;
            this.vy -= GRAVITY; this.vx *= DRAG; this.vz *= DRAG;
            double nextX = x + vx, nextY = y + vy, nextZ = z + vz;
            if (mc.level != null) {
                if (vy < 0.0 && !mc.level.getBlockState(BlockPos.ofFloored(x, nextY - 0.02, z)).isAir()) {
                    vy = -vy * BOUNCE; vx *= 0.7; vz *= 0.7; nextY = y;
                }
                if (!mc.level.getBlockState(BlockPos.ofFloored(nextX, y, z)).isAir()) { vx = -vx * 0.3; nextX = x; }
                if (!mc.level.getBlockState(BlockPos.ofFloored(x, y, nextZ)).isAir()) { vz = -vz * 0.3; nextZ = z; }
            }
            this.x = nextX; this.y = nextY; this.z = nextZ;
        }

        float lifePC(long now) { return Mth.clamp((now - startTime) / (float) LIFETIME_MS, 0.0F, 1.0F); }
        double lerpX(float pt) { return prevX + (x - prevX) * pt; }
        double lerpY(float pt) { return prevY + (y - prevY) * pt; }
        double lerpZ(float pt) { return prevZ + (z - prevZ) * pt; }

        private static final long LIFETIME_MS = FizzTrail.LIFETIME_MS;
    }

    // ═══ Shared helpers ══════════════════════════════════════════════════════
    private static void appendFacingQuad(VertexConsumer buffer, PoseStack matrices, Camera camera, Vec3 camPos,
                                         double x, double y, double z, float halfW, float halfH, int argb) {
        int a = (argb >>> 24);
        if (a <= 1) return;
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        matrices.pushPose();
        matrices.translate(x - camPos.x, y - camPos.y, z - camPos.z);
        matrices.multiply(camera.getRotation());
        Matrix4f m = matrices.last().getPositionMatrix();
        buffer.vertex(m, -halfW, -halfH, 0.0F).texture(0.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(m, -halfW, halfH, 0.0F).texture(0.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(m, halfW, halfH, 0.0F).texture(1.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(m, halfW, -halfH, 0.0F).texture(1.0F, 1.0F).color(r, g, b, a);
        matrices.popPose();
    }

    private static void appendBillboard(VertexConsumer buffer, PoseStack matrices, float camYaw, float camPitch,
                                        double x, double y, double z, float size, int r, int g, int b, int a) {
        if (a <= 1) return;
        float half = size * 0.5F;
        matrices.pushPose();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-camYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camPitch));
        Matrix4f m = matrices.last().getPositionMatrix();
        buffer.vertex(m, -half, -half, 0.0F).texture(0.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(m, -half, half, 0.0F).texture(0.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(m, half, half, 0.0F).texture(1.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(m, half, -half, 0.0F).texture(1.0F, 1.0F).color(r, g, b, a);
        matrices.popPose();
    }

    private static int overlay(int color, int over, float pc) {
        pc = Mth.clamp(pc, 0.0f, 1.0f);
        int r = (int) (((color >> 16) & 0xFF) + (((over >> 16) & 0xFF) - ((color >> 16) & 0xFF)) * pc);
        int g = (int) (((color >> 8) & 0xFF) + (((over >> 8) & 0xFF) - ((color >> 8) & 0xFF)) * pc);
        int b = (int) ((color & 0xFF) + ((over & 0xFF) - (color & 0xFF)) * pc);
        return (color & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int color, float alpha) {
        int a = Mth.clamp((int) alpha, 0, 255);
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static int toDark(int color, float factor) {
        factor = Mth.clamp(factor, 0.0f, 1.0f);
        int a = (color >>> 24);
        int r = (int) (((color >> 16) & 0xFF) * factor);
        int g = (int) (((color >> 8) & 0xFF) * factor);
        int b = (int) ((color & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static double easeInOutQuadWave(double x) {
        double half = x < 0.5 ? x * 2.0 : (1.0 - x) * 2.0;
        return half < 0.5 ? 2 * half * half : 1 - Math.pow(-2 * half + 2, 2) / 2;
    }
}
