package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.entity.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import net.minecraft.client.render.MultiBufferSource.Immediate;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.util.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;

@IModule(name = "Container Esp", description = "Подсветка контейнеров с эффектом свечения", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ContainerESP extends Module {

    private final BooleanSetting glowEffect = new BooleanSetting("Свечение", true);

    private static final ResourceLocation GLOW_TEXTURE_C = ResourceLocation.of("vesence", "textures/world/dashbloom.png");

    private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                .withLocation(ResourceLocation.of("vesence", "container_esp_box"))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.QUADS)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.LIGHTNING)
                .build());
    private static final RenderType BOX_LAYER = RenderType.of("container_esp_box", RenderSetup.builder(BOX_PIPELINE).expectedBufferSize(2048).translucent().build());

    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_COLOR_SNIPPET })
                .withLocation(ResourceLocation.of("vesence", "container_esp_line"))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, DrawMode.LINES)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.LIGHTNING)
                .build());
    private static final RenderType LINE_LAYER = RenderType.of("container_esp_line", RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1024).translucent().build());

    private static final RenderPipeline TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE = RenderPipelines.register(
          RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
                .withLocation(ResourceLocation.of("vesence", "container_esp_glow"))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                .withCull(false)
                .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                .withDepthWrite(false)
                .withBlend(BlendFunction.LIGHTNING)
                .build());
    private static final RenderType GLOW_LAYER = RenderType.of("container_esp_glow_layer", RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", GLOW_TEXTURE_C).build());

    private float smoothTopAlpha = -1.0f;
    private float smoothBottomAlpha = -1.0f;
    private ByteBufferBuilder allocator = null;
    private Immediate immediate = null;

    public ContainerESP() {
        addSettings(glowEffect);
    }

    @EventInit
    private void onRender3D(EventRender3D event) {
        if (mc.level == null || mc.player == null) return;

        updateAlphas();

        Vec3 cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        PoseStack matrices = event.getMatrixStack();

        if (allocator == null) {
            allocator = new ByteBufferBuilder(262144);
            immediate = MultiBufferSource.immediate(allocator);
        }

        try {
            int playerChunkX = mc.player.getChunkPos().x;
            int playerChunkZ = mc.player.getChunkPos().z;
            int radius = 8;

            for (int cx = playerChunkX - radius; cx <= playerChunkX + radius; cx++) {
                for (int cz = playerChunkZ - radius; cz <= playerChunkZ + radius; cz++) {
                    if (!mc.level.getChunkManager().isChunkLoaded(cx, cz)) continue;
                    WorldChunk chunk = mc.level.getChunkManager().getWorldChunk(cx, cz, false);
                    if (chunk == null) continue;

                    for (BlockEntity tile : chunk.getBlockEntities().values()) {
                        int color = getContainerColor(tile);
                        if (color == -1) continue;

                        renderContainerEffect(matrices, immediate, tile.getPos(), color, cameraPos);
                    }
                }
            }

            for (net.minecraft.entity.Entity entity : mc.level.getEntities()) {
                if (entity instanceof net.minecraft.entity.vehicle.ChestMinecartEntity) {
                    renderContainerEffect(matrices, immediate, entity.getBlockPos(), 0xFFF3AC52, cameraPos);
                }
            }

            immediate.draw();
        } catch (Exception e) {
            if (allocator != null) { try { allocator.close(); } catch (Exception ignored) {} }
            allocator = null;
            immediate = null;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (allocator != null) {
            try { allocator.close(); } catch (Exception ignored) {}
            allocator = null;
            immediate = null;
        }
    }

    @Override
    public void toggle() {
        this.enable = !this.enable;
        if (this.enable) {
            this.onEnable();
        } else {
            this.onDisable();
        }
        if (vesence.Vesence.get.configManager != null) {
            vesence.Vesence.get.configManager.autoSave();
        }
    }

    private void renderContainerEffect(PoseStack matrices, Immediate immediate, BlockPos pos, int color, Vec3 cameraPos) {
        double minX = pos.getX() - cameraPos.x;
        double minY = pos.getY() - cameraPos.y;
        double minZ = pos.getZ() - cameraPos.z;
        double maxX = minX + 1.0;
        double maxY = minY + 1.0;
        double maxZ = minZ + 1.0;

        AABB bb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        if (this.glowEffect.get()) {
            renderGlow(matrices, immediate, pos, color, cameraPos);
        }

        renderGradientBox(matrices, immediate, bb, color);
    }

    private int getContainerColor(BlockEntity tile) {
        if (tile instanceof ChestBlockEntity chest) {
            if (chest instanceof TrappedChestBlockEntity) {
                return 0xFF8F6D3E;
            }
            return 0xFFF3AC52;
        } else if (tile instanceof EnderChestBlockEntity) {
            return 0xFF5231EE;
        } else if (tile instanceof BarrelBlockEntity) {
            return 0xFFFAE13E;
        } else if (tile instanceof ShulkerBoxBlockEntity) {
            return 0xFFF67B7B;
        } else if (tile instanceof MobSpawnerBlockEntity) {
            return 0xFF70ECA6;
        }
        return -1;
    }

    private void updateAlphas() {
        double timeSec = System.currentTimeMillis() / 1000.0;
        float t = (float) ((Math.sin(timeSec * 2.0 * Math.PI * 0.35) + 1.0) * 0.5);
        int targetTop = (int) (255 * (1.0f - t) + 30 * t);
        int targetBottom = (int) (30 * (1.0f - t) + 255 * t);

        if (this.smoothTopAlpha < 0) {
            this.smoothTopAlpha = targetTop;
            this.smoothBottomAlpha = targetBottom;
        } else {
            this.smoothTopAlpha += (targetTop - this.smoothTopAlpha) * 0.06f;
            this.smoothBottomAlpha += (targetBottom - this.smoothBottomAlpha) * 0.06f;
        }
    }

    private void renderGradientBox(PoseStack matrices, Immediate immediate, AABB bb, int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float aT = this.smoothTopAlpha / 255.0f * 0.25f;
        float aB = this.smoothBottomAlpha / 255.0f * 0.25f;

        Matrix4f matrix = matrices.last().getPositionMatrix();
        VertexConsumer quadBuf = immediate.getBuffer(BOX_LAYER);

        float minX = (float) bb.minX;
        float minY = (float) bb.minY;
        float minZ = (float) bb.minZ;
        float maxX = (float) bb.maxX;
        float maxY = (float) bb.maxY;
        float maxZ = (float) bb.maxZ;

        quadBuf.vertex(matrix, minX, minY, minZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, maxX, minY, minZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, maxX, minY, maxZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, minX, minY, maxZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, minX, maxY, minZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, minX, maxY, maxZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, maxX, maxY, minZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, minX, minY, minZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, minX, maxY, minZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, maxX, maxY, minZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, maxX, minY, minZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, minX, minY, maxZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, maxX, minY, maxZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, minX, maxY, maxZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, minX, minY, minZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, minX, minY, maxZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, minX, maxY, maxZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, minX, maxY, minZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, maxX, minY, minZ).color(r, g, b, aB);
        quadBuf.vertex(matrix, maxX, maxY, minZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, aT);
        quadBuf.vertex(matrix, maxX, minY, maxZ).color(r, g, b, aB);
        VertexConsumer lineBuf = immediate.getBuffer(LINE_LAYER);
        float lineAlpha = 0.8f;
        drawLine(lineBuf, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, lineAlpha);
        drawLine(lineBuf, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, lineAlpha);
    }

    private void drawLine(VertexConsumer buf, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float a) {
        buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
        buf.vertex(matrix, x2, y2, z2).color(r, g, b, a);
    }

    private void renderGlow(PoseStack matrices, Immediate immediate, BlockPos blockPos, int color, Vec3 cameraPos) {
        double x = (blockPos.getX() + 0.5) - cameraPos.x;
        double y = (blockPos.getY() + 0.5) - cameraPos.y;
        double z = (blockPos.getZ() + 0.5) - cameraPos.z;

        matrices.pushPose();
        matrices.translate(x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-mc.gameRenderer.getCamera().getYRot()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(mc.gameRenderer.getCamera().getXRot()));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f));

        VertexConsumer bloomBuf = immediate.getBuffer(GLOW_LAYER);
        Matrix4f matrix = matrices.last().getPositionMatrix();

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        for (int i = 0; i < 3; i++) {
            float alpha = (0.2f - i * 0.05f);
            float size = 2.5f + i * 0.2f;
            float half = size / 2.0f;

            bloomBuf.vertex(matrix, -half, -half, 0.0f).color(r, g, b, alpha).texture(0.0f, 1.0f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
            bloomBuf.vertex(matrix, half, -half, 0.0f).color(r, g, b, alpha).texture(1.0f, 1.0f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
            bloomBuf.vertex(matrix, half, half, 0.0f).color(r, g, b, alpha).texture(1.0f, 0.0f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
            bloomBuf.vertex(matrix, -half, half, 0.0f).color(r, g, b, alpha).texture(0.0f, 0.0f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(15728880).normal(0.0f, 0.0f, 1.0f);
        }

        matrices.popPose();
    }
}
