package vesence.module.impl.visuals.attributes;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import vesence.module.impl.visuals.Attributes;
import vesence.utils.render.ColorUtil;

/** China hat - 1:1 port of Minced ChinaHat (EventLayerHead, model space). */
public final class AttributesChinaHat {

    private static final float OFFSET_BARE = 0.42F;
    private static final float OFFSET_HELMET = 0.50F;
    private static final float ROT_Z_DEGREES = 180.0F;
    private static final float ROT_Y_DEGREES = 90.0F;
    private static final float TOP_ALPHA = 0.5F;
    private static final float RIM_Y = -0.01F;
    private static final float RIM_THICKNESS = 0.01F;
    private static final float RIM_FLARE = 0.01F;
    private static final float SEG_STEP = 0.06981317F;
    private static final float CONE_APEX_HEIGHT = 0.3F;
    private static final float RIM_ALPHA = 0.5F;

    private static volatile RenderType coneLayer;
    private static volatile RenderType rimLayer;

    private final Attributes parent;

    public AttributesChinaHat(Attributes parent) {
        this.parent = parent;
    }

    public void onRender3D(vesence.event.render.EventRender3D event) {
    }

    public void deactivate() {
    }

    public void renderOnHead(Player player, PoseStack matrices, BipedEntityModel<?> model,
                             OrderedRenderCommandQueue queue) {
        Minecraft client = Minecraft.getInstance();
        if (player == null || model == null || client.level == null || client.player == null) return;
        boolean local = player == client.player;
        boolean friend = vesence.utils.friends.FriendStorage.isFriend(player.getName().getString());
        if (!local && (!friend || !parent.showChinaHatOnFriends())) return;

        ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
        float offset = helmet.isEmpty() ? OFFSET_BARE : OFFSET_HELMET;
        float width = (float) (player.getBoundingBox().maxX - player.getBoundingBox().minX);
        int theme = this.parent.resolveThemeColor();
        // Entity models are shared and their parts are mutated for every rendered entity.
        // submitCustom is executed later, so retaining ModelPart here makes the hat use
        // whichever head pose was written last and causes it to slide away from its owner.
        HeadTransform head = HeadTransform.capture(model.head);

        queue.submitCustom(matrices, coneLayer(), (entry, consumer) -> {
            applyHead(entry, head);
            entry.translate(0.0F, -offset, 0.0F);
            entry.rotate(RotationAxis.NEGATIVE_Z.rotationDegrees(ROT_Z_DEGREES));
            entry.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(ROT_Y_DEGREES));
            drawCone(entry.getPositionMatrix(), consumer, theme, width);
        });

        queue.submitCustom(matrices, rimLayer(), (entry, consumer) -> {
            applyHead(entry, head);
            entry.translate(0.0F, -offset, 0.0F);
            entry.rotate(RotationAxis.NEGATIVE_Z.rotationDegrees(ROT_Z_DEGREES));
            entry.rotate(RotationAxis.POSITIVE_Y.rotationDegrees(ROT_Y_DEGREES));
            drawRim(entry.getPositionMatrix(), consumer, theme, width);
        });
    }

    private static void drawCone(Matrix4f matrix, VertexConsumer consumer, int theme, float width) {
        int apex = theme;
        for (int i = 0; i <= 180; i++) {
            float angle = i * SEG_STEP;
            int fadeIndex = i * 8;
            int ring = ColorUtil.fade(3, fadeIndex, theme, ColorUtil.replAlpha(theme, TOP_ALPHA));
            int ringColor = ColorUtil.replAlpha(ring, ColorUtil.alpha(ring) / 255.0F * TOP_ALPHA);
            float x = (float) (Mth.sin(angle) * width);
            float z = (float) (Mth.cos(angle) * width);
            consumer.vertex(matrix, x, 0.0F, z).color(ringColor);
            consumer.vertex(matrix, 0.0F, CONE_APEX_HEIGHT, 0.0F).color(apex);
        }
    }

    private static void drawRim(Matrix4f matrix, VertexConsumer consumer, int theme, float width) {
        float yLow = RIM_Y;
        float yHigh = RIM_Y + RIM_THICKNESS;
        float outer = width + RIM_FLARE;
        float inner = width - RIM_FLARE;

        float prevOuterX = (float) (Mth.sin(0.0F) * outer);
        float prevOuterZ = (float) (Mth.cos(0.0F) * outer);
        float prevInnerX = (float) (Mth.sin(0.0F) * inner);
        float prevInnerZ = (float) (Mth.cos(0.0F) * inner);

        for (int i = 1; i <= 181; i++) {
            float angle = i * SEG_STEP;
            int fadeIndex = (i - 1) * 8;
            int color = ColorUtil.fade(3, fadeIndex, theme, ColorUtil.replAlpha(theme, RIM_ALPHA));
            float outerX = (float) (Mth.sin(angle) * outer);
            float outerZ = (float) (Mth.cos(angle) * outer);
            float innerX = (float) (Mth.sin(angle) * inner);
            float innerZ = (float) (Mth.cos(angle) * inner);

            consumer.vertex(matrix, prevOuterX, yLow, prevOuterZ).color(color);
            consumer.vertex(matrix, prevOuterX, yHigh, prevOuterZ).color(color);
            consumer.vertex(matrix, outerX, yHigh, outerZ).color(color);
            consumer.vertex(matrix, outerX, yLow, outerZ).color(color);

            consumer.vertex(matrix, prevInnerX, yHigh, prevInnerZ).color(color);
            consumer.vertex(matrix, innerX, yHigh, innerZ).color(color);
            consumer.vertex(matrix, outerX, yHigh, outerZ).color(color);
            consumer.vertex(matrix, prevOuterX, yHigh, prevOuterZ).color(color);

            prevOuterX = outerX;
            prevOuterZ = outerZ;
            prevInnerX = innerX;
            prevInnerZ = innerZ;
        }
    }

    private static void applyHead(PoseStack.Entry entry, HeadTransform head) {
        entry.translate(head.originX / 16.0F, head.originY / 16.0F, head.originZ / 16.0F);
        if (head.pitch != 0.0F || head.yaw != 0.0F || head.roll != 0.0F) {
            entry.rotate(new Quaternionf().rotationZYX(head.roll, head.yaw, head.pitch));
        }
        if (head.xScale != 1.0F || head.yScale != 1.0F || head.zScale != 1.0F) {
            entry.scale(head.xScale, head.yScale, head.zScale);
        }
    }

    private record HeadTransform(float originX, float originY, float originZ,
                                 float pitch, float yaw, float roll,
                                 float xScale, float yScale, float zScale) {
        private static HeadTransform capture(ModelPart head) {
            return new HeadTransform(head.originX, head.originY, head.originZ,
                    head.pitch, head.yaw, head.roll,
                    head.xScale, head.yScale, head.zScale);
        }
    }

    private static RenderType coneLayer() {
        RenderType local = coneLayer;
        if (local == null) {
            synchronized (AttributesChinaHat.class) {
                local = coneLayer;
                if (local == null) {
                    local = RenderType.of("vesence_china_hat_cone",
                            RenderSetup.builder(hatPipeline(DrawMode.TRIANGLE_STRIP, "china_hat_cone"))
                                    .expectedBufferSize(8192).translucent().build());
                    coneLayer = local;
                }
            }
        }
        return local;
    }

    private static RenderType rimLayer() {
        RenderType local = rimLayer;
        if (local == null) {
            synchronized (AttributesChinaHat.class) {
                local = rimLayer;
                if (local == null) {
                    local = RenderType.of("vesence_china_hat_rim",
                            RenderSetup.builder(hatPipeline(DrawMode.QUADS, "china_hat_rim"))
                                    .expectedBufferSize(16384).translucent().build());
                    rimLayer = local;
                }
            }
        }
        return local;
    }

    private static RenderPipeline hatPipeline(DrawMode mode, String path) {
        return RenderPipelines.register(
                RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                        .withLocation(ResourceLocation.of("vesence", path))
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                        .withCull(false)
                        .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                        .withDepthWrite(true)
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .build());
    }
}
