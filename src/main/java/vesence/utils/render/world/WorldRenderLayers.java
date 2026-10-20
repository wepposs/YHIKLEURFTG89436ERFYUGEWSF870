package vesence.utils.render.world;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;

@Environment(EnvType.CLIENT)
public final class WorldRenderLayers {
      private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
      private static final int LINE_BUFFER_SIZE_BYTES = 256;
      private static final String PIPELINE_NAMESPACE = "vesence";
      private static final DepthStencilState LEQUAL_DEPTH = new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true);
      private static final DepthStencilState NO_DEPTH = new DepthStencilState(CompareOp.ALWAYS_PASS, false);
      private static final RenderPipeline POSITION_COLOR_QUADS_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/position_color_quads"))
                              .withVertexShader("core/position_color")
                              .withFragmentShader("core/position_color")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                              .withPrimitiveTopology(PrimitiveTopology.QUADS)
                              .withCull(false)
                              .withDepthStencilState(LEQUAL_DEPTH)
                              .build());
      private static final RenderPipeline POSITION_COLOR_QUADS_NO_DEPTH_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/position_color_quads_no_depth"))
                              .withVertexShader("core/position_color")
                              .withFragmentShader("core/position_color")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                              .withPrimitiveTopology(PrimitiveTopology.QUADS)
                              .withCull(false)
                              .withDepthStencilState(NO_DEPTH)
                              .build());
      private static final RenderPipeline POSITION_COLOR_QUADS_NO_DEPTH_BLEND_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                              .withLocation(
                                          Identifier.fromNamespaceAndPath("vesence", "pipeline/world/position_color_quads_no_depth_blend"))
                              .withVertexShader("core/position_color")
                              .withFragmentShader("core/position_color")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                              .withPrimitiveTopology(PrimitiveTopology.QUADS)
                              .withCull(false)
                              .withDepthStencilState(NO_DEPTH)
                              .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                              .build());
      private static final RenderPipeline LINES_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.LINES_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/lines"))
                              .withVertexShader("core/rendertype_lines")
                              .withFragmentShader("core/rendertype_lines")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                              .withPrimitiveTopology(PrimitiveTopology.LINES)
                              .withDepthStencilState(LEQUAL_DEPTH)
                              .build());
      private static final RenderPipeline LINES_NO_DEPTH_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.LINES_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/lines_no_depth"))
                              .withVertexShader("core/rendertype_lines")
                              .withFragmentShader("core/rendertype_lines")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH)
                              .withPrimitiveTopology(PrimitiveTopology.LINES)
                              .withDepthStencilState(NO_DEPTH)
                              .build());
      private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/textured_quads"))
                              .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
                              .withVertexShader("core/position_tex_color")
                              .withFragmentShader("core/position_tex_color")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                              .withPrimitiveTopology(PrimitiveTopology.QUADS)
                              .withCull(false)
                              .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                              .build());
      private static final RenderPipeline TEXTURED_QUADS_ADDITIVE_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/textured_quads_additive"))
                              .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
                              .withVertexShader("core/position_tex_color")
                              .withFragmentShader("core/position_tex_color")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                              .withPrimitiveTopology(PrimitiveTopology.QUADS)
                              .withCull(false)
                              .withDepthStencilState(NO_DEPTH)
                              .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                              .build());
      private static final RenderPipeline TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/textured_quads_no_depth_additive"))
                              .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
                              .withVertexShader("core/position_tex_color")
                              .withFragmentShader("core/position_tex_color")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                              .withPrimitiveTopology(PrimitiveTopology.QUADS)
                              .withCull(false)
                              .withDepthStencilState(NO_DEPTH)
                              .withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
                              .build());
      private static final RenderPipeline TEXTURED_QUADS_NO_DEPTH_PIPELINE = RenderPipelines.register(
                  RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                              .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/textured_quads_no_depth"))
                              .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER0)
                              .withVertexShader("core/position_tex_color")
                              .withFragmentShader("core/position_tex_color")
                              .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                              .withPrimitiveTopology(PrimitiveTopology.QUADS)
                              .withCull(false)
                              .withDepthStencilState(NO_DEPTH)
                              .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                              .build());
      private static final RenderType POSITION_COLOR_QUADS_LAYER = RenderType.create("vesence/world/position_color_quads", RenderSetup.builder(POSITION_COLOR_QUADS_PIPELINE).createRenderSetup());
      private static final RenderType POSITION_COLOR_QUADS_NO_DEPTH_LAYER = RenderType.create("vesence/world/position_color_quads_no_depth", RenderSetup.builder(POSITION_COLOR_QUADS_NO_DEPTH_PIPELINE).createRenderSetup());
      private static final RenderType POSITION_COLOR_QUADS_NO_DEPTH_BLEND_LAYER = RenderType.create("vesence/world/position_color_quads_no_depth_blend", RenderSetup.builder(POSITION_COLOR_QUADS_NO_DEPTH_BLEND_PIPELINE).createRenderSetup());
      private static final RenderType TEXTURED_QUADS_LAYER = RenderType.create("vesence/world/textured_quads", RenderSetup.builder(TEXTURED_QUADS_PIPELINE).createRenderSetup());
      private static final RenderType TEXTURED_QUADS_ADDITIVE_LAYER = RenderType.create("vesence/world/textured_quads_additive", RenderSetup.builder(TEXTURED_QUADS_ADDITIVE_PIPELINE).createRenderSetup());
      private static final RenderType TEXTURED_QUADS_NO_DEPTH_ADDITIVE_LAYER = RenderType.create("vesence/world/textured_quads_no_depth_additive", RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).createRenderSetup());
      private static final RenderType TEXTURED_QUADS_NO_DEPTH_LAYER = RenderType.create("vesence/world/textured_quads_no_depth", RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_PIPELINE).createRenderSetup());
      private static final Map<Double, RenderType> LINES_CACHE = new ConcurrentHashMap<>();
      private static final Map<Double, RenderType> LINES_NO_DEPTH_CACHE = new ConcurrentHashMap<>();

      private WorldRenderLayers() {
      }

      public static RenderType POSITION_COLOR_QUADS() {
            return POSITION_COLOR_QUADS_LAYER;
      }

      public static RenderType POSITION_COLOR_QUADS_NO_DEPTH() {
            return POSITION_COLOR_QUADS_NO_DEPTH_LAYER;
      }

      public static RenderType POSITION_COLOR_QUADS_NO_DEPTH_BLEND() {
            return POSITION_COLOR_QUADS_NO_DEPTH_BLEND_LAYER;
      }

      public static RenderType TEXTURED_QUADS() {
            return TEXTURED_QUADS_LAYER;
      }

      public static RenderType TEXTURED_QUADS_NO_DEPTH(Identifier texture) {
            return RenderType.create(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_PIPELINE).withTexture("Sampler0", texture).createRenderSetup());
      }

      public static RenderType TEXTURED_QUADS(Identifier texture) {
            return RenderType.create(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).withTexture("Sampler0", texture).createRenderSetup());
      }

      public static RenderType TEXTURED_QUADS_ADDITIVE(Identifier texture) {
            return RenderType.create(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_ADDITIVE_PIPELINE).withTexture("Sampler0", texture).createRenderSetup());
      }

      public static RenderType TEXTURED_QUADS_NO_DEPTH_ADDITIVE(Identifier texture) {
            return RenderType.create(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).withTexture("Sampler0", texture).createRenderSetup());
      }

      public static RenderType LINES(double width) {
            double normalizedWidth = normalizeWidth(width);
            return LINES_CACHE.computeIfAbsent(normalizedWidth,
                        value -> createLineLayer(value, "vesence/world/lines", LINES_PIPELINE));
      }

      public static RenderType LINES_NO_DEPTH(double width) {
            double normalizedWidth = normalizeWidth(width);
            return LINES_NO_DEPTH_CACHE.computeIfAbsent(normalizedWidth,
                        value -> createLineLayer(value, "vesence/world/lines_no_depth", LINES_NO_DEPTH_PIPELINE));
      }

      private static RenderType createLineLayer(double width, String baseName, RenderPipeline pipeline) {
            return RenderType.create(
                        baseName + "/" + (width == 0.0 ? "default" : Double.toHexString(width)),
                        RenderSetup.builder(pipeline)
                              .createRenderSetup());
      }

      public static RenderType withRenderPassSetup(RenderType renderLayer, Consumer<RenderPass> renderPassSetup) {
            Objects.requireNonNull(renderLayer, "renderLayer");
            MoreMultiPhase.cast(renderLayer).withRenderPassSetup(renderPassSetup);
            return renderLayer;
      }

      private static double normalizeWidth(double width) {
            if (!Double.isFinite(width)) {
                  throw new IllegalArgumentException("Line width must be finite.");
            } else if (width < 0.0) {
                  throw new IllegalArgumentException("Line width cannot be negative.");
            } else {
                  return width == 0.0 ? 0.0 : width;
            }
      }
}
