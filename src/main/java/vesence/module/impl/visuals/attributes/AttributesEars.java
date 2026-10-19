package vesence.module.impl.visuals.attributes;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import vesence.event.render.EventRender3D;
import vesence.module.impl.visuals.Attributes;

/**
 * Ушки — облако мягких glow-биллбордов из {@code bloom.png}. Привязаны к голове
 * игрока: следуют за {@code headYaw} и {@code pitch}.
 *
 * <p>Порт косметики «Ушки» из клиента RelevantPremium, адаптированный под
 * render-API MC 1.21.11. Уши поворачиваются ВМЕСТЕ С ГОЛОВОЙ — вокруг шеи, а
 * не вокруг макушки. Использует общий bloom-RenderLayer из {@link AttributesTail}.
 */
public final class AttributesEars {

    private static final int EAR_POINTS = 7;
    private static final float EAR_OFFSET_X = 0.16f;
    private static final float EAR_HEIGHT = 0.30f;
    private static final float EAR_BASE = 0.10f;
    private static final float SIZE_BASE = 0.22f;
    private static final float SIZE_TIP = 0.10f;
    private static final int BASE_ALPHA = 70;

    /** Радиус головы (от шеи до макушки). У Steve голова 8×8×8 пикселей =
     *  0.5 блока. Половина = расстояние от центра головы до макушки = 0.25. */
    private static final float HEAD_RADIUS = 0.25f;
    /** Отступ от макушки до основания уха — чтобы уши «росли» над головой. */
    private static final float EAR_LIFT = 0.22f;
    /** Высота шеи у standing-pose. */
    private static final float NECK_Y_STANDING = 1.37f;
    /** Высота шеи у sneaking-pose. */
    private static final float NECK_Y_SNEAKING = 1.02f;

    private final Attributes parent;
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private BufferAllocator allocator;

    public AttributesEars(Attributes parent) {
        this.parent = parent;
    }

    public void deactivate() {}

    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null) return;
        float tickDelta = event.getTickDelta();
        Vec3d cameraPos = event.getCameraPos();
        MatrixStack matrices = event.getMatrixStack();

        if (this.allocator == null) this.allocator = new BufferAllocator(262144);
        Immediate immediate = VertexConsumerProvider.immediate(this.allocator);
        VertexConsumer buffer = immediate.getBuffer(AttributesTail.BLOOM_LAYER);

        boolean any = false;
        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRenderForPlayer(player)) continue;
            if (player == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            renderEars(player, tickDelta, matrices, cameraPos, buffer);
            any = true;
        }

        if (any) immediate.draw();
    }

    private boolean shouldRenderForPlayer(PlayerEntity player) {
        if (mc.player == null) return false;
        return player == mc.player;
    }

    private void renderEars(PlayerEntity player, float tickDelta, MatrixStack matrices, Vec3d cameraPos,
                            VertexConsumer buffer) {
        if (!player.isAlive() || player.isInvisible()) return;
        EntityPose pose = player.getPose();
        if (pose == EntityPose.SWIMMING || player.isInSwimmingPose() || player.isGliding()) return;

        Vec3d lerpPos = player.getLerpedPos(tickDelta);
        double playerX = lerpPos.x;
        double playerY = lerpPos.y;
        double playerZ = lerpPos.z;
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.lastHeadYaw, player.headYaw);
        float pitch   = player.getPitch(tickDelta);

        float neckY = player.isSneaking() ? NECK_Y_SNEAKING : NECK_Y_STANDING;

        int theme = parent.resolveThemeColor();
        int rT = (theme >> 16) & 0xFF;
        int gT = (theme >>  8) & 0xFF;
        int bT =  theme        & 0xFF;
        int rW = Math.min(255, rT + 90);
        int gW = Math.min(255, gT + 90);
        int bW = Math.min(255, bT + 90);

        // Twitch анимация (редкие подёргивания).
        float t = (System.currentTimeMillis() % 100000L) / 1000.0f;
        int eid = player.getId();
        float twitchL = MathHelper.sin(t * 1.7f + eid * 0.31f);
        float twitchR = MathHelper.sin(t * 1.5f + eid * 0.71f);
        float earTiltL = (twitchL > 0.92f) ? (twitchL - 0.92f) * 15.0f : 0.0f;
        float earTiltR = (twitchR > 0.92f) ? (twitchR - 0.92f) * 15.0f : 0.0f;

        float camYaw = mc.gameRenderer.getCamera().getYaw();
        float camPitch = mc.gameRenderer.getCamera().getPitch();

        // Левое ухо.
        renderEar(buffer, matrices, cameraPos, playerX, playerY, playerZ,
                headYaw, pitch, neckY,
                -EAR_OFFSET_X, +1.0f, earTiltL,
                rT, gT, bT, rW, gW, bW, camYaw, camPitch);
        // Правое ухо.
        renderEar(buffer, matrices, cameraPos, playerX, playerY, playerZ,
                headYaw, pitch, neckY,
                +EAR_OFFSET_X, -1.0f, earTiltR,
                rT, gT, bT, rW, gW, bW, camYaw, camPitch);
    }

    private void renderEar(VertexConsumer buffer, MatrixStack matrices, Vec3d cameraPos,
                           double playerX, double playerY, double playerZ,
                           float headYaw, float pitch, float neckY,
                           float earX, float outwardSign, float twitchTilt,
                           int rT, int gT, int bT, int rW, int gW, int bW,
                           float camYaw, float camPitch) {

        // Строим matrix ear-local → world. Pitch применяется на уровне ШЕИ:
        // подъём к точке поворота → rotateX(-pitch) → подъём к макушке →
        // сдвиг уха.
        Matrix4f m = new Matrix4f();
        m.translate((float) playerX, (float) playerY, (float) playerZ);
        m.rotateY((float) Math.toRadians(180.0f - headYaw));
        // Поднимаемся к шее (точка вращения головы).
        m.translate(0.0f, neckY, 0.0f);
        // Pitch головы — поворачиваем вокруг шеи, не макушки.
        m.rotateX((float) Math.toRadians(-pitch));
        // Поднимаемся к макушке (от шеи на радиус головы) + EAR_LIFT gap.
        m.translate(0.0f, HEAD_RADIUS + EAR_LIFT, 0.0f);
        // Сдвиг к месту крепления уха.
        m.translate(earX, 0.0f, 0.0f);
        // Базовый наклон уха наружу (V-образно) + twitch.
        m.rotateZ((float) Math.toRadians(14.0f * outwardSign + twitchTilt * outwardSign));

        Vector4f localPoint = new Vector4f();

        for (int i = 0; i < EAR_POINTS; i++) {
            float p = i / (float)(EAR_POINTS - 1);
            float localY = EAR_HEIGHT * p;
            float halfWidth = EAR_BASE * (1.0f - p);
            int colsAtHeight = (p > 0.85f) ? 1 : (p > 0.55f) ? 2 : 3;

            for (int c = 0; c < colsAtHeight; c++) {
                float localX;
                if (colsAtHeight == 1) {
                    localX = 0f;
                } else if (colsAtHeight == 2) {
                    localX = (c == 0 ? -halfWidth : halfWidth) * 0.5f;
                } else {
                    localX = (c == 0 ? -halfWidth : c == 1 ? 0f : halfWidth);
                }

                localPoint.set(localX, localY, 0f, 1f);
                localPoint.mul(m);

                float whiteMix = MathHelper.clamp((p - 0.65f) * 3.0f, 0.0f, 1.0f);
                int r = MathHelper.lerp(whiteMix, rT, rW);
                int g = MathHelper.lerp(whiteMix, gT, gW);
                int b = MathHelper.lerp(whiteMix, bT, bW);

                float size = MathHelper.lerp(p, SIZE_BASE, SIZE_TIP);
                int alpha = (int)(BASE_ALPHA * (1.0f - 0.30f * p));

                AttributesTail.drawBloomBillboard(buffer, matrices, cameraPos,
                        localPoint.x, localPoint.y, localPoint.z,
                        size * 1.50f, camYaw, camPitch, r, g, b, alpha);
                AttributesTail.drawBloomBillboard(buffer, matrices, cameraPos,
                        localPoint.x, localPoint.y, localPoint.z,
                        size * 1.00f, camYaw, camPitch, r, g, b, alpha);
                AttributesTail.drawBloomBillboard(buffer, matrices, cameraPos,
                        localPoint.x, localPoint.y, localPoint.z,
                        size * 0.55f, camYaw, camPitch, r, g, b, alpha);
            }
        }
    }
}
