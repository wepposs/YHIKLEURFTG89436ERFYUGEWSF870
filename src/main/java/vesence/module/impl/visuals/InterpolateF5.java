package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventRotation;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.impl.combat.auraComponent.rotationComponent.impl.FreeLookUtil;
import vesence.module.impl.player.FreeLook;

@IModule(
    name = "Cinematic Camera",
    description = "Плавная камера от ф5",
    category = Category.VISUALS,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class InterpolateF5 extends Module {

    private static final float SWITCH_ANIM_SPEED = 0.26F;
    private static final float DISTANCE_SPEED = 0.13F;
    private static final float ROTATION_SMOOTH = 0.28F;
    private static final float CAMERA_DISTANCE = 4.1F;
    private static final float SNEAK_OFFSET = 0.5F;
    private static final float JUMP_MULTIPLIER = 2.0F;
    private static final float ANIM_SPEED = 0.13F;

    private float currentDistance;
    private float prevDistance;
    private float currentYaw;
    private float prevYaw;
    private float currentPitch;
    private float prevPitch;
    private float heightOffset;
    private float prevHeightOffset;
    private boolean switchAnimating;
    private boolean wasThirdPerson;
    private boolean needsInit = true;

    public InterpolateF5() {
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) return;

        boolean isThirdPerson = !mc.options.getPerspective().isFirstPerson();

        if (isThirdPerson && !wasThirdPerson) initCamera(true);
        if (!isThirdPerson && wasThirdPerson) {
            needsInit = true;
            switchAnimating = false;
        }

        wasThirdPerson = isThirdPerson;
        if (isThirdPerson) updateCamera();
    }

    @EventInit
    public void onRotation(EventRotation event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.options.getPerspective().isFirstPerson()) return;

        event.setYaw(getInterpolatedYaw(event.getPartialTicks()));
        event.setPitch(getInterpolatedPitch(event.getPartialTicks()));
    }

    private void initCamera(boolean animateSwitch) {
        if (mc.player == null) return;

        currentYaw = prevYaw = getReferenceYaw();
        currentPitch = prevPitch = getReferencePitch();
        currentDistance = prevDistance = animateSwitch ? 0.0F : CAMERA_DISTANCE;
        heightOffset = prevHeightOffset = 0.0F;
        switchAnimating = animateSwitch;
        needsInit = false;
    }

    private void updateCamera() {
        if (mc.player == null) return;
        if (needsInit) {
            initCamera(true);
            return;
        }

        prevYaw = currentYaw;
        prevPitch = currentPitch;
        prevDistance = currentDistance;
        prevHeightOffset = heightOffset;

        float rotationSpeed = ROTATION_SMOOTH;

        currentYaw += MathHelper.wrapDegrees(getReferenceYaw() - currentYaw) * rotationSpeed;
        currentPitch = MathHelper.clamp(currentPitch + (getReferencePitch() - currentPitch) * rotationSpeed, -90.0F, 90.0F);

        float distanceSpeed = switchAnimating ? SWITCH_ANIM_SPEED : DISTANCE_SPEED;
        currentDistance += (CAMERA_DISTANCE - currentDistance) * distanceSpeed;
        if (switchAnimating && Math.abs(CAMERA_DISTANCE - currentDistance) <= 0.02F) {
            currentDistance = CAMERA_DISTANCE;
            switchAnimating = false;
        }

        float targetOffset = 0.0F;
        if (mc.player.isSneaking()) {
            targetOffset = -SNEAK_OFFSET;
        }
        if (!mc.player.isOnGround()) {
            targetOffset += (float) (-mc.player.getVelocity().y * JUMP_MULTIPLIER);
        }

        heightOffset += (targetOffset - heightOffset) * ANIM_SPEED;
    }

    public float getInterpolatedYaw(float partialTicks) {
        if (mc.player == null) return 0.0F;
        return prevYaw + (currentYaw - prevYaw) * partialTicks;
    }

    public float getInterpolatedPitch(float partialTicks) {
        if (mc.player == null) return 0.0F;
        return MathHelper.clamp(prevPitch + (currentPitch - prevPitch) * partialTicks, -90.0F, 90.0F);
    }

    public float getInterpolatedDistance(float partialTicks) {
        return prevDistance + (currentDistance - prevDistance) * partialTicks;
    }

    public float getInterpolatedHeightOffset(float partialTicks) {
        return prevHeightOffset + (heightOffset - prevHeightOffset) * partialTicks;
    }

    private float getReferenceYaw() {
        if (FreeLook.isActive()) {
            return FreeLook.getFreeYaw();
        }
        if (FreeLookUtil.active) {
            return FreeLookUtil.freeYaw;
        }
        return mc.player != null ? mc.player.getYaw() : 0.0F;
    }

    private float getReferencePitch() {
        if (FreeLook.isActive()) {
            return FreeLook.getFreePitch();
        }
        if (FreeLookUtil.active) {
            return FreeLookUtil.freePitch;
        }
        return mc.player != null ? mc.player.getPitch() : 0.0F;
    }

    @Override
    public void onEnable() {
        super.onEnable();
        needsInit = true;
        wasThirdPerson = false;

        if (mc.player != null && !mc.options.getPerspective().isFirstPerson()) {
            initCamera(true);
            wasThirdPerson = true;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        needsInit = true;
        heightOffset = 0.0F;
        prevHeightOffset = 0.0F;
    }

    public static InterpolateF5 getInstance() {
        return (InterpolateF5) Vesence.get.manager.getModule(InterpolateF5.class);
    }

    public static boolean isActive() {
        InterpolateF5 instance = getInstance();
        return instance != null && instance.enable;
    }
}
