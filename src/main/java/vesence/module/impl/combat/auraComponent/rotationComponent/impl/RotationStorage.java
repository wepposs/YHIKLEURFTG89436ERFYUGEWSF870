package vesence.module.impl.combat.auraComponent.rotationComponent.impl;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.impl.combat.auraComponent.rotationComponent.Component;

/**
 * Исторический дубликат {@link URotations}. Ни один профиль ротации его не
 * вызывает, но раньше он был зарегистрирован как обработчик {@code EventUpdate}
 * и мог перетирать уже применённую ротацию своим собственным шагом (без
 * per-axis cap и с другим GCD-снапом).
 *
 * <p>Теперь это тонкий делегат: вся логика живёт в {@link URotations},
 * а сам класс больше не регистрируется в {@code ComponentManager}.
 *
 * @deprecated используйте {@link URotations}
 */
@Deprecated
@Environment(EnvType.CLIENT)
public class RotationStorage extends Component {

    public static RotationStorage instance;

    public RotationStorage() {
        instance = this;
    }

    public static void update(Rotation target, float yawSpeed, float pitchSpeed, float yawReturnSpeed,
                              float pitchReturnSpeed, int timeout, int priority, boolean clientRotation) {
        URotations.update(target, yawSpeed, pitchSpeed, yawReturnSpeed, pitchReturnSpeed, timeout, priority, clientRotation);
    }

    public static void update(Rotation targetRotation, float turnSpeed, float returnSpeed, int timeout, int priority) {
        URotations.update(targetRotation, turnSpeed, turnSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }

    public static void update(Rotation targetRotation, float yawSpeed, float pitchSpeed, float returnSpeed,
                              int timeout, int priority) {
        URotations.update(targetRotation, yawSpeed, pitchSpeed, returnSpeed, returnSpeed, timeout, priority, false);
    }

    public boolean isRotating() {
        return URotations.isRotating();
    }

    public void stopRotation() {
        URotations.currentTask = URotations.RotationTask.IDLE;
        URotations.currentPriority = 0;
        FreeLookUtil.active = false;
    }

    @Environment(EnvType.CLIENT)
    public enum RotationTask {
        AIM,
        RESET,
        IDLE
    }
}