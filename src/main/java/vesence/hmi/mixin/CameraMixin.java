package vesence.hmi.mixin;

import vesence.hmi.access.CameraAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={Camera.class})
public abstract class CameraMixin
implements CameraAccessor {
    @Shadow
    @Final
    private Quaternionf rotation;
    @Shadow
    private float pitch;
    @Shadow
    private float yaw;
    @Shadow
    @Final
    private static Vector3f HORIZONTAL;
    @Shadow
    @Final
    private static Vector3f VERTICAL;
    @Shadow
    @Final
    private static Vector3f DIAGONAL;
    @Shadow
    @Final
    private Vector3f horizontalPlane;
    @Shadow
    @Final
    private Vector3f verticalPlane;
    @Shadow
    @Final
    private Vector3f diagonalPlane;
    @Shadow
    private Vec3d pos;
    @Unique
    private float pitchM = 0.0f;
    @Unique
    private float yawM = 0.0f;
    @Unique
    private float rollM = 0.0f;
    @Unique
    private float xM = 0.0f;
    @Unique
    private float yM = 0.0f;
    @Unique
    private float zM = 0.0f;

    @Shadow
    protected abstract void setPos(Vec3d var1);

    public void hMI5_0$applyRotation() {
        this.rotation.add((Quaternionfc)RotationAxis.POSITIVE_X.rotationDegrees(this.pitchM));
        this.rotation.add((Quaternionfc)RotationAxis.POSITIVE_Y.rotationDegrees(this.yawM));
        this.rotation.add((Quaternionfc)RotationAxis.POSITIVE_Z.rotationDegrees(this.rollM));
    }

    public void hMI5_0$setRotationValues(float pitch, float yaw, float roll) {
        this.pitchM = pitch;
        this.yawM = yaw;
        this.rollM = roll;
    }

    public void hMI5_0$setPosValues(float x, float y, float z) {
        this.xM = x;
        this.yM = y;
        this.zM = z;
    }

    @Inject(method={"setRotation"}, at={@At(value="HEAD")}, cancellable=true)
    public void setRotationMixin(float yaw, float pitch, CallbackInfo ci) {
        if (!vesence.module.impl.visuals.LivingHands.isEnabled()) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("do_a_barrel_roll")) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.rotation.rotationYXZ((float)Math.PI - (yaw + this.yawM) * ((float)Math.PI / 180), (-pitch + this.pitchM) * ((float)Math.PI / 180), this.rollM * ((float)Math.PI / 180));
            HORIZONTAL.rotate((Quaternionfc)this.rotation, this.horizontalPlane);
            VERTICAL.rotate((Quaternionfc)this.rotation, this.verticalPlane);
            DIAGONAL.rotate((Quaternionfc)this.rotation, this.diagonalPlane);
            ci.cancel();
        }
    }

    @Inject(method={"setPos(DDD)V"}, at={@At(value="HEAD")}, cancellable=true)
    public void setRotationMixin(double x, double y, double z, CallbackInfo ci) {
        if (!vesence.module.impl.visuals.LivingHands.isEnabled()) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("do_a_barrel_roll")) {
            this.setPos(new Vec3d(x += (double)this.xM, y += (double)this.yM, z += (double)this.zM));
            ci.cancel();
        }
    }
}

