package vesence.module.impl.combat.rotation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.auraComponent.UBoxPoints;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.Rotation;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.URotations;
import vesence.utils.other.Mathf;

import static vesence.utils.other.IMinecraft.mc;

@Environment(EnvType.CLIENT)
public class UFastRotation {
    private static float basePitch = 0.0f;

    public static void rotation(LivingEntity target, boolean attack) {
        Vec3 point = UBoxPoints.getBestVector3dOnEntityBox(target).subtract(mc.player.getEyePosition());

        float yaw = (float) Math.toDegrees(Math.atan2(-point.x, point.z));
        basePitch = (float) Mth.clamp(
                -Math.toDegrees(Math.atan2(point.y, Math.hypot(point.x, point.z))),
                -90.0, 90.0
        );


        float randomXY = Mathf.random(-3.0F, 3.0F)
                + (float) (3.0 * Math.cos(System.currentTimeMillis() / 40.0));
        float randomX = Mathf.random(-1.0F, 1.0F)
                + (float) (6.0 * Math.sin(System.currentTimeMillis() / 240.0));

        // ТЗ: в окне атаки ротация чистая — мгновенный снап палится на 100%,
        // jitter на тике удара это та же сигнатура.
        if (URotationMath.inAttackWindow()) {
            randomXY = 0.0F;
            randomX = 0.0F;
        }

        Rotation newRotation = new Rotation(yaw + randomXY, basePitch + randomX);
        URotations.update(newRotation, Mathf.randomValue(330, 360), Mathf.randomValue(330, 360), 30.0F, 30.0F, 0, 15, false);
    }

    public static void state() {
        basePitch = mc.player.getXRot();
    }
}