package vesence.module.impl.visuals.pet;

import java.util.ArrayList;
import java.util.List;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;

/**
 * Эмулятор net.minecraft.client.renderer.model.ModelRenderer из 1.16.5 под
 * 1.21.11. Нужен для дракона (бокс-модель), т.к. harmony DragonModel строит
 * ~35 боксов через setRotationPoint/setTextureOffset/addBox/addChild с UV из
 * texture offset. Геометрия/UV бокса и порядок translateRotate воспроизводят
 * ModelRenderer/ModelBox/TexturedQuad 1.16.5 точь-в-точь (позиции делятся на 16,
 * повороты Z->Y->X). Рендер — в VertexConsumer (POSITION_TEXTURE_COLOR, QUADS).
 */
public class PetBox {

   public float rotationPointX;
   public float rotationPointY;
   public float rotationPointZ;
   public float rotateAngleX;
   public float rotateAngleY;
   public float rotateAngleZ;
   public boolean showModel = true;

   private final float texWidth;
   private final float texHeight;
   private int texOffX;
   private int texOffY;

   private final List<Cube> cubes = new ArrayList<>();
   private final List<PetBox> children = new ArrayList<>();

   public PetBox(float texWidth, float texHeight) {
      this.texWidth = texWidth;
      this.texHeight = texHeight;
   }

   public void setRotationPoint(float x, float y, float z) {
      this.rotationPointX = x;
      this.rotationPointY = y;
      this.rotationPointZ = z;
   }

   public PetBox setTextureOffset(int u, int v) {
      this.texOffX = u;
      this.texOffY = v;
      return this;
   }

   public PetBox addBox(float x, float y, float z, float width, float height, float depth, float delta) {
      this.cubes.add(new Cube(this.texOffX, this.texOffY, x, y, z, width, height, depth, delta, this.texWidth, this.texHeight));
      return this;
   }

   public void addChild(PetBox child) {
      this.children.add(child);
   }

   public void render(PoseStack matrices, VertexConsumer buffer) {
      if (!this.showModel) {
         return;
      }
      if (this.cubes.isEmpty() && this.children.isEmpty()) {
         return;
      }
      matrices.pushPose();
      this.translateRotate(matrices);
      Matrix4f matrix = matrices.last().pose();
      for (Cube cube : this.cubes) {
         cube.render(matrix, buffer);
      }
      for (PetBox child : this.children) {
         child.render(matrices, buffer);
      }
      matrices.popPose();
   }

   private void translateRotate(PoseStack matrices) {
      matrices.translate(this.rotationPointX / 16.0F, this.rotationPointY / 16.0F, this.rotationPointZ / 16.0F);
      if (this.rotateAngleZ != 0.0F) {
         matrices.rotate(Axis.ZP.rotation(this.rotateAngleZ));
      }
      if (this.rotateAngleY != 0.0F) {
         matrices.rotate(Axis.YP.rotation(this.rotateAngleY));
      }
      if (this.rotateAngleX != 0.0F) {
         matrices.rotate(Axis.XP.rotation(this.rotateAngleX));
      }
   }

   // Соответствует 1.16.5 ModelBox (mirror=false, дельта одинакова по осям).
   private static final class Cube {
      private final Quad[] quads = new Quad[6];

      private Cube(int texOffX, int texOffY, float x, float y, float z, float width, float height, float depth,
                   float delta, float texWidth, float texHeight) {
         float f = x + width;
         float f1 = y + height;
         float f2 = z + depth;
         x -= delta;
         y -= delta;
         z -= delta;
         f += delta;
         f1 += delta;
         f2 += delta;

         float[] v7 = {x, y, z};
         float[] v = {f, y, z};
         float[] v1 = {f, f1, z};
         float[] v2 = {x, f1, z};
         float[] v3 = {x, y, f2};
         float[] v4 = {f, y, f2};
         float[] v5 = {f, f1, f2};
         float[] v6 = {x, f1, f2};

         float f4 = texOffX;
         float f5 = texOffX + depth;
         float f6 = texOffX + depth + width;
         float f7 = texOffX + depth + width + width;
         float f8 = texOffX + depth + width + depth;
         float f9 = texOffX + depth + width + depth + width;
         float f10 = texOffY;
         float f11 = texOffY + depth;
         float f12 = texOffY + depth + height;

         this.quads[2] = new Quad(new float[][]{v4, v3, v7, v}, f5, f10, f6, f11, texWidth, texHeight);
         this.quads[3] = new Quad(new float[][]{v1, v2, v6, v5}, f6, f11, f7, f10, texWidth, texHeight);
         this.quads[1] = new Quad(new float[][]{v7, v3, v6, v2}, f4, f11, f5, f12, texWidth, texHeight);
         this.quads[4] = new Quad(new float[][]{v, v7, v2, v1}, f5, f11, f6, f12, texWidth, texHeight);
         this.quads[0] = new Quad(new float[][]{v4, v, v1, v5}, f6, f11, f8, f12, texWidth, texHeight);
         this.quads[5] = new Quad(new float[][]{v3, v4, v5, v6}, f8, f11, f9, f12, texWidth, texHeight);
      }

      private void render(Matrix4f matrix, VertexConsumer buffer) {
         for (Quad quad : this.quads) {
            for (int i = 0; i < 4; i++) {
               float[] p = quad.positions[i];
               buffer.addVertex(matrix, p[0] / 16.0F, p[1] / 16.0F, p[2] / 16.0F)
                     .setUv(quad.u[i], quad.v[i])
                     .setColor(255, 255, 255, 255);
            }
         }
      }
   }

   private static final class Quad {
      private final float[][] positions;
      private final float[] u = new float[4];
      private final float[] v = new float[4];

      private Quad(float[][] positions, float u1, float v1, float u2, float v2, float texWidth, float texHeight) {
         this.positions = positions;
         this.u[0] = u2 / texWidth;
         this.v[0] = v1 / texHeight;
         this.u[1] = u1 / texWidth;
         this.v[1] = v1 / texHeight;
         this.u[2] = u1 / texWidth;
         this.v[2] = v2 / texHeight;
         this.u[3] = u2 / texWidth;
         this.v[3] = v2 / texHeight;
      }
   }
}
