package vesence.hmi.script_wrappers;

import vesence.hmi.access.CameraAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;

public class C {
    public void setCamPos(double x, double y, double z) {
        Camera class_41842 = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (class_41842 instanceof CameraAccessor) {
            CameraAccessor camera = (CameraAccessor)class_41842;
            camera.hMI5_0$setPosValues((float)x, (float)y, (float)z);
        }
    }

    public void setCamRot(double x, double y, double z) {
        Camera class_41842 = MinecraftClient.getInstance().gameRenderer.getCamera();
        if (class_41842 instanceof CameraAccessor) {
            CameraAccessor camera = (CameraAccessor)class_41842;
            camera.hMI5_0$setRotationValues((float)x, (float)y, (float)z);
        }
    }
}

