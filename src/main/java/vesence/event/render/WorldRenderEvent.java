package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import vesence.event.Event;
import vesence.utils.render.world.WorldRenderer;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class WorldRenderEvent extends Event {
   private final MinecraftClient client;
   private final GameRenderer gameRenderer;
   private final WorldRenderer worldRenderer;
   private final float frameDepth;

   public WorldRenderEvent(MinecraftClient client, GameRenderer gameRenderer, WorldRenderer worldRenderer, float frameDepth) {
      this.client = Objects.requireNonNull(client, "client");
      this.gameRenderer = Objects.requireNonNull(gameRenderer, "gameRenderer");
      this.worldRenderer = Objects.requireNonNull(worldRenderer, "worldRenderer");
      this.frameDepth = frameDepth;
   }

   public MinecraftClient client() {
      return this.client;
   }

   public GameRenderer gameRenderer() {
      return this.gameRenderer;
   }

   public WorldRenderer worldRenderer() {
      return this.worldRenderer;
   }

   public MatrixStack matrixStack() {
      return this.worldRenderer.matrixStack();
   }

   public Matrix4f positionMatrix() {
      return this.worldRenderer.positionMatrix();
   }

   public Matrix4f projectionMatrix() {
      return this.worldRenderer.projectionMatrix();
   }

   public float frameDepth() {
      return this.frameDepth;
   }
}
