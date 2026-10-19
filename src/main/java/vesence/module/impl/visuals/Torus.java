package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.network.packet.s2c.play.DamageTiltS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.TorusReflectionRenderer;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Полный порт Relevant {@code Torus} в vesence — визуально 1:1.
 * <p>
 * При ударе по сущности (пакет урона) в точке попадания появляется тор,
 * который раскрывается и затухает. Фрагментный шейдер делает скринспейс-
 * «отражение» сцены (3D simplex noise + reflect + эмуляция cube mapping),
 * как в оригинале — «жидкометаллический/хромовый» тор, отражающий искажённый
 * мир. Рендер через raw-GL {@link TorusReflectionRenderer}.
 */
@IModule(name = "Torus", description = "Рендерит тор с эффектом отражения при ударе", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Torus extends Module {

    private static final Minecraft MC = Minecraft.getInstance();

    private final BooleanSetting depth = new BooleanSetting("Глубина", false);
    private final SliderSetting frequency = new SliderSetting("Частота шума", 50.0, 0.0, 100.0, 1.0, false);
    private final SliderSetting lifetime = new SliderSetting("Время жизни", 1000.0, 500.0, 5000.0, 100.0, false);
    private final SliderSetting size = new SliderSetting("Размер", 0.5, 0.2, 2.0, 0.05, false);

    private final CopyOnWriteArrayList<ActiveTorus> toruses = new CopyOnWriteArrayList<>();

    public Torus() {
        this.addSettings(new Setting[]{depth, frequency, lifetime, size});
    }

    @Override
    public void onDisable() {
        this.toruses.clear();
        super.onDisable();
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE) return;
        if (MC.world == null || MC.player == null) return;

        Entity hitEntity = null;
        if (event.getPacket() instanceof EntityDamageS2CPacket packet) {
            hitEntity = MC.world.getEntityById(packet.entityId());
        } else if (event.getPacket() instanceof DamageTiltS2CPacket packet) {
            hitEntity = MC.world.getEntityById(packet.id());
        }

        if (hitEntity != null && hitEntity != MC.player) {
            Vec3 hitPos = (MC.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() == hitEntity)
                    ? ehr.getPos()
                    : hitEntity.getEyePosition();

            float[] rot = this.getRotations(hitPos);
            this.toruses.add(new ActiveTorus(hitPos, rot[0], rot[1],
                    lifetime.get().doubleValue(), size.get().floatValue()));
        }
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (MC.player == null || MC.world == null || MC.gameRenderer == null || this.toruses.isEmpty()) return;

        this.toruses.removeIf(ActiveTorus::isFinished);
        if (this.toruses.isEmpty()) return;

        PoseStack matrices = event.getMatrixStack();
        Vec3 camPos = event.getCameraPos();
        Matrix4f projection = event.getProjectionMatrix();
        float freq = frequency.get().floatValue() / 100.0F;

        TorusReflectionRenderer renderer = TorusReflectionRenderer.getInstance();
        if (!renderer.begin(depth.get(), projection, camPos.x, camPos.y, camPos.z, freq, 1.0F)) return;

        try {
            for (ActiveTorus torus : this.toruses) {
                torus.update();

                matrices.pushPose();
                matrices.translate(torus.pos.x - camPos.x, torus.pos.y - camPos.y, torus.pos.z - camPos.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-torus.yaw));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(torus.pitch));

                Matrix4f modelView = new Matrix4f(matrices.last().getPositionMatrix());
                renderer.renderTorus(modelView, torus.outerRadAnim.get(), torus.innerRadAnim.get());

                matrices.popPose();
            }
        } finally {
            renderer.end();
        }
    }

    /** Углы поворота (yaw, pitch) от игрока к точке — как PlayerUtility.getRotations. */
    private float[] getRotations(Vec3 target) {
        Vec3 eyes = MC.player.getEyePosition();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, dist)));
        return new float[]{yaw, pitch};
    }

    /**
     * Активный тор. Анимация раскрытия/затухания как в оригинале: внешний радиус
     * расширяется easeOut-кубик, тело тора (inner) сжимается easeIn-кубик.
     */
    private static final class ActiveTorus {
        private final Vec3 pos;
        private final float yaw;
        private final float pitch;
        private final long spawnTime;
        private final double lifetime;

        private final Animation2 outerRadAnim = new Animation2();
        private final Animation2 innerRadAnim = new Animation2();

        ActiveTorus(Vec3 pos, float yaw, float pitch, double lifetime, float size) {
            this.pos = pos;
            this.yaw = yaw;
            this.pitch = pitch;
            this.lifetime = lifetime;
            this.spawnTime = System.currentTimeMillis();

            this.outerRadAnim.set(0.6 * size);
            this.innerRadAnim.set(0.4 * size);

            double dur = lifetime / 1000.0;
            this.outerRadAnim.run(2.6 * size, dur, Easings.CUBIC_OUT);
            this.innerRadAnim.run(0.0, dur, Easings.CUBIC_IN);
        }

        void update() {
            this.outerRadAnim.update();
            this.innerRadAnim.update();
        }

        boolean isFinished() {
            return System.currentTimeMillis() - this.spawnTime > this.lifetime;
        }
    }
}
