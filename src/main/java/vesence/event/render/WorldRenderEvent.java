package vesence.event.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import vesence.event.Event;
import vesence.utils.render.world.WorldRenderer;

import java.util.Objects;

@Environment(EnvType.CLIENT)
public final class WorldRenderEvent extends Event {
   private final Minecraft client;
   private final GameRenderer gameRenderer;
   private final WorldRenderer levelRenderer;
   private final float frameDepth;

   public WorldRenderEvent(Minecraft client, GameRenderer gameRenderer, WorldRenderer levelRenderer, float frameDepth) {
      this.client = Objects.requireNonNull(client, "client");
      this.gameRenderer = Objects.requireNonNull(gameRenderer, "gameRenderer");
      this.levelRenderer = Objects.requireNonNull(levelRenderer, "levelRenderer");
      this.frameDepth = frameDepth;
   }

   public Minecraft client() {
      return this.client;
   }

   public GameRenderer gameRenderer() {
      return this.gameRenderer;
   }

   public WorldRenderer levelRenderer() {
      return this.levelRenderer;
   }

   public PoseStack matrixStack() {
      return this.levelRenderer.matrixStack();
   }

   public Matrix4f positionMatrix() {
      return this.levelRenderer.positionMatrix();
   }

   public Matrix4f projectionMatrix() {
      return this.levelRenderer.projectionMatrix();
   }

   public float frameDepth() {
      return this.frameDepth;
   }
}
