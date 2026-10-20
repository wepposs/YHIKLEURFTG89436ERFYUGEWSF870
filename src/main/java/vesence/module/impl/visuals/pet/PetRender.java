package vesence.module.impl.visuals.pet;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import net.minecraft.client.renderer.BindGroupLayouts;

/**
 * Пайплайны/слои рендера питомцев под 1.21.11.
 *
 * harmony рисовал меши/боксы флэт-фуллбрайтом (POSITION_COLOR_TEX_LIGHTMAP,
 * color 255, lightmap 240) с alpha-test cutout, без cull, с depth-тестом и записью
 * глубины. Эквивалент в 1.21.11 — свой POSITION_TEXTURE_COLOR пайплайн (флэт,
 * фуллбрайт, без освещения) с LEQUAL depth-тестом + записью глубины (питомец
 * корректно перекрывается миром и сам себя), TRANSLUCENT blend (pixel-art =
 * cutout), без cull. Меш = TRIANGLES, бокс-модель (дракон) = QUADS.
 */
public final class PetRender {

   private PetRender() {
   }

   private static final RenderPipeline MESH_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/pet_mesh"))
         .withVertexShader("core/position_tex_color")
         .withFragmentShader("core/position_tex_color")
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)         .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
         .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
         .build());

   private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
         .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/pet_box"))
         .withVertexShader("core/position_tex_color")
         .withFragmentShader("core/position_tex_color")
         .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
         .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)         .withPrimitiveTopology(PrimitiveTopology.QUADS)
         .withCull(false)
         .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))
         .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
         .build());

   public static RenderType meshLayer(String name, Identifier texture) {
      return RenderType.create(name, RenderSetup.builder(MESH_PIPELINE)
         .withTexture("Sampler0", texture).createRenderSetup());
   }

   public static RenderType boxLayer(String name, Identifier texture) {
      return RenderType.create(name, RenderSetup.builder(BOX_PIPELINE)
         .withTexture("Sampler0", texture).createRenderSetup());
   }
}
