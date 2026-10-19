package vesence.module.impl.visuals.pet;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.util.ResourceLocation;

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
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
         .withLocation(ResourceLocation.of("vesence", "pipeline/world/pet_mesh"))
         .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.TRIANGLES)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(true)
         .withBlend(BlendFunction.TRANSLUCENT)
         .build());

   private static final RenderPipeline BOX_PIPELINE = RenderPipelines.register(
      RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
         .withLocation(ResourceLocation.of("vesence", "pipeline/world/pet_box"))
         .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
         .withCull(false)
         .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
         .withDepthWrite(true)
         .withBlend(BlendFunction.TRANSLUCENT)
         .build());

   public static RenderType meshLayer(String name, ResourceLocation texture) {
      return RenderType.of(name, RenderSetup.builder(MESH_PIPELINE)
         .expectedBufferSize(262144).texture("Sampler0", texture).build());
   }

   public static RenderType boxLayer(String name, ResourceLocation texture) {
      return RenderType.of(name, RenderSetup.builder(BOX_PIPELINE)
         .expectedBufferSize(65536).texture("Sampler0", texture).build());
   }
}
