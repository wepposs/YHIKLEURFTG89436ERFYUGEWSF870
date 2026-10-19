package vesence.hmi.access;

public interface LivingEntityAccessor {
    public float hMI5_0$getMainHandSwingProgress(float var1);

    public float hMI5_0$getOffHandSwingProgress(float var1);

    public boolean hMI5_0$getMHandEvent();

    public boolean hMI5_0$getOHandEvent();

    public boolean hMI5_0$getMInteract();

    public boolean hMI5_0$getOInteract();

    public boolean hMI5_0$getBlockBreak();

    public void hMI5_0$resetOffHandSwing(boolean var1);

    public void hMI5_0$resetMainHandSwing(boolean var1);

    public int hMI5_0$getSwingCount();
}

