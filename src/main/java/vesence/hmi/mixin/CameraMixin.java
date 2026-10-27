package vesence.hmi.mixin;

import vesence.hmi.access.CameraAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Camera;
import com.mojang.math.Axis;
import org.joml.Quaternionf;
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
    private float xRot;
    @Shadow
    private float yRot;
    @Shadow
    @Final
    private static org.joml.Vector3fc FORWARDS;
    @Shadow
    @Final
    private static org.joml.Vector3fc UP;
    @Shadow
    @Final
    private static org.joml.Vector3fc LEFT;
    @Shadow
    @Final
    private Vector3f forwards;
    @Shadow
    @Final
    private Vector3f up;
    @Shadow
    @Final
    private Vector3f left;
    @Shadow
    private Vec3 position;
    /**
     * В 26.3 Camera кэширует матрицы вида (cachedViewRotMatrix/cachedViewRotProjMatrix)
     * и пересобирает их только когда взведён этот флаг (DIRTY_VIEW_ROT | DIRTY_VIEW_ROT_PROJ = 3).
     * Ванильный setRotation взводит его в конце; наш заменитель обязан делать то же,
     * иначе мир рендерится с замороженной матрицей вида (крутится только небо).
     */
    @Shadow
    private int matrixPropertiesDirty;
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
    protected abstract void setPosition(Vec3 var1);

    public void hMI5_0$applyRotation() {
        // Композиция вращений — умножение кватернионов (add был бы покомпонентным сложением).
        this.rotation.mul(Axis.XP.rotationDegrees(this.pitchM));
        this.rotation.mul(Axis.YP.rotationDegrees(this.yawM));
        this.rotation.mul(Axis.ZP.rotationDegrees(this.rollM));
        FORWARDS.rotate(this.rotation, this.forwards);
        UP.rotate(this.rotation, this.up);
        LEFT.rotate(this.rotation, this.left);
        this.matrixPropertiesDirty |= 3;
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
            this.xRot = pitch;
            this.yRot = yaw;
            this.rotation.rotationYXZ((float)Math.PI - (yaw + this.yawM) * ((float)Math.PI / 180), (-pitch + this.pitchM) * ((float)Math.PI / 180), this.rollM * ((float)Math.PI / 180));
            FORWARDS.rotate(this.rotation, this.forwards);
            UP.rotate(this.rotation, this.up);
            LEFT.rotate(this.rotation, this.left);
            // Как в ванили: пометить кэши матриц вида грязными (DIRTY_VIEW_ROT | DIRTY_VIEW_ROT_PROJ).
            this.matrixPropertiesDirty |= 3;
            ci.cancel();
        }
    }

    @Inject(method={"setPosition(DDD)V"}, at={@At(value="HEAD")}, cancellable=true)
    public void setRotationMixin(double x, double y, double z, CallbackInfo ci) {
        if (!vesence.module.impl.visuals.LivingHands.isEnabled()) {
            return;
        }
        if (!FabricLoader.getInstance().isModLoaded("do_a_barrel_roll")) {
            this.setPosition(new Vec3(x += (double)this.xM, y += (double)this.yM, z += (double)this.zM));
            ci.cancel();
        }
    }
}
