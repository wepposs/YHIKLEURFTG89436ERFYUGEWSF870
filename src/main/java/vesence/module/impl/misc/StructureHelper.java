package vesence.module.impl.misc;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.Display;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Matrix4f;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.event.render.EventScreenPre;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.Mathf;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.Font;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@IModule(name = "StructureHelper", description = "Сканирует сундуки с таймерами, локально отсчитывает время и подсвечивает их 3D боксами", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class StructureHelper extends Module {

    private final BooleanSetting highlight = new BooleanSetting("Подсветка", true);
    private final BooleanSetting boxFill = new BooleanSetting("Заливка", true);
    private final SliderSetting fillAlpha = new SliderSetting("Прозрачность заливки", 0.25, 0.0, 1.0, 0.05);
    private final BooleanSetting showHologram = new BooleanSetting("Голограмма таймера", true);

    private static final int COLOR_READY   = 0xFF4CFF4C;
    private static final int COLOR_MEDIUM  = 0xFFFFD700;
    private static final int COLOR_LONG    = 0xFFFF4444;
    private static final int COLOR_UNKNOWN = 0xFFEEEEEE;

    private static final int THRESHOLD_READY = 90;

    private static final Pattern TIMER_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");

    private static final DepthStencilState SH_NO_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);
    private static final RenderPipeline SH_LINES_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
            .withLocation(Identifier.fromNamespaceAndPath("vesence", "structure_helper_lines"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
            .withCull(false)
            .withDepthStencilState(SH_NO_DEPTH)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build());
    private static final RenderPipeline SH_QUADS_PIPELINE = RenderPipelines.register(
        RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
            .withLocation(Identifier.fromNamespaceAndPath("vesence", "structure_helper_quads"))
            .withVertexShader("core/position_color")
            .withFragmentShader("core/position_color")
            .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withCull(false)
            .withDepthStencilState(SH_NO_DEPTH)
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .build());
    private static final RenderType SH_LINES_LAYER = RenderType.create("sh_lines",
        RenderSetup.builder(SH_LINES_PIPELINE).createRenderSetup());
    private static final RenderType SH_QUADS_LAYER = RenderType.create("sh_quads",
        RenderSetup.builder(SH_QUADS_PIPELINE).createRenderSetup());

    private static final class ChestEntry {
        int initialSeconds;
        long recordedAtMs;
        boolean seenHologram;

        ChestEntry(int initialSeconds, long recordedAtMs, boolean seenHologram) {
            this.initialSeconds = initialSeconds;
            this.recordedAtMs = recordedAtMs;
            this.seenHologram = seenHologram;
        }

        int currentTime() {
            long elapsed = (System.currentTimeMillis() - recordedAtMs) / 1000L;
            return initialSeconds - (int) elapsed;
        }
    }

    private final ConcurrentHashMap<BlockPos, ChestEntry> chestCache = new ConcurrentHashMap<>();
    private ByteBufferBuilder allocator = null;
    private boolean timersLoaded = false;

    public StructureHelper() {
        addSettings(highlight, boxFill, fillAlpha, showHologram);
    }

    private File getTimersFile() {
        return new File(Vesence.get.root, "structure_timers.json");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (!timersLoaded) {
            loadTimers();
            timersLoaded = true;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        saveTimers();
        if (allocator != null) {
            try { allocator.close(); } catch (Exception ignored) {}
            allocator = null;
        }
    }

    private void loadTimers() {
        chestCache.clear();
        File file = getTimersFile();
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, com.google.gson.JsonElement> e : root.entrySet()) {
                try {
                    String[] parts = e.getKey().split(",");
                    if (parts.length != 3) continue;
                    int x = Integer.parseInt(parts[0]);
                    int y = Integer.parseInt(parts[1]);
                    int z = Integer.parseInt(parts[2]);
                    JsonObject obj = e.getValue().getAsJsonObject();
                    int initial = obj.get("initial").getAsInt();
                    long recorded = obj.get("recorded").getAsLong();
                    boolean seen = obj.has("seen") && obj.get("seen").getAsBoolean();
                    chestCache.put(new BlockPos(x, y, z), new ChestEntry(initial, recorded, seen));
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    private void saveTimers() {
        File file = getTimersFile();
        try {
            file.getParentFile().mkdirs();
        } catch (Exception ignored) {}
        JsonObject root = new JsonObject();
        for (Map.Entry<BlockPos, ChestEntry> e : chestCache.entrySet()) {
            BlockPos p = e.getKey();
            ChestEntry en = e.getValue();
            JsonObject obj = new JsonObject();
            obj.addProperty("initial", en.initialSeconds);
            obj.addProperty("recorded", en.recordedAtMs);
            obj.addProperty("seen", en.seenHologram);
            root.add(p.getX() + "," + p.getY() + "," + p.getZ(), obj);
        }
        try (FileWriter writer = new FileWriter(file)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (Exception ignored) {}
    }

    @EventInit
    private void onRender3D(EventRender3D event) {
        if (mc.level == null || mc.player == null) return;

        if (!timersLoaded) {
            loadTimers();
            timersLoaded = true;
        }

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        PoseStack matrices = event.getMatrixStack();
        Matrix4f matrix = matrices.last().pose();

        if (allocator == null) {
            allocator = new ByteBufferBuilder(262144);
        }
        Immediate immediate = new Immediate(allocator);

        try {
            long now = System.currentTimeMillis();

            int playerChunkX = mc.player.chunkPosition().x();
            int playerChunkZ = mc.player.chunkPosition().z();

            int radius = mc.options.renderDistance().get();

            List<BlockPos> loadedChests = new ArrayList<>();
            for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
                for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                    LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false);
                    if (chunk == null) continue;

                    for (BlockEntity tile : chunk.getBlockEntities().values()) {
                        if (tile instanceof ChestBlockEntity || tile instanceof TrappedChestBlockEntity) {
                            loadedChests.add(tile.getBlockPos());
                        }
                    }
                }
            }

            for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
                int seconds;
                if (entity instanceof ArmorStand) {
                    seconds = parseTimerFromEntity(entity);
                } else if (entity instanceof Display.TextDisplay) {
                    seconds = parseTimerFromTextDisplay(entity);
                } else {
                    continue;
                }
                if (seconds < 0) continue;

                Vec3 entityPos = entity.getPosition(1.0f);
                BlockPos nearest = findNearestChest(loadedChests, entityPos);
                if (nearest != null) {

                    chestCache.put(nearest, new ChestEntry(seconds, now, true));
                }
            }

            for (BlockPos pos : loadedChests) {
                if (!chestCache.containsKey(pos)) {
                    chestCache.put(pos, new ChestEntry(0, now, false));
                }
            }

            if (highlight.get()) {
                for (BlockPos pos : loadedChests) {
                    ChestEntry en = chestCache.get(pos);
                    int color = getColorByEntry(en, true);
                    renderChestBox(immediate, matrix, pos, color, cameraPos);
                }
            }

            immediate.draw();
        } catch (Exception e) {
            if (allocator != null) { try { allocator.close(); } catch (Exception ignored) {} }
            allocator = null;
        }
    }

    @EventInit
    private void onRender2D(EventScreenPre event) {
        if (mc.level == null || mc.player == null) return;
        if (!showHologram.get()) return;
        if (chestCache.isEmpty()) return;

        Renderer2D renderer = event.renderer();
        FontObject font = FontRegistry.SF_MEDIUM;
        float fontScale = (float) Mathf.getScaleFactor();

        for (Map.Entry<BlockPos, ChestEntry> entry : chestCache.entrySet()) {
            BlockPos pos = entry.getKey();
            ChestEntry en = entry.getValue();

            boolean loaded = isChunkLoaded(pos);
            int color = getColorByEntry(en, loaded);

            String text;
            boolean ready = false;
            if (!loaded || !en.seenHologram) {
                text = "неизвестно";
            } else {
                int cur = en.currentTime();
                if (cur <= 0) {
                    text = "готов";
                    ready = true;
                } else {
                    int m = cur / 60;
                    int s = cur % 60;
                    text = String.format("%d:%02d", m, s);
                }
            }

            Vec3 holoPos = new Vec3(pos.getX() + 0.5, pos.getY() + 1.4, pos.getZ() + 0.5);
            Vec3 screen = Mathf.worldSpaceToScreenSpace(holoPos);
            if (screen.z <= 0 || screen.z >= 1) continue;
            if (!Float.isFinite((float) screen.x) || !Float.isFinite((float) screen.y)) continue;

            float scale = fontScale;
            float sx = (float) screen.x * scale;
            float sy = (float) screen.y * scale;

            Font.TextMetrics metrics = renderer.measureText(font, text, 25);
            float padH = 6f, padV = 3f;
            float tagW = metrics.width + padH * 2;
            float tagH = metrics.height + padV * 2;
            float tagX = sx - tagW / 2f;
            float tagY = sy - tagH / 2f;

            float alphaMul = 1f;
            if (ready) {
                double t = (Math.sin(System.currentTimeMillis() / 200.0) + 1.0) * 0.5;
                alphaMul = 0.5f + 0.5f * (float) t;
            }

            int bgAlpha = (int) (100 * alphaMul);
            renderer.blurSquircle(tagX, tagY, tagW, tagH, 5, 3, BorderRadius.all(6), 1);
            renderer.drawSquircle(tagX, tagY, tagW, tagH, 3, BorderRadius.all(6),
                    ColorUtil.getColor(0, 0, 0, bgAlpha));

            renderer.pushClipRect(tagX, tagY, tagW, tagH);
            int textAlpha = (int) (235 * alphaMul);
            int textColor = (color & 0x00FFFFFF) | (textAlpha << 24);
            renderer.text(font, sx - metrics.width / 2f, tagY + padV + metrics.height - 2,
                    25, text, textColor);
            renderer.popClipRect();
        }
    }

    private boolean isChunkLoaded(BlockPos pos) {
        if (mc.level == null) return false;
        return mc.level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, false) != null;
    }

    private int getColorByEntry(ChestEntry en, boolean loaded) {
        if (!loaded || en == null || !en.seenHologram) {
            return COLOR_UNKNOWN;
        }
        int cur = en.currentTime();
        if (cur <= 0) {
            return COLOR_READY;
        } else if (cur <= THRESHOLD_READY) {
            return COLOR_MEDIUM;
        } else {
            return COLOR_LONG;
        }
    }

    private int parseTimerFromEntity(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof ArmorStand)) return -1;
        return parseTimeString(stripFormatting(entity.getName().getString()));
    }

    private int parseTimerFromTextDisplay(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof Display.TextDisplay textDisplay)) return -1;
        try {
            return parseTimeString(stripFormatting(textDisplay.getText().getString()));
        } catch (Exception e) {
            return -1;
        }
    }

    private int parseTimeString(String text) {
        if (text == null || text.isEmpty()) return -1;
        Matcher matcher = TIMER_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                if (seconds >= 0 && seconds <= 59 && minutes >= 0 && minutes <= 99) {
                    return minutes * 60 + seconds;
                }
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private BlockPos findNearestChest(List<BlockPos> chests, Vec3 holoPos) {
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos pos : chests) {
            double dx = (pos.getX() + 0.5) - holoPos.x;
            double dz = (pos.getZ() + 0.5) - holoPos.z;
            double distSq = dx * dx + dz * dz;
            if (distSq <= 9.0 && distSq < minDist) {
                minDist = distSq;
                nearest = pos;
            }
        }
        return nearest;
    }

    // TODO(port26): MultiBufferSource was removed in 26.3; minimal immediate replacement.
    // draw() builds meshes but cannot submit them without a frame-graph RenderPass.
    private static final class Immediate {
        private final ByteBufferBuilder allocator;
        private final java.util.Map<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> buffers = new java.util.HashMap<>();
        Immediate(ByteBufferBuilder allocator) { this.allocator = allocator; }
        VertexConsumer getBuffer(RenderType layer) {
            return this.buffers.computeIfAbsent(layer, l -> new com.mojang.blaze3d.vertex.BufferBuilder(this.allocator, l.primitiveTopology(), l.format()));
        }
        void draw() {
            for (java.util.Map.Entry<RenderType, com.mojang.blaze3d.vertex.BufferBuilder> e : this.buffers.entrySet()) {
                com.mojang.blaze3d.vertex.MeshData mesh = e.getValue().build();
                if (mesh != null) mesh.close();
            }
            this.buffers.clear();
        }
    }

    private void renderChestBox(Immediate immediate, Matrix4f matrix, BlockPos pos, int color, Vec3 cameraPos) {
        double minX = pos.getX() - cameraPos.x;
        double minY = pos.getY() - cameraPos.y;
        double minZ = pos.getZ() - cameraPos.z;
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        if (boxFill.get()) {
            VertexConsumer quadBuf = immediate.getBuffer(SH_QUADS_LAYER);
            float fa = fillAlpha.get().floatValue();

            quadBuf.addVertex(matrix, (float)minX, (float)minY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)minY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)minY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)minX, (float)minY, (float)maxZ).setColor(r, g, b, fa);

            quadBuf.addVertex(matrix, (float)minX, (float)maxY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)minX, (float)maxY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)maxY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)maxY, (float)minZ).setColor(r, g, b, fa);

            quadBuf.addVertex(matrix, (float)minX, (float)minY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)minY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)maxY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)minX, (float)maxY, (float)maxZ).setColor(r, g, b, fa);

            quadBuf.addVertex(matrix, (float)minX, (float)minY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)minX, (float)maxY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)maxY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)minY, (float)minZ).setColor(r, g, b, fa);

            quadBuf.addVertex(matrix, (float)minX, (float)minY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)minX, (float)minY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)minX, (float)maxY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)minX, (float)maxY, (float)minZ).setColor(r, g, b, fa);

            quadBuf.addVertex(matrix, (float)maxX, (float)minY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)maxY, (float)minZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)maxY, (float)maxZ).setColor(r, g, b, fa);
            quadBuf.addVertex(matrix, (float)maxX, (float)minY, (float)maxZ).setColor(r, g, b, fa);
        }

        VertexConsumer lineBuf = immediate.getBuffer(SH_LINES_LAYER);
        float lineAlpha = 0.85f;
        float fMinX = (float) minX, fMinY = (float) minY, fMinZ = (float) minZ;
        float fMaxX = (float) maxX, fMaxY = (float) maxY, fMaxZ = (float) maxZ;

        drawLine3D(lineBuf, matrix, fMinX, fMinY, fMinZ, fMaxX, fMinY, fMinZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMaxX, fMinY, fMinZ, fMaxX, fMinY, fMaxZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMaxX, fMinY, fMaxZ, fMinX, fMinY, fMaxZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMinX, fMinY, fMaxZ, fMinX, fMinY, fMinZ, r, g, b, lineAlpha);

        drawLine3D(lineBuf, matrix, fMinX, fMaxY, fMinZ, fMaxX, fMaxY, fMinZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMaxX, fMaxY, fMinZ, fMaxX, fMaxY, fMaxZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMaxX, fMaxY, fMaxZ, fMinX, fMaxY, fMaxZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMinX, fMaxY, fMaxZ, fMinX, fMaxY, fMinZ, r, g, b, lineAlpha);

        drawLine3D(lineBuf, matrix, fMinX, fMinY, fMinZ, fMinX, fMaxY, fMinZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMaxX, fMinY, fMinZ, fMaxX, fMaxY, fMinZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMaxX, fMinY, fMaxZ, fMaxX, fMaxY, fMaxZ, r, g, b, lineAlpha);
        drawLine3D(lineBuf, matrix, fMinX, fMinY, fMaxZ, fMinX, fMaxY, fMaxZ, r, g, b, lineAlpha);
    }

    private static void drawLine3D(VertexConsumer buf, Matrix4f matrix,
                                   float x1, float y1, float z1, float x2, float y2, float z2,
                                   float r, float g, float b, float a) {
        buf.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
        buf.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
    }

    private static String stripFormatting(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
