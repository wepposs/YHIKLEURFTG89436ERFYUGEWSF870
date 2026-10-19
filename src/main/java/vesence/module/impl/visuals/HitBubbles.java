package vesence.module.impl.visuals;

import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.AttackEvent;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.utils.render.ColorUtil;

/**
 * HitBubbles — порт из RelevantPremiumpp4.
 * Круг (bubble.png) появляется сбоку от цели при ударе, растёт и затухает.
 */
@IModule(name = "HitBubbles", description = "Круг при ударе игрока", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class HitBubbles extends Module {

    private static final long LIFE_MS = 1600L;
    private static final ResourceLocation BUBBLE_TEXTURE = ResourceLocation.of("vesence", "textures/hitbubble/bubble.png");

    private final CopyOnWriteArrayList<HitBubble> bubbles = new CopyOnWriteArrayList<>();
    private ByteBufferBuilder bubbleAllocator = null;

    private static final RenderPipeline BUBBLE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("vesence", "pipeline/world/hitbubbles"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());

    private static final RenderType BUBBLE_LAYER = RenderType.of(
            "vesence_hitbubbles",
            RenderSetup.builder(BUBBLE_PIPELINE).expectedBufferSize(4096).translucent()
                    .texture("Sampler0", BUBBLE_TEXTURE).build());

    public HitBubbles() {
    }

    @Override
    public void onDisable() {
        this.bubbles.clear();
        if (bubbleAllocator != null) { bubbleAllocator.close(); bubbleAllocator = null; }
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        long now = System.currentTimeMillis();
        this.bubbles.removeIf(b -> now - b.spawnTime >= LIFE_MS);
    }

    @EventInit
    public void onAttack(AttackEvent event) {
        if (event == null || event.getTarget() == null || mc.player == null) return;
        if (!(event.getTarget() instanceof LivingEntity living)) return;

        Vec3 sideDir = getHitSideDirection(living, mc.player.getEntityPos());
        Vec3 pos = getHitPosition(living, sideDir);
        float sideYaw = (float) Math.toDegrees(Math.atan2(sideDir.x, sideDir.z));
        this.bubbles.add(new HitBubble(pos, System.currentTimeMillis(), (float) (Math.random() * 360.0), sideYaw));
    }

    @EventInit
    public void onWorldRender(EventRender3D event) {
        if (this.bubbles.isEmpty() || mc.player == null) return;

        PoseStack stack = event.getMatrixStack();
        Vec3 cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        long now = System.currentTimeMillis();

        ByteBufferBuilder alloc = bubbleAllocator;
        if (alloc == null) {
            alloc = new ByteBufferBuilder(65536);
            bubbleAllocator = alloc;
        }
        MultiBufferSource.Immediate immediate = MultiBufferSource.immediate(alloc);
        try {
            VertexConsumer buffer = immediate.getBuffer(BUBBLE_LAYER);
            for (HitBubble bubble : this.bubbles) {
                renderSingleBubble(stack, buffer, cameraPos, bubble, now);
            }
            immediate.draw();
        } finally {
            // reused across frames
        }
    }

    private void renderSingleBubble(PoseStack stack, VertexConsumer buffer, Vec3 cameraPos, HitBubble bubble, long now) {
        float progress = (float) (now - bubble.spawnTime) / (float) LIFE_MS;
        if (progress >= 1.0F) return;

        float inPhase = Math.max(0.0F, Math.min(1.0F, progress / 0.22F));
        float outPhase = Math.max(0.0F, Math.min(1.0F, (progress - 0.225F) / 0.4F));
        float scaleIn = inPhase * inPhase * (3.0F - 2.0F * inPhase);
        float scaleOut = 1.0F - outPhase * outPhase;
        float scale = 0.02F + 1.55F * scaleIn * scaleOut;
        float alpha = 1.0F - outPhase * outPhase * outPhase;
        float rotation = (float) (now - bubble.spawnTime) / 1.5F + bubble.spinSeed;
        Vec3 rel = bubble.pos.subtract(cameraPos);
        int color = ColorUtil.multAlpha(ColorUtil.fade(), alpha);

        stack.pushPose();
        stack.translate(rel.x, rel.y, rel.z);
        stack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(bubble.sideYaw));
        stack.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-210.0F));
        stack.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation));
        drawTexturedQuad(stack, buffer, -scale * 0.5F, -scale * 0.5F, scale, scale, color);
        stack.popPose();
    }

    private void drawTexturedQuad(PoseStack stack, VertexConsumer buffer, float x, float y, float width, float height, int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        if (a <= 0) return;
        Matrix4f mat = stack.last().getPositionMatrix();
        buffer.vertex(mat, x, y, 0.0F).texture(0.0F, 0.0F).color(r, g, b, a);
        buffer.vertex(mat, x, y + height, 0.0F).texture(0.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(mat, x + width, y + height, 0.0F).texture(1.0F, 1.0F).color(r, g, b, a);
        buffer.vertex(mat, x + width, y, 0.0F).texture(1.0F, 0.0F).color(r, g, b, a);
    }

    private Vec3 getHitSideDirection(LivingEntity target, Vec3 attackerPos) {
        Vec3 dir = attackerPos.subtract(target.getEntityPos());
        dir = new Vec3(dir.x, 0.0, dir.z);
        if (dir.lengthSqr() < 1.0E-4) {
            Vec3 fallback = target.getRotationVector();
            dir = new Vec3(fallback.x, 0.0, fallback.z);
        }
        if (dir.lengthSqr() < 1.0E-4) {
            dir = new Vec3(0.0, 0.0, 1.0);
        }
        return dir.normalize();
    }

    private Vec3 getHitPosition(LivingEntity target, Vec3 sideDir) {
        Vec3 head = new Vec3(target.getX(), target.getY() + target.getHeight() + 0.18, target.getZ());
        return head.add(sideDir.multiply(0.1));
    }

    private record HitBubble(Vec3 pos, long spawnTime, float spinSeed, float sideYaw) {
    }
}
