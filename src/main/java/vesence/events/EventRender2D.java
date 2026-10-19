package vesence.events;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.vertex.PoseStack;
import vesence.event.Event;

@Environment(EnvType.CLIENT)
public class EventRender2D extends Event {
    private PoseStack matrixStack;
    private float partialTicks;
    private Type type;

    public EventRender2D(PoseStack matrixStack, float partialTicks) {
        this.matrixStack = matrixStack;
        this.partialTicks = partialTicks;
    }

    public PoseStack getMatrixStack() {
        return matrixStack;
    }

    public void setMatrixStack(PoseStack matrixStack) {
        this.matrixStack = matrixStack;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public void setPartialTicks(float partialTicks) {
        this.partialTicks = partialTicks;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public enum Type {
        PRE, POST, HIGH
    }
}
