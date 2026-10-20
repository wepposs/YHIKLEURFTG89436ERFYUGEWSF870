package vesence.module.impl.visuals.particular.splashes;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.tags.FluidTags;
import net.minecraft.resources.Identifier;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import vesence.event.render.EventRender3D;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import vesence.module.impl.visuals.Immediate;
import net.minecraft.client.renderer.BindGroupLayouts;

/**
 * Собственный рендер "венца" всплеска воды с точной геометрией оригинала:
 * 4 вертикальные грани (ширина 2w, высота height, база строго на поверхности воды),
 * без кулинга (двусторонние). Это то, что штатная система частиц 1.21.11 сделать не даёт
 * (там только квадратные биллборды), поэтому крона рисуется здесь напрямую вершинами.
 */
public final class WaterCrownRenderer {
    private static final int FRAMES = 9;

    private static final RenderPipeline PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath("particular", "pipeline/water_crown"))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build());

    private static final Map<Identifier, RenderType> LAYERS = new HashMap<>();
    private static final List<Crown> CROWNS = new ArrayList<>();

    private static ByteBufferBuilder allocator;
    private static Immediate immediate;

    private WaterCrownRenderer() {
    }

    public static void spawn(ClientLevel level, double x, double y, double z, float width, float height, boolean foam) {
        float r;
        float g;
        float b;
        if (foam) {
            r = 1.0F;
            g = 1.0F;
            b = 1.0F;
        } else {
            Color color = new Color(BiomeColors.getAverageWaterColor(level, BlockPos.containing(x, y, z)));
            r = color.getRed() / 255.0F;
            g = color.getGreen() / 255.0F;
            b = color.getBlue() / 255.0F;
        }
        CROWNS.add(new Crown(x, y, z, width, height, foam, r, g, b));
    }

    public static void tick(ClientLevel level) {
        Iterator<Crown> it = CROWNS.iterator();
        while (it.hasNext()) {
            Crown c = it.next();
            if (++c.age >= c.lifetime
                    || !level.getFluidState(BlockPos.containing(c.x, c.y, c.z)).is(FluidTags.WATER)) {
                it.remove();
            }
        }
    }

    public static void clear() {
        CROWNS.clear();
    }

    public static void render(EventRender3D event) {
        if (CROWNS.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }

        try {
            if (allocator == null) {
                allocator = new ByteBufferBuilder(1048576);
                immediate = new Immediate();
            }

            PoseStack matrices = event.getMatrixStack();
            Vec3 cam = event.getCameraPos();
            float tickDelta = event.getTickDelta();
            Matrix4f matrix = matrices.last().pose();

            for (Crown c : CROWNS) {
                renderCrown(c, matrix, cam, tickDelta);
            }

            immediate.draw();
        } catch (Exception e) {
            if (allocator != null) {
                try {
                    allocator.close();
                } catch (Exception ignored) {
                }
            }
            allocator = null;
            immediate = null;
        }
    }

    private static void renderCrown(Crown c, Matrix4f matrix, Vec3 cam, float tickDelta) {
        float progress = (c.age + tickDelta) / c.lifetime;
        float w = c.width * (0.8F + 0.2F * progress);
        float h = c.height;

        float cx = (float) (c.x - cam.x);
        float baseY = (float) (c.y - cam.y);
        float cz = (float) (c.z - cam.z);
        float topY = baseY + h;

        int frame = 1 + Math.min(FRAMES - 1, (int) ((c.age + tickDelta) * FRAMES / c.lifetime));
        Identifier texture = Identifier.fromNamespaceAndPath("particular",
                "textures/particle/" + (c.foam ? "water_splash_foam_" : "water_splash_") + frame + ".png");
        VertexConsumer buffer = immediate.getBuffer(layerFor(texture));

        int r = (int) (c.rCol * 255.0F);
        int g = (int) (c.gCol * 255.0F);
        int b = (int) (c.bCol * 255.0F);

        float x0 = cx - w;
        float x1 = cx + w;
        float z0 = cz - w;
        float z1 = cz + w;

        // 4 вертикальные грани по периметру, база на поверхности (baseY), верх на topY.
        wall(buffer, matrix, x0, z0, x0, z1, baseY, topY, r, g, b);
        wall(buffer, matrix, x0, z1, x1, z1, baseY, topY, r, g, b);
        wall(buffer, matrix, x1, z1, x1, z0, baseY, topY, r, g, b);
        wall(buffer, matrix, x1, z0, x0, z0, baseY, topY, r, g, b);
    }

    private static void wall(VertexConsumer buffer, Matrix4f m, float ax, float az, float bx, float bz,
                             float baseY, float topY, int r, int g, int b) {
        buffer.addVertex(m, ax, baseY, az).setColor(r, g, b, 255).setUv(0.0F, 1.0F);
        buffer.addVertex(m, bx, baseY, bz).setColor(r, g, b, 255).setUv(1.0F, 1.0F);
        buffer.addVertex(m, bx, topY, bz).setColor(r, g, b, 255).setUv(1.0F, 0.0F);
        buffer.addVertex(m, ax, topY, az).setColor(r, g, b, 255).setUv(0.0F, 0.0F);
    }

    private static RenderType layerFor(Identifier texture) {
        return LAYERS.computeIfAbsent(texture, tex -> RenderType.create(tex.toString(),
                RenderSetup.builder(PIPELINE).withTexture("Sampler0", tex).createRenderSetup()));
    }

    private static final class Crown {
        private final double x;
        private final double y;
        private final double z;
        private final float width;
        private final float height;
        private final boolean foam;
        private final float rCol;
        private final float gCol;
        private final float bCol;
        private final int lifetime = 18;
        private int age;

        private Crown(double x, double y, double z, float width, float height, boolean foam, float rCol, float gCol, float bCol) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.height = height;
            this.foam = foam;
            this.rCol = rCol;
            this.gCol = gCol;
            this.bCol = bCol;
        }
    }
}
