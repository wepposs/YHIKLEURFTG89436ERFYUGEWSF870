package vesence.module.impl.visuals;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.resources.Identifier;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.Vesence;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.TriggerBot;
import vesence.renderengine.render.TorusReflectionRenderer;
import vesence.renderengine.utils.animation.Animation;
import vesence.renderengine.utils.animation.Direction;
import vesence.renderengine.utils.animation.impl.EaseInOutQuad;
import vesence.utils.other.Mathf;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.math.animation.anim.util.Animation2;
import vesence.utils.render.math.animation.anim.util.Easings;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import net.minecraft.client.renderer.BindGroupLayouts;

@IModule(name = "TargetESP", description = "Жозки таргет есп", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class TargetESP extends Module {
   public static ModeSetting typeTargetEsp = new ModeSetting("Режим", "Картинка", "Картинка", "Призраки", "Призраки 2", "Кольцо",
           "Кубики", "Кристаллы", "Кольцо 2",
           "Цепи", "Призрачные орбиты", "Райдер", "Циркуль", "Череп", "Пентограма 1", "Торус", "Ромб крутой");
   public static BooleanSetting throughWalls = new BooleanSetting("Сквозь стены", false);
   public static BooleanSetting torusDepth = new BooleanSetting("Глубина", false)
           .hidden(() -> !typeTargetEsp.is("Торус"));
   public static SliderSetting torusSize = new SliderSetting("Размер", 1.3, 0.5, 3.0, 0.05)
           .hidden(() -> !typeTargetEsp.is("Торус"));
   public static SliderSetting torusWidth = new SliderSetting("Ширина", 0.3, 0.05, 0.7, 0.01)
           .hidden(() -> !typeTargetEsp.is("Торус"));
   public static SliderSetting torusGlow = new SliderSetting("Свечение", 1.0, 0.0, 3.0, 0.05)
           .hidden(() -> !typeTargetEsp.is("Торус"));
   public static BooleanSetting hurtColor = new BooleanSetting("Окрашивание при ударе", true);
   public static SliderSetting rotateSpeed = new SliderSetting("Скорость вращения", 1.2, 0.2, 4.0, 0.05)
           .hidden(() -> !typeTargetEsp.is("Картинка") && !typeTargetEsp.is("Ромб крутой"));
   public static SliderSetting bmwGhostCount = new SliderSetting("Кол-во призраков", 3.0, 2.0, 5.0, 1.0)
           .hidden(() -> !typeTargetEsp.is("Райдер"));
   public static SliderSetting bmwGhostLife = new SliderSetting("Время жизни (мс)", 350.0, 150.0, 500.0, 25.0)
           .hidden(() -> !typeTargetEsp.is("Райдер"));
   public static SliderSetting bmwStrengthXZ = new SliderSetting("Цикл XZ", 2000.0, 1000.0, 5000.0, 100.0)
           .hidden(() -> !typeTargetEsp.is("Райдер"));
   public static SliderSetting bmwStrengthY = new SliderSetting("Цикл Y", 1700.0, 1000.0, 5000.0, 100.0)
           .hidden(() -> !typeTargetEsp.is("Райдер"));
   public static SliderSetting pentagramSpeed = new SliderSetting("Скорость пентограмы", 1.0, 0.1, 5.0, 0.1)
           .hidden(() -> !typeTargetEsp.is("Пентограма 1"));
   public static SliderSetting ringRadius = new SliderSetting("Радиус кольца", 0.5, 0.3, 1.5, 0.05)
           .hidden(() -> !typeTargetEsp.is("Кольцо"));
   public static SliderSetting ringSpeed = new SliderSetting("Скорость кольца", 1.0, 0.3, 3.0, 0.1)
           .hidden(() -> !typeTargetEsp.is("Кольцо"));
   public static BooleanSetting hitBubbleEffect = new BooleanSetting("Раскол таргета", false)
           .hidden(() -> !typeTargetEsp.is("Картинка"));
   public static ModeSetting hitBubbleColor = new ModeSetting("Цвет пузыря", "Клиент", "Радужный", "Клиент")
           .hidden(() -> !(typeTargetEsp.is("Картинка") && hitBubbleEffect.get()));
   public static BooleanSetting hitBubbleVoronoi = new BooleanSetting("Осколки", true)
           .hidden(() -> !(typeTargetEsp.is("Картинка") && hitBubbleEffect.get()));
   public static ModeSetting hitBubbleVoronoiPriority = new ModeSetting("Режим осколков", "Сбалансированное",
           "Производительность", "Сбалансированное", "Множество", "Ультра")
           .hidden(() -> !(typeTargetEsp.is("Картинка") && hitBubbleEffect.get() && hitBubbleVoronoi.get()));
   public static ModeSetting typeImage = new ModeSetting("Режим картинки", "Картинка 3", "Картинка 1", "Картинка 2", "Картинка 3", "Картинка 4")
           .hidden(() -> !typeTargetEsp.is("Картинка"));
   public static SliderSetting imageSize = new SliderSetting("Размер картинки", 1.15, 0.6, 2.5, 0.05)
           .hidden(() -> !typeTargetEsp.is("Картинка") && !typeTargetEsp.is("Ромб крутой"));
   public static ModeSetting typeCube = new ModeSetting("Режим кубиков", "Новый", "Новый", "Старый")
           .hidden(() -> !typeTargetEsp.is("Кубики"));
   private static final Identifier GLOW_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/world/glow.png");
   private static final Identifier GLOW_TEXTURE_C = Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloom.png");
   private static final Identifier CRYSTAL_GLOW_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/world/dashbloom.png");
   private static final Identifier CHAIN_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/chain.png");
   private static final Identifier ESP_BLOOM_TEXTURE = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/bloom.png");
   private static final Identifier PENTAGRAM_CIRCLE_1 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/circles1.png");
   private static final Identifier SKULL_0 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/skull_state_0.png");
   private static final Identifier SKULL_1 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/skull_state_1.png");
   private static final Identifier SKULL_2 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/skull_state_2.png");
   private static final float CHAINS_SCALE = 1.30F;
   private static final Identifier IMAGE_1 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/targetesp_1.png");
   private static final Identifier IMAGE_2 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/targetesp_2.png");
   private static final Identifier IMAGE_3 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/targetesp_3.png");
   private static final Identifier IMAGE_4 = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/targetesp_4.png");
   private static final Identifier IMAGE_SQUARE = Identifier.fromNamespaceAndPath("vesence", "textures/targetesp/square.png");
   public static Animation2 alpha = new Animation2();
   public static Animation2 size = new Animation2();
   private LivingEntity lastTarget = null;
   private ByteBufferBuilder targetEspAllocator = null;
   private ByteBufferBuilder targetEspAllocator2 = null;
   private ByteBufferBuilder targetEspAllocQuad = null;
   private ByteBufferBuilder targetEspAllocFill = null;
   private static long lastTime = 0L;
   private float animationNurik = 0.0F;
   private long currentTimeSpirits = 0L;
   private final ArrayList<TargetESP.OldCubeParticle> oldCubeParticles = new ArrayList<>();
   private static long oldCubeLastTime = System.currentTimeMillis();
   private static float oldCubeDeltaTime = 0.0F;
   private static final long OLD_CUBE_LIFE_TIME = 1000L;
   private static final int OLD_CUBE_PARTICLES_PER_SPAWN = 1;
   private static final float OLD_CUBE_SPAWN_INTERVAL = 0.02F;
   private static final int MAX_PARTICLES = 50;
   private float oldCubeSpawnAccumulator = 0.0F;

   private float spiritsRingAngle = 0.0F;
   private long spiritsRingLastTime = 0L;

   private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
   private static final int RING2_TOTAL_POINTS = 138;
   private static final int RING2_TAIL_SEGMENTS = 26;
   private static final float RING2_HEAD_SIZE = 0.25f;
   private static final float[] RING2_COS = new float[RING2_TOTAL_POINTS];
   private static final float[] RING2_SIN = new float[RING2_TOTAL_POINTS];
   private static final float[] RING2_TAIL_PROGRESS = new float[RING2_TAIL_SEGMENTS];
   private static final float[] RING2_TAIL_ALPHA = new float[RING2_TAIL_SEGMENTS];
   private static final float[] RING2_TAIL_SIZE = new float[RING2_TAIL_SEGMENTS];

   static {
      for (int i = 0; i < RING2_TOTAL_POINTS; i++) {
         double angleRadians = 2.0D * Math.PI * i / RING2_TOTAL_POINTS;
         RING2_COS[i] = (float) Math.cos(angleRadians);
         RING2_SIN[i] = (float) Math.sin(angleRadians);
      }
      for (int i = 0; i < RING2_TAIL_SEGMENTS; i++) {
         float progress = (i + 1.0F) / (RING2_TAIL_SEGMENTS + 1.0F);
         RING2_TAIL_PROGRESS[i] = progress;
         RING2_TAIL_ALPHA[i] = (1.0F - progress) * 0.15F;
         RING2_TAIL_SIZE[i] = 1.0F - progress * 0.9F;
      }
   }

   private final Animation2 ring2InterpolationAnimation = new Animation2();
   private float ring2Alpha;
   private float previousRing2Alpha;
   private float ring2Step;
   private final int[] ring2TailColors = new int[RING2_TAIL_SEGMENTS];
   private final float[] ring2TailYOffsetFactors = new float[RING2_TAIL_SEGMENTS];
   private static final float RING2_SPEED = 0.012F;
   private final Animation2 crystalAlphaAnimation = new Animation2();
   private final Animation2 hurtColorAnimation = new Animation2();

   private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/textured_quads"))
                   .withVertexShader("core/position_tex_color")
                   .withFragmentShader("core/position_tex_color")
                   .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.QUADS)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderPipeline TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/textured_quads"))
                   .withVertexShader("core/position_tex_color")
                   .withFragmentShader("core/position_tex_color")
                   .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.QUADS)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderPipeline RING_STRIP_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("minecraft", "rendertype_lequal_depth_test"))
                   .withVertexShader("core/position_color")
                   .withFragmentShader("core/position_color")
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_STRIP)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderPipeline RING_LINE_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("minecraft", "rendertype_lines"))
                   .withVertexShader("core/position_color")
                   .withFragmentShader("core/position_color")
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINE_STRIP)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderType RING_STRIP_LAYER = RenderType.create("night_ring_strip", RenderSetup.builder(RING_STRIP_PIPELINE).createRenderSetup());
   private static final RenderType RING_LINE_LAYER = RenderType.create("night_ring_line", RenderSetup.builder(RING_LINE_PIPELINE).createRenderSetup());
   private static final RenderPipeline COLOR_QUADS_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/color_quads"))
                   .withVertexShader("core/position_color")
                   .withFragmentShader("core/position_color")
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.QUADS)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderType COLOR_QUADS_LAYER = RenderType.create("night_color_quads", RenderSetup.builder(COLOR_QUADS_PIPELINE).createRenderSetup());
   private static final RenderPipeline COLOR_LINES_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("minecraft", "rendertype_lines"))
                   .withVertexShader("core/position_color")
                   .withFragmentShader("core/position_color")
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.LINES)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderPipeline CUBE_LINES_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "targetesp_cube_lines"))
                   .withVertexShader("core/position_color")
                   .withFragmentShader("core/position_color")
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.DEBUG_LINES)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderType CUBE_LINES_LAYER = RenderType.create("targetesp_cube_lines", RenderSetup.builder(CUBE_LINES_PIPELINE).createRenderSetup());

   // Прозрачный тор (стеклянное кольцо) — TRANSLUCENT блендинг.
   private static final RenderPipeline TORUS_BODY_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "targetesp_torus_body"))
                   .withVertexShader("core/position_color")
                   .withFragmentShader("core/position_color")
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.QUADS)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                   .build());
   private static final RenderType TORUS_BODY_LAYER = RenderType.create("targetesp_torus_body", RenderSetup.builder(TORUS_BODY_PIPELINE).createRenderSetup());


   private static final RenderPipeline CRYSTAL_TRIANGLES_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/crystal_triangles"))
                   .withVertexShader("core/position_color")
                   .withFragmentShader("core/position_color")
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderType CRYSTAL_TRIANGLES_LAYER = RenderType.create("vesence_crystal_triangles", RenderSetup.builder(CRYSTAL_TRIANGLES_PIPELINE).createRenderSetup());

   private static final RenderPipeline TEXTURED_QUADS_ADDITIVE_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/textured_quads_additive"))
                   .withVertexShader("core/position_tex_color")
                   .withFragmentShader("core/position_tex_color")
                   .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.QUADS)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());
   private static final RenderType CRYSTAL_GLOW_LAYER = RenderType.create(
           "vesence_crystal_glow",
           RenderSetup.builder(TEXTURED_QUADS_ADDITIVE_PIPELINE)
                   
                   
                   .withTexture("Sampler0", CRYSTAL_GLOW_TEXTURE)
                   .createRenderSetup());

   private static final RenderType RING2_GLOW_LAYER = RenderType.create(
           "vesence_ring2_glow",
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   
                   
                   .withTexture("Sampler0", GLOW_TEXTURE_C)
                   .createRenderSetup());
   private static final RenderType GLOW_NO_DEPTH_LAYER = RenderType.create(
           GLOW_TEXTURE_C.toString(),
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE).withTexture("Sampler0", GLOW_TEXTURE_C).createRenderSetup());
   private static final RenderType SPIRITS_GLOW_LAYER = RenderType.create(
           GLOW_TEXTURE.getNamespace(),
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   
                   
                   .withTexture("Sampler0", GLOW_TEXTURE)
                   .createRenderSetup());
   private static final RenderType SPIRITS_BLOOM_LAYER = RenderType.create(
           GLOW_TEXTURE_C.getNamespace(),
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   
                   
                   .withTexture("Sampler0", GLOW_TEXTURE_C)
                   .createRenderSetup());

   private static final RenderType CHAIN_LAYER = RenderType.create(
           "vesence_targetesp_chain",
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   .withTexture("Sampler0", CHAIN_TEXTURE).createRenderSetup());
   private static final RenderType PENTAGRAM_CIRCLE_LAYER = RenderType.create(
           "vesence_targetesp_pentagram",
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   .withTexture("Sampler0", PENTAGRAM_CIRCLE_1).createRenderSetup());
   private static final RenderType ESP_BLOOM_LAYER = RenderType.create(
           "vesence_targetesp_bloom",
           RenderSetup.builder(TEXTURED_QUADS_PIPELINE)
                   .withTexture("Sampler0", ESP_BLOOM_TEXTURE).createRenderSetup());
   private static final RenderType ESP_BLOOM_NO_DEPTH_LAYER = RenderType.create(
           "vesence_targetesp_bloom_nodepth",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", ESP_BLOOM_TEXTURE).createRenderSetup());
   private static final RenderType SKULL_0_LAYER = RenderType.create(
           "vesence_targetesp_skull0",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", SKULL_0).createRenderSetup());
   private static final RenderType SKULL_1_LAYER = RenderType.create(
           "vesence_targetesp_skull1",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", SKULL_1).createRenderSetup());
   private static final RenderType SKULL_2_LAYER = RenderType.create(
           "vesence_targetesp_skull2",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", SKULL_2).createRenderSetup());

   private RenderType skullLayer(int state) {
      return state == 0 ? SKULL_0_LAYER : (state == 1 ? SKULL_1_LAYER : SKULL_2_LAYER);
   }

   private static final RenderType IMAGE_1_LAYER = RenderType.create(           "vesence_targetesp_img1",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", IMAGE_1).createRenderSetup());
   private static final RenderType IMAGE_2_LAYER = RenderType.create(
           "vesence_targetesp_img2",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", IMAGE_2).createRenderSetup());
   private static final RenderType IMAGE_3_LAYER = RenderType.create(
           "vesence_targetesp_img3",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", IMAGE_3).createRenderSetup());
   private static final RenderType IMAGE_4_LAYER = RenderType.create(
           "vesence_targetesp_img4",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", IMAGE_4).createRenderSetup());
   private static final RenderType IMAGE_SQUARE_LAYER = RenderType.create(
           "vesence_targetesp_square",
           RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                   .withTexture("Sampler0", IMAGE_SQUARE).createRenderSetup());

   private RenderType imageLayer() {
      if (typeImage.is("Картинка 1")) return IMAGE_1_LAYER;
      if (typeImage.is("Картинка 2")) return IMAGE_2_LAYER;
      if (typeImage.is("Картинка 4")) return IMAGE_4_LAYER;
      return IMAGE_3_LAYER;
   }

   private Identifier imageTexture() {
      if (typeImage.is("Картинка 1")) return IMAGE_1;
      if (typeImage.is("Картинка 2")) return IMAGE_2;
      if (typeImage.is("Картинка 4")) return IMAGE_4;
      return IMAGE_3;
   }

   // Заливка осколков: additive-блендинг (LIGHTNING = SRC_ALPHA, ONE) как в
   // оригинале harmony. Это даёт яркие светящиеся осколки картинки таргета.
   private static final RenderPipeline HITBUBBLE_FILL_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.MATRICES_FOG_SNIPPET })
                   .withLocation(Identifier.fromNamespaceAndPath("vesence", "pipeline/world/hitbubble_fill"))
                   .withVertexShader("core/position_tex_color")
                   .withFragmentShader("core/position_tex_color")
                   .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                   .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)                   .withPrimitiveTopology(PrimitiveTopology.QUADS)
                   .withCull(false)
                   .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                   .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
                   .build());

   private static RenderType hitBubbleQuadLayer(Identifier tex) {
      return RenderType.create("vesence_hitbubble_quad_" + tex.getPath(),
              RenderSetup.builder(TEXTURED_QUADS_NO_DEPTH_ADDITIVE_PIPELINE)
                      .withTexture("Sampler0", tex).createRenderSetup());
   }
   private static RenderType hitBubbleFillLayer(Identifier tex) {
      // Заливка осколков через additive QUADS-пайплайн — яркие цветные текстурные
      // осколки картинки таргета (полный порт harmony: GL_SRC_ALPHA, GL_ONE).
      return RenderType.create("vesence_hitbubble_fill_" + tex.getPath(),
              RenderSetup.builder(HITBUBBLE_FILL_PIPELINE)
                      .withTexture("Sampler0", tex).createRenderSetup());
   }
   private static final RenderType IMG1_BUBBLE_QUAD = hitBubbleQuadLayer(IMAGE_1);
   private static final RenderType IMG2_BUBBLE_QUAD = hitBubbleQuadLayer(IMAGE_2);
   private static final RenderType IMG3_BUBBLE_QUAD = hitBubbleQuadLayer(IMAGE_3);
   private static final RenderType IMG4_BUBBLE_QUAD = hitBubbleQuadLayer(IMAGE_4);
   private static final RenderType IMG1_BUBBLE_FILL = hitBubbleFillLayer(IMAGE_1);
   private static final RenderType IMG2_BUBBLE_FILL = hitBubbleFillLayer(IMAGE_2);
   private static final RenderType IMG3_BUBBLE_FILL = hitBubbleFillLayer(IMAGE_3);
   private static final RenderType IMG4_BUBBLE_FILL = hitBubbleFillLayer(IMAGE_4);

   private RenderType bubbleQuadLayer() {
      if (typeImage.is("Картинка 1")) return IMG1_BUBBLE_QUAD;
      if (typeImage.is("Картинка 2")) return IMG2_BUBBLE_QUAD;
      if (typeImage.is("Картинка 4")) return IMG4_BUBBLE_QUAD;
      return IMG3_BUBBLE_QUAD;
   }
   private RenderType bubbleFillLayer() {
      if (typeImage.is("Картинка 1")) return IMG1_BUBBLE_FILL;
      if (typeImage.is("Картинка 2")) return IMG2_BUBBLE_FILL;
      if (typeImage.is("Картинка 4")) return IMG4_BUBBLE_FILL;
      return IMG3_BUBBLE_FILL;
   }

   private float appearValue = 0.0F;
   private float renderPartialTicks = 0.0F;
   private LivingEntity lastHandledTarget = null;
   private Vec3 lastTargetPos = null;
   private float lastTargetHeight = 1.8F;
   private float lastTargetWidth = 0.6F;

   private final java.util.concurrent.CopyOnWriteArrayList<GlowPoint> bmwPoints = new java.util.concurrent.CopyOnWriteArrayList<>();

   private static final int ORBIT_PARTICLE_COUNT = 3;
   private static final float ORBIT_BASE_RADIUS = 0.4F;
   private static final float ORBIT_BASE_MUL = 0.1F;
   private static final float ORBIT_SPEED = 15.0F;
   private static final int ORBIT_TRAIL_LENGTH = 40;
   private static final float[] ORBIT_SCALE_CACHE = new float[101];
   static {
      for (int k = 0; k <= 100; k++) {
         ORBIT_SCALE_CACHE[k] = Math.max(0.28F * (k / 100.0F), 0.15F);
      }
   }
   private final Vec3[] orbitPositions = new Vec3[ORBIT_PARTICLE_COUNT];
   private final Vec3[] orbitMotions = new Vec3[ORBIT_PARTICLE_COUNT];
   @SuppressWarnings("unchecked")
   private final java.util.List<Vec3>[] orbitTrails = new java.util.List[ORBIT_PARTICLE_COUNT];
   private float orbitMovingAngle = 0.0F;
   private long orbitLastTime = 0L;
   private float orbitShrinkValue = 0.0F;
   private LivingEntity orbitLastTarget = null;

   private float compassValue23 = 0.0F;
   private long compassTimestamp4 = System.currentTimeMillis();
   private long compassTimestamp5 = System.nanoTime();

   private final ArrayList<PentParticle> pentagramParticles = new ArrayList<>();
   private final ArrayDeque<PentParticle> pentagramPool = new ArrayDeque<>();
   private long lastPentagramParticleTime = 0L;
   private static final int MAX_PENTAGRAM_PARTICLES = 120;
   private static final float PENTAGRAM_PARTICLE_SPAWN_INTERVAL_MS = 35.0F;

   private long lastSkullTime = 0L;
   private float skullFadeProgress = 1.0F;
   private int skullCurrentState = -1;
   private int skullNextState = -1;
   private long skullShakeStart = 0L;

   private final vesence.utils.render.HitBubbleEffect hitBubbleShatter = new vesence.utils.render.HitBubbleEffect();
   private boolean hitBubbleShatterEmitted = false;
   private Vec3 hitBubbleLastMarkerPos = null;

   private float scaleValue = 0.0F;
   private float rotProgress = 0.0F;
   private float rotFrom = -280.0F;
   private float rotTo = 280.0F;
   private long lastRotateUpdate = System.currentTimeMillis();

   public TargetESP() {
      this.addSettings(new Setting[] { typeTargetEsp, typeImage, imageSize, typeCube, throughWalls, torusDepth, torusSize, torusWidth, torusGlow,
              hurtColor, rotateSpeed, ringRadius, ringSpeed, bmwGhostCount, bmwGhostLife, bmwStrengthXZ, bmwStrengthY, pentagramSpeed,
              hitBubbleEffect, hitBubbleColor, hitBubbleVoronoi, hitBubbleVoronoiPriority });
      for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
         this.orbitTrails[i] = new ArrayList<>();
         this.orbitMotions[i] = Vec3.ZERO;
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      if (targetEspAllocator != null) { targetEspAllocator.close(); targetEspAllocator = null; }
      if (targetEspAllocator2 != null) { targetEspAllocator2.close(); targetEspAllocator2 = null; }
      if (targetEspAllocQuad != null) { targetEspAllocQuad.close(); targetEspAllocQuad = null; }
      if (targetEspAllocFill != null) { targetEspAllocFill.close(); targetEspAllocFill = null; }
   }

   @EventInit
   public void onRender(EventRender3D e) {
      alpha.update();
      this.renderPartialTicks = e.getTickDelta();
      LivingEntity target = AttackAura.target != null ? AttackAura.target : TriggerBot.target;
      if (mc.level != null && mc.player != null) {
         AttackAura hitAura = (AttackAura) Vesence.get.manager.getModule(AttackAura.class);
         TriggerBot triggerBot = (TriggerBot) Vesence.get.manager.getModule(TriggerBot.class);
         if (hitAura != null || triggerBot != null) {
            boolean hasTargetNow = target != null && target.isAlive();
            this.appearValue = this.animateTo(this.appearValue, hasTargetNow ? 1.0F : 0.0F, 0.05F);
            this.scaleValue = this.animateTo(this.scaleValue, hasTargetNow ? 1.0F : 0.5F, 0.05F);
            if (hasTargetNow) {
               this.lastHandledTarget = target;
               float td = e.getTickDelta();
               this.lastTargetPos = new Vec3(
                       Mth.lerp(td, target.xOld, target.getX()),
                       Mth.lerp(td, target.yOld, target.getY()),
                       Mth.lerp(td, target.zOld, target.getZ()));
               this.lastTargetHeight = target.getBbHeight();
               this.lastTargetWidth = target.getBbWidth();
            }
            alpha.run(target == null ? 0.0 : 1.0, 0.35F, Easings.QUART_OUT);

            this.tickHitBubbleEffect(hasTargetNow);
            this.renderHitBubbleShatter(e);
            if (alpha.getValue() > 0.0) {
               if (target != null) {
                  if (this.lastTarget != target) {
                     lastTime = 0L;
                     this.currentTimeSpirits = 0L;
                     this.animationNurik = 0.0F;
                     this.spiritsRingLastTime = 0L;
                     this.spiritsRingAngle = 0.0F;
                  }

                  this.lastTarget = target;
               }
               if (this.lastTarget != null && !typeTargetEsp.is("Не отображать")) {
                  if (targetEspAllocator == null) { targetEspAllocator = new ByteBufferBuilder(1048576); }
                  Immediate immediate = new Immediate();

                  try {
                     if (typeTargetEsp.is("Картинка")) {
                        this.renderMarker3D(e, immediate, this.lastTarget, hasTargetNow);
                     }

                     if (typeTargetEsp.is("Призраки")) {
                        this.renderSpirits(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Призраки 2")) {
                        this.renderSpiritsRing(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кольцо")) {
                        this.renderRing(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кубики") && typeCube.is("Новый")) {
                        this.renderCubes(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кубики") && typeCube.is("Старый")) {
                        this.renderCubesOld(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кристаллы")) {
                        this.renderCrystals(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     if (typeTargetEsp.is("Кольцо 2")) {
                        this.renderRing2(e.getMatrixStack(), immediate, this.lastTarget, e.getTickDelta());
                     }

                     LivingEntity portTarget = hasTargetNow ? target : this.lastTarget;
                     if (typeTargetEsp.is("Цепи")) {
                        this.drawChainsV2(e, immediate, portTarget);
                     }
                     if (typeTargetEsp.is("Призрачные орбиты")) {
                        this.drawGhostOrbits3D(e, immediate, portTarget);
                     }
                     if (typeTargetEsp.is("Райдер")) {
                        if (hasTargetNow && target != null) {
                           this.addBMWGhosts(target,
                                   Math.max(1, Math.round((float) (double) bmwGhostCount.get())),
                                   Math.max(1, Math.round((float) (double) bmwGhostLife.get())),
                                   this.getESPColor());
                        }
                        this.bmwPoints.removeIf(GlowPoint::shouldRemove);
                        this.drawBMW3D(e, immediate);
                     }
                     if (typeTargetEsp.is("Циркуль")) {
                        this.drawCompass3D(e, immediate, portTarget);
                     }
                     if (typeTargetEsp.is("Череп")) {
                        this.drawSkull3D(e, immediate, portTarget);
                     }
                     if (typeTargetEsp.is("Пентограма 1")) {
                        this.renderPentagram3D(e, immediate, portTarget);
                     }
                     if (typeTargetEsp.is("Торус")) {
                        this.renderTorusRing(e, this.lastTarget);
                     }

                     if (typeTargetEsp.is("Ромб крутой")) {
                        this.renderSquare(e, immediate, hasTargetNow ? target : this.lastTarget);
                     }

                     immediate.draw();
                  } finally {
                     // reused across frames
                  }
               }
            } else {
               this.lastTarget = null;
               lastTime = 0L;
               this.currentTimeSpirits = 0L;
               this.animationNurik = 0.0F;
               this.spiritsRingLastTime = 0L;
               this.spiritsRingAngle = 0.0F;
               this.appearValue = 0.0F;
               this.lastTargetPos = null;
               this.lastHandledTarget = null;
               this.bmwPoints.clear();
               for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
                  this.orbitPositions[i] = null;
                  this.orbitMotions[i] = Vec3.ZERO;
                  if (this.orbitTrails[i] != null) this.orbitTrails[i].clear();
               }
               this.orbitMovingAngle = 0.0F;
               this.orbitLastTime = 0L;
               this.orbitShrinkValue = 0.0F;
               this.orbitLastTarget = null;
               this.compassValue23 = 0.0F;
               this.pentagramParticles.clear();
               this.lastPentagramParticleTime = 0L;
               this.lastSkullTime = 0L;
               this.skullFadeProgress = 1.0F;
               this.skullCurrentState = -1;
               this.skullNextState = -1;
               this.skullShakeStart = 0L;
            }
         }
      }
   }

   private void renderRing(PoseStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target == null) {
         return;
      }
      // ── Кольцо в стиле Relevant (без шейдера) ─────────────────────────────
      // Порт визуала ru.whylol TargetESP.drawRing3D: переливающийся градиент
      // темы (fade-flow), плавный bounce по телу цели (ease-in-out вверх-вниз),
      // плотность 360 сегментов + вертикальная «шторка» затухания за ярко
      // светящейся линией. Шейдерный диск под кольцом НЕ добавляем (по просьбе).
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      double x = target.xOld + (target.getX() - target.xOld) * partialTicks;
      double y = target.yOld + (target.getY() - target.yOld) * partialTicks;
      double z = target.zOld + (target.getZ() - target.zOld) * partialTicks;

      matrices.pushPose();
      matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
      Matrix4f matrix = matrices.last().pose();

      float alphaPC = (float) alpha.getValue();
      float height = target.getBbHeight();
      double radius = ringRadius.get();

      // Bounce: тот же расчёт, что в Relevant — прогон по телу с ease-in-out,
      // «шлейф» (eased) тянется за линией в сторону движения.
      double duration = 2000.0 / ringSpeed.get();
      double elapsed = System.currentTimeMillis() % (long) duration;
      boolean sideFlag = elapsed > duration / 2.0;
      double progress = elapsed / (duration / 2.0);
      if (sideFlag) {
         progress -= 1.0;
      } else {
         progress = 1.0 - progress;
      }
      progress = progress < 0.5 ? 2.0 * progress * progress : 1.0 - Math.pow(-2.0 * progress + 2.0, 2.0) / 2.0;
      double eased = height / 1.2 * (progress > 0.5 ? 1.0 - progress : progress) * (sideFlag ? -1 : 1);

      // Цвет — переливающийся градиент темы (аналог getFlowColor в Relevant).
      // ColorUtil.fade(0) осциллирует тема↔тёмная-тема по времени.
      int baseCol = ColorUtil.fade(0);
      float hurtPC = getHurtPC(target);
      int redCol = ColorUtil.getColor(255, 3, 3, 255);
      int mainColor = ColorUtil.overCol(baseCol, redCol, hurtPC);
      // Низ шторки (у линии) — почти непрозрачный (0.882), верх — прозрачный.
      int colorBottom = ColorUtil.replAlpha(mainColor, (int) (225.0F * alphaPC));
      int colorTop = ColorUtil.replAlpha(mainColor, (int) alphaPC);
      int colorLine = ColorUtil.replAlpha(mainColor, (int) (255.0F * alphaPC));

      int rb = ColorUtil.red(colorBottom), gb = ColorUtil.green(colorBottom), bb = ColorUtil.blue(colorBottom), ab = ColorUtil.alpha(colorBottom);
      int rt = ColorUtil.red(colorTop), gt = ColorUtil.green(colorTop), bt = ColorUtil.blue(colorTop), at = ColorUtil.alpha(colorTop);

      float ringY = (float) (height * progress);
      float trailY = (float) (height * progress + eased);

      VertexConsumer strip = immediate.getBuffer(RING_STRIP_LAYER);
      for (int i = 0; i <= 360; i++) {
         double rad = Math.toRadians(i);
         float xPos = (float) (Math.cos(rad) * radius);
         float zPos = (float) (Math.sin(rad) * radius);
         strip.addVertex(matrix, xPos, ringY, zPos).setColor(rb, gb, bb, ab);
         strip.addVertex(matrix, xPos, trailY, zPos).setColor(rt, gt, bt, at);
      }

      VertexConsumer line = immediate.getBuffer(RING_LINE_LAYER);
      for (int i = 0; i <= 360; i++) {
         double rad = Math.toRadians(i);
         float xPos = (float) (Math.cos(rad) * radius);
         float zPos = (float) (Math.sin(rad) * radius);
         line.addVertex(matrix, xPos, ringY, zPos).setColor(colorLine);
      }

      matrices.popPose();
   }

   private void renderSpirits(PoseStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         long currentTime = System.currentTimeMillis();
         if (this.currentTimeSpirits == 0L) {
            this.currentTimeSpirits = currentTime;
         }

         long timeDiff = currentTime - this.currentTimeSpirits;
         if (timeDiff > 0L) {
            this.animationNurik += (float) (5L * timeDiff) / 1250.0f;
         }

         this.currentTimeSpirits = currentTime;
         Vec3 lerpedPos = target.getPosition(partialTicks);
         Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
         double x = lerpedPos.x - cameraPos.x;
         double y = lerpedPos.y - cameraPos.y;
         double z = lerpedPos.z - cameraPos.z;
         float alphaPC = (float) alpha.getValue();
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = size.get();
         int fadeColor = ColorUtil.fade();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
         int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);
         VertexConsumer buffer = immediate.getBuffer(SPIRITS_GLOW_LAYER);
         int n2 = 3;
         int n3 = 12;
         int n4 = 3 * n2;
         matrices.pushPose();
         Camera camera = mc.gameRenderer.mainCamera();

         for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; j++) {
               float f2 = this.animationNurik + j * 0.1F;
               float f3 = 0.75F;
               float f4 = 0.5F;
               int n5 = (int) Math.pow(i, 2.0);
               matrices.pushPose();
               double particleX = x + f3 * Math.sin(f2 + n5);
               double particleY = y + f4 + 0.3F * Math.sin(this.animationNurik + j * 0.2F) + 0.2F * i;
               double particleZ = z + f3 * Math.cos(f2 - n5);
               matrices.translate(particleX, particleY, particleZ);

               float scale;
               scale = 0.0015F + j / 2000.0F;

               matrices.scale(scale, scale, scale);
               matrices.rotate(Axis.YP.rotationDegrees(-camera.yRot()));
               matrices.rotate(Axis.XP.rotationDegrees(camera.xRot()));
               Matrix4f matrix = matrices.last().pose();
               VertexConsumer buffer2 = immediate.getBuffer(SPIRITS_GLOW_LAYER);
               int r = baseColor >> 16 & 0xFF;
               int g = baseColor >> 8 & 0xFF;
               int b = baseColor & 0xFF;
               int a = (int) (alphaPC * 255.0F);
               int n7 = -25;
               int n8 = 50;
               buffer2.addVertex(matrix, n7, n7 + n8, 0.0F)
                       .setColor(r, g, b, a)
                       .setUv(0.0F, 1.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               buffer2.addVertex(matrix, n7 + n8, n7 + n8, 0.0F)
                       .setColor(r, g, b, a)
                       .setUv(1.0F, 1.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               buffer2.addVertex(matrix, n7 + n8, n7, 0.0F)
                       .setColor(r, g, b, a)
                       .setUv(1.0F, 0.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               buffer2.addVertex(matrix, n7, n7, 0.0F)
                       .setColor(r, g, b, a)
                       .setUv(0.0F, 0.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               matrices.popPose();
            }
         }

         for (int i = 0; i < n4; i += n2) {
            for (int j = 0; j < n3; j++) {
               float f2 = this.animationNurik + j * 0.1F;
               float f3 = 0.75F;
               float f4 = 0.5F;
               int n5 = (int) Math.pow(i, 2.0);
               matrices.pushPose();
               double particleX = x + f3 * Math.sin(f2 + n5);
               double particleY = y + f4 + 0.3F * Math.sin(this.animationNurik + j * 0.2F) + 0.2F * i;
               double particleZ = z + f3 * Math.cos(f2 - n5);
               matrices.translate(particleX, particleY, particleZ);

               float bloomScale = (0.005F + j / 2000.0F) * 2;

               matrices.scale(bloomScale, bloomScale, bloomScale);
               matrices.rotate(Axis.YP.rotationDegrees(-camera.yRot()));
               matrices.rotate(Axis.XP.rotationDegrees(camera.xRot()));
               Matrix4f bloomMatrix = matrices.last().pose();
               VertexConsumer bloomBuffer = immediate.getBuffer(SPIRITS_BLOOM_LAYER);
               int br = baseColor >> 16 & 0xFF;
               int bg = baseColor >> 8 & 0xFF;
               int bb = baseColor & 0xFF;
               int ba = (int) (alphaPC * 255.0F * 0.2F);
               int bn7 = -25;
               int bn8 = 50;
               bloomBuffer.addVertex(bloomMatrix, bn7, bn7 + bn8, 0.0F)
                       .setColor(br, bg, bb, ba)
                       .setUv(0.0F, 1.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               bloomBuffer.addVertex(bloomMatrix, bn7 + bn8, bn7 + bn8, 0.0F)
                       .setColor(br, bg, bb, ba)
                       .setUv(1.0F, 1.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               bloomBuffer.addVertex(bloomMatrix, bn7 + bn8, bn7, 0.0F)
                       .setColor(br, bg, bb, ba)
                       .setUv(1.0F, 0.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               bloomBuffer.addVertex(bloomMatrix, bn7, bn7, 0.0F)
                       .setColor(br, bg, bb, ba)
                       .setUv(0.0F, 0.0F)
                       .setOverlay(OverlayTexture.NO_OVERLAY)
                       .setLight(15728880)
                       .setNormal(0.0F, 0.0F, 1.0F);
               matrices.popPose();
            }
         }

         matrices.popPose();
      }
   }

   private void renderSpiritsRing(PoseStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target == null) return;

      long currentTime = System.currentTimeMillis();
      if (this.spiritsRingLastTime == 0L) {
         this.spiritsRingLastTime = currentTime;
      }
      long timeDiff = currentTime - this.spiritsRingLastTime;
      if (timeDiff > 0L) {
         this.spiritsRingAngle += (float) (5L * timeDiff) / 600;
      }
      this.spiritsRingLastTime = currentTime;

      Vec3 lerpedPos = target.getPosition(partialTicks);
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      double baseX = lerpedPos.x - cameraPos.x;
      double baseY = lerpedPos.y - cameraPos.y;
      double baseZ = lerpedPos.z - cameraPos.z;

      float alphaPC = (float) alpha.getValue();
      size.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      size.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float atts = size.get();
      int fadeColor = ColorUtil.fade();
      int redColor = ColorUtil.getColor(200, 70, 70, (int) (255.0F * alphaPC));
      int baseColor = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC), redColor, atts);

      Camera camera = mc.gameRenderer.mainCamera();
      float entityHeight = target.getBbHeight();

      double duration = 2000;
      double elapsed = currentTime % duration;
      double t = elapsed / duration;
      double progress = 0.5 + 0.5 * Math.sin(t * 2.0 * Math.PI - Math.PI / 2.0);
      double eased = entityHeight / 1.0F * 0.3 * Math.cos(t * 2.0 * Math.PI);
      float ringY = (float) (entityHeight * progress + eased);

      int numGroups = 3;
      int particlesPerGroup = 12;
      float ringRadius = 0.65F;
      float arcLength = (float) (2.0 * Math.PI / numGroups * 0.4F);
      float angleStepPerParticle = arcLength / (particlesPerGroup - 1);
      int n7 = -25;
      int n8 = 50;

      matrices.pushPose();

      for (int i = 0; i < numGroups; i++) {
         float groupOffset = (float) (2.0 * Math.PI / numGroups * i);

         for (int j = 0; j < particlesPerGroup; j++) {
            float angle = this.spiritsRingAngle + groupOffset + j * angleStepPerParticle;

            double particleX = baseX + ringRadius * Math.cos(angle);
            double particleZ = baseZ + ringRadius * Math.sin(angle);
            float bobY = 0.1F * (float) Math.sin(this.spiritsRingAngle + j * 0.2F);
            double particleY = baseY + ringY + bobY;

            int c = ColorUtil.overCol(ColorUtil.multAlpha(ColorUtil.gradient(ColorUtil.multDark(fadeColor, 0.5F),ColorUtil.multDark(fadeColor, 1.0F), (i * particlesPerGroup + j) * 4, 1), alphaPC),
                    redColor, atts);

            int r = c >> 16 & 0xFF;
            int g = c >> 8 & 0xFF;
            int b = c & 0xFF;
            int a = (int) (alphaPC * 255.0F);

            float scale = 0.005F + j / 2500.0F;

            matrices.pushPose();
            matrices.translate(particleX, particleY, particleZ);
            matrices.scale(scale, scale, scale);
            matrices.rotate(Axis.YP.rotationDegrees(-camera.yRot()));
            matrices.rotate(Axis.XP.rotationDegrees(camera.xRot()));
            Matrix4f matrix = matrices.last().pose();
            VertexConsumer buffer = immediate.getBuffer(SPIRITS_GLOW_LAYER);
            buffer.addVertex(matrix, n7, n7 + n8, 0.0F)
                    .setColor(r, g, b, a)
                    .setUv(0.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            buffer.addVertex(matrix, n7 + n8, n7 + n8, 0.0F)
                    .setColor(r, g, b, a)
                    .setUv(1.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            buffer.addVertex(matrix, n7 + n8, n7, 0.0F)
                    .setColor(r, g, b, a)
                    .setUv(1.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            buffer.addVertex(matrix, n7, n7, 0.0F)
                    .setColor(r, g, b, a)
                    .setUv(0.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            matrices.popPose();

            float bloomScale = (scale + j / 2000.0F) * 1.5f;
            int ba = (int) (alphaPC * 255.0F * 0.2F);
            matrices.pushPose();
            matrices.translate(particleX, particleY, particleZ);
            matrices.scale(bloomScale, bloomScale, bloomScale);
            matrices.rotate(Axis.YP.rotationDegrees(-camera.yRot()));
            matrices.rotate(Axis.XP.rotationDegrees(camera.xRot()));
            Matrix4f bloomMatrix = matrices.last().pose();
            VertexConsumer bloomBuffer = immediate.getBuffer(SPIRITS_BLOOM_LAYER);
            bloomBuffer.addVertex(bloomMatrix, n7, n7 + n8, 0.0F)
                    .setColor(r, g, b, ba)
                    .setUv(0.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            bloomBuffer.addVertex(bloomMatrix, n7 + n8, n7 + n8, 0.0F)
                    .setColor(r, g, b, ba)
                    .setUv(1.0F, 1.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            bloomBuffer.addVertex(bloomMatrix, n7 + n8, n7, 0.0F)
                    .setColor(r, g, b, ba)
                    .setUv(1.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            bloomBuffer.addVertex(bloomMatrix, n7, n7, 0.0F)
                    .setColor(r, g, b, ba)
                    .setUv(0.0F, 0.0F)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(15728880)
                    .setNormal(0.0F, 0.0F, 1.0F);
            matrices.popPose();
         }
      }

      matrices.popPose();
   }

   private static void drawGradientQuad(VertexConsumer buffer, Matrix4f matrix, int color, int alpha) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      buffer.addVertex(matrix, -0.5F, -0.5F, 0.0F)
              .setColor(r, g, b, alpha)
              .setUv(0.0F, 1.0F)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
      buffer.addVertex(matrix, 0.5F, -0.5F, 0.0F)
              .setColor(r, g, b, alpha)
              .setUv(1.0F, 1.0F)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
      buffer.addVertex(matrix, 0.5F, 0.5F, 0.0F)
              .setColor(r, g, b, alpha)
              .setUv(1.0F, 0.0F)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
      buffer.addVertex(matrix, -0.5F, 0.5F, 0.0F)
              .setColor(r, g, b, alpha)
              .setUv(0.0F, 0.0F)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
   }

   private void renderCubes(PoseStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target != null) {
         Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
         long time = System.currentTimeMillis();
         int count = 24;
         double radius = 0.4 + target.getBbWidth() / 2.0F + 0.35F - 0.35F * alpha.get();
         double heightRange = target.getBbHeight();
         Vec3 lerpedPos = target.getPosition(partialTicks);
         float alphaPC = (float) alpha.getValue();
         size.update();
         int hurtTicks = target.hurtTime;
         float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
         size.run(hurtPC, 0.4F, Easings.QUART_OUT);
         float atts = size.get();
         int redColor = ColorUtil.getColor(200, 70, 70, (int) (60.0F * alphaPC));
         int fadeColor = ColorUtil.fade();
         int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35F);
         int color = ColorUtil.overCol(baseColor, redColor, atts);
         int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
                 ColorUtil.getColor(200, 100, 100, (int) (255.0F * alphaPC)), atts);

         for (int i = 0; i < count; i++) {
            double r1 = Math.sin(i * 132.12 + 4.12);
            double r2 = Math.cos(i * 453.21 + 1.23);
            double r3 = Math.sin(i * 789.34 + 9.87);
            double speedFactor = 1.0;
            double angleOffset = (Math.PI * 2) / count * i;
            double timeFactor = time / 6000.0 * (Math.PI * 2) * speedFactor;
            double angle = timeFactor + angleOffset;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            double ySpeed = 1.0 + r1 * 0.2;
            double yPhase = angleOffset + r3 * 2.0;
            double yOffset = Math.sin(time / 9000.0 * (Math.PI * 2) * ySpeed + yPhase) * 0.45 + 0.55;
            double y = yOffset * heightRange;
            double cX = lerpedPos.x + x - cameraPos.x;
            double cY = lerpedPos.y + y - cameraPos.y;
            double cZ = lerpedPos.z + z - cameraPos.z;
            matrices.pushPose();
            matrices.translate(cX, cY, cZ);
            float pulse = 1.0F + 0.15F * (float) Math.sin(time / 400.0 + i * 1.5);
            float cubeSize = 0.19F * pulse;
            double hurtFactor = atts * (0.5 + 0.5 * Math.sin(i * 123.45));
            if (hurtFactor > 0.05) {
               cubeSize = (float) (cubeSize * (1.0 - hurtFactor * 0.2));
               double pushOut = hurtFactor * 0.4;
               matrices.translate(Math.cos(angle) * pushOut, 0.0, Math.sin(angle) * pushOut);
            }

            matrices.pushPose();
            float selfRotSpeed = 12000.0F + (float) r3 * 2000.0F;
            float selfRot = (float) (time % (long) Math.abs(selfRotSpeed)) / Math.abs(selfRotSpeed) * 360.0F;
            if (i % 3 == 0) {
               matrices.rotate(Axis.YP.rotationDegrees(selfRot));
               matrices.rotate(Axis.XP.rotationDegrees(selfRot));
            } else if (i % 3 == 1) {
               matrices.rotate(Axis.ZP.rotationDegrees(selfRot));
               matrices.rotate(Axis.YP.rotationDegrees(selfRot));
            } else {
               matrices.rotate(Axis.XP.rotationDegrees(selfRot));
               matrices.rotate(Axis.ZP.rotationDegrees(selfRot));
            }

            VertexConsumer buffer = immediate.getBuffer(COLOR_QUADS_LAYER);
            Matrix4f matrix = matrices.last().pose();
            drawColorCube(buffer, matrix, ColorUtil.multAlpha(color, 0.5F), cubeSize);
            int lineColor = ColorUtil.multAlpha(color, alphaPC * 1.0F);
            VertexConsumer lineBuffer = immediate.getBuffer(CUBE_LINES_LAYER);
            drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);
            matrices.popPose();
            matrices.pushPose();
            matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
            matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));
            VertexConsumer glowBuffer = immediate.getBuffer(SPIRITS_BLOOM_LAYER);
            Matrix4f glowMatrix = matrices.last().pose();
            float glowSize = cubeSize * 3.0F;
            matrices.scale(glowSize, glowSize, glowSize);
            drawGradientQuad(glowBuffer, glowMatrix, glowCol, (int) (125.0F * alphaPC));
            matrices.popPose();
            matrices.popPose();
         }
      }
   }

   private static void drawColorCube(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
      float s = size / 2.0F;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      buffer.addVertex(matrix, -s, s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, -s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, -s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, -s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, -s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, -s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, -s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, -s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, -s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, -s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, -s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, -s, s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, s, -s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, -s, s).setColor(r, g, b, a);
      buffer.addVertex(matrix, s, -s, -s).setColor(r, g, b, a);
   }

   private static void drawCubeLines(VertexConsumer buffer, Matrix4f matrix, int color, float size) {
      float s = size / 2.0F;
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      drawLine(buffer, matrix, -s, -s, -s, s, -s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, -s, s, -s, s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, s, -s, -s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, s, -s, -s, -s, r, g, b, a);
      drawLine(buffer, matrix, -s, s, -s, s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, s, -s, s, s, s, r, g, b, a);
      drawLine(buffer, matrix, s, s, s, -s, s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, s, s, -s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, -s, -s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, -s, s, s, -s, r, g, b, a);
      drawLine(buffer, matrix, s, -s, s, s, s, s, r, g, b, a);
      drawLine(buffer, matrix, -s, -s, s, -s, s, s, r, g, b, a);
   }

   private static void drawLine(VertexConsumer buffer, Matrix4f matrix, float x1, float y1, float z1, float x2,
                                float y2, float z2, int r, int g, int b, int a) {
      buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
      buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
   }

   private void renderCubesOld(PoseStack matrices, Immediate immediate, LivingEntity target, float partialTicks) {
      if (target == null) {
         this.oldCubeParticles.clear();
      } else {
         Iterator<TargetESP.OldCubeParticle> iterator = this.oldCubeParticles.iterator();

         while (iterator.hasNext()) {
            TargetESP.OldCubeParticle particle = iterator.next();
            if (particle.animation.getDirection() != Direction.FORWARDS && particle.animation.getOutput() <= 0.0F) {
               iterator.remove();
            }
         }

         long currentTime = System.currentTimeMillis();
         oldCubeDeltaTime = Math.max(0.001F, Math.min(0.1F, (float) (currentTime - oldCubeLastTime) / 1000.0F));
         oldCubeLastTime = currentTime;
         if (this.oldCubeParticles.size() < 50) {
            this.oldCubeSpawnAccumulator = this.oldCubeSpawnAccumulator + oldCubeDeltaTime;

            while (this.oldCubeSpawnAccumulator >= 0.02F && this.oldCubeParticles.size() < 50) {
               this.oldCubeSpawnAccumulator -= 0.02F;

               for (int i = 0; i < 1 && this.oldCubeParticles.size() < 50; i++) {
                  double rand = Mathf.random(0.0F, 360.0F);
                  double x = Math.cos(rand * Math.PI / 180.0) * 0.7F;
                  double y = Mathf.getRandomNumberBetween(0.04F, 0.2F);
                  double z = Math.sin(rand * Math.PI / 180.0) * 0.7F;
                  this.oldCubeParticles.add(new TargetESP.OldCubeParticle(target, x, y, z));
               }
            }
         }

         if (!this.oldCubeParticles.isEmpty()) {
            float alphaPC = (float) alpha.getValue();
            size.update();
            int hurtTicks = target.hurtTime;
            float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
            size.run(hurtPC, 0.4F, Easings.QUART_OUT);
            float atts = size.get();
            int redColor = ColorUtil.getColor(200, 70, 70, (int) (60.0F * alphaPC));
            int fadeColor = ColorUtil.fade();
            int baseColor = ColorUtil.multAlpha(fadeColor, alphaPC * 0.35F);
            int color = ColorUtil.overCol(baseColor, redColor, atts);
            int glowCol = ColorUtil.overCol(ColorUtil.multAlpha(fadeColor, alphaPC),
                    ColorUtil.getColor(200, 100, 100, (int) (255.0F * alphaPC)), atts);
            Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
            float pitch = mc.gameRenderer.mainCamera().xRot();
            float yaw = mc.gameRenderer.mainCamera().yRot();
            for (TargetESP.OldCubeParticle particle : this.oldCubeParticles) {
               particle.update(partialTicks);
               particle.render(matrices, immediate, color, glowCol, alphaPC, atts, partialTicks, cameraPos, pitch, yaw,
                       SPIRITS_BLOOM_LAYER);
            }
         }
      }
   }

   private void renderCrystals(PoseStack matrices, Immediate immediate, LivingEntity target, float tickDelta) {
      if (target == null) return;

      float alphaPC = (float) alpha.getValue();
      if (alphaPC <= 0.01F) return;

      this.hurtColorAnimation.update();
      float easedAnim = easeOutCubic(alphaPC);

      Vec3 targetPos = target.getPosition(tickDelta);
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      Vec3 renderPos = targetPos.subtract(cameraPos);

      float time = (mc.player.tickCount + tickDelta) * 6.0F;
      float entityHeight = target.getBbHeight();
      float entityWidth = target.getBbWidth();
      float halfWidth = entityWidth * 0.5F;

      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      this.hurtColorAnimation.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float hurtBlend = (float) this.hurtColorAnimation.get();
      int baseColor = blendWithHurt(ColorUtil.fade(), ColorUtil.getColor(255, 0, 0, 255), hurtBlend);

      matrices.pushPose();
      matrices.translate(renderPos.x, renderPos.y, renderPos.z);

      Camera camera = mc.gameRenderer.mainCamera();

      VertexConsumer glowBuffer = immediate.getBuffer(CRYSTAL_GLOW_LAYER);

      int crystalCount = 14;
      for (int i = 0; i < crystalCount; i++) {
         float seed1 = (float) Math.sin(i * 1.7F + 0.3F) * 0.5F + 0.5F;
         float seed2 = (float) Math.cos(i * 2.3F + 0.7F) * 0.5F + 0.5F;
         float seed3 = (float) Math.sin(i * 3.1F + 1.1F) * 0.5F + 0.5F;
         float angleOffset = i * (360F / crystalCount) + seed1 * 12F;
         float angle = time + angleOffset;
         float radius = halfWidth + 0.25F + seed3 * 0.15F;

         float x = radius * (float) Math.cos(Math.toRadians(angle));
         float z = radius * (float) Math.sin(Math.toRadians(angle));
         float y = seed2 * entityHeight;
         float crystalScale = 0.24F * easedAnim;

         matrices.pushPose();
         matrices.translate(x, y, z);
         matrices.rotate(Axis.YP.rotationDegrees(-camera.yRot()));
         matrices.rotate(Axis.XP.rotationDegrees(camera.xRot()));
         matrices.rotate(Axis.ZP.rotationDegrees(180));
         matrices.scale(crystalScale * 3.6F, crystalScale * 3.6F, crystalScale * 3.6F);
         Matrix4f glowMatrix = matrices.last().pose();
         int glowColor = ColorUtil.multAlpha(baseColor, alphaPC * 0.5F);
         drawGradientQuad(glowBuffer, glowMatrix, glowColor, (glowColor >> 24) & 0xFF);
         matrices.popPose();
      }

      VertexConsumer geomBuffer = immediate.getBuffer(CRYSTAL_TRIANGLES_LAYER);
      for (int i = 0; i < crystalCount; i++) {
         float seed1 = (float) Math.sin(i * 1.7F + 0.3F) * 0.5F + 0.5F;
         float seed2 = (float) Math.cos(i * 2.3F + 0.7F) * 0.5F + 0.5F;
         float seed3 = (float) Math.sin(i * 3.1F + 1.1F) * 0.5F + 0.5F;
         float angleOffset = i * (360F / crystalCount) + seed1 * 12F;
         float angle = time + angleOffset;
         float radius = halfWidth + 0.25F + seed3 * 0.15F;

         float x = radius * (float) Math.cos(Math.toRadians(angle));
         float z = radius * (float) Math.sin(Math.toRadians(angle));
         float y = seed2 * entityHeight;

         drawCrystalGeometry(matrices, geomBuffer, x, y, z, 0.24F * easedAnim, angle, baseColor, alphaPC * 0.7F);
      }

      matrices.popPose();
   }

   private void drawCrystalTriangle(VertexConsumer buffer, Matrix4f matrix,
                                    float x1, float y1, float z1,
                                    float x2, float y2, float z2,
                                    float x3, float y3, float z3,
                                    int r, int g, int b, int a) {
      buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
      buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
      buffer.addVertex(matrix, x3, y3, z3).setColor(r, g, b, a);
   }

   private int getThemeColorAngle(int angle) {
      int baseColor = ColorUtil.fade();
      float hueShift = (angle % 360) / 360.0F * 0.15F;
      float[] hsv = Color.RGBtoHSB((baseColor >> 16) & 0xFF, (baseColor >> 8) & 0xFF, baseColor & 0xFF, null);
      hsv[0] = (hsv[0] + hueShift) % 1.0F;
      int shifted = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]);
      return (shifted & 0x00FFFFFF) | (baseColor & 0xFF000000);
   }

   private void drawCrystalGeometry(PoseStack matrices, VertexConsumer buffer, float x, float y, float z, float scale, float yaw, int color, float alpha) {
      matrices.pushPose();
      matrices.translate(x, y, z);
      matrices.rotate(Axis.YP.rotationDegrees(-yaw + 90F));
      matrices.rotate(Axis.XP.rotationDegrees(90F));
      matrices.scale(scale, scale, scale);
      Matrix4f matrix = matrices.last().pose();

      int r = (color >> 16) & 0xFF;
      int g = (color >> 8) & 0xFF;
      int b = color & 0xFF;

      int a = (int) (255 * alpha);
      int aDark = (int) (255 * alpha);

      int rL = Math.min(255, (int) (r * 0.10F));
      int gL = Math.min(255, (int) (g * 0.10F));
      int bL = Math.min(255, (int) (b * 0.10F));

      int rLDark = Math.min(255, (int) (r));
      int gLDark = Math.min(255, (int) (g));
      int bLDark = Math.min(255, (int) (b));

      float d = 0.3F;
      float h = 0.5F;

      float[][] vertices = {
              {0.0F, h, 0.0F},
              {0.0F, -h, 0.0F},
              {d, 0.0F, d},
              {-d, 0.0F, -d},
              {d, 0.0F, -d},
              {-d, 0.0F, d}
      };

      int[][] faces = {
              {0, 2, 5}, {0, 5, 3}, {0, 3, 4}, {0, 4, 2},
              {1, 2, 5}, {1, 5, 3}, {1, 3, 4}, {1, 4, 2}
      };

      for (int[] face : faces) {
         int i0 = face[0];
         int i1 = face[1];
         int i2 = face[2];

         buffer.addVertex(matrix, vertices[i0][0], vertices[i0][1], vertices[i0][2]).setColor(rL, gL, bL, aDark);
         buffer.addVertex(matrix, vertices[i1][0], vertices[i1][1], vertices[i1][2]).setColor(rL, gL, bL, aDark);
         buffer.addVertex(matrix, vertices[i2][0], vertices[i2][1], vertices[i2][2]).setColor(rL, gL, bL, a);

         buffer.addVertex(matrix, vertices[i0][0], vertices[i0][1], vertices[i0][2]).setColor(rL, gL, bL, a);
         buffer.addVertex(matrix, vertices[i2][0], vertices[i2][1], vertices[i2][2]).setColor(rL, gL, bL, a);
         buffer.addVertex(matrix, vertices[i1][0], vertices[i1][1], vertices[i1][2]).setColor(rLDark, gLDark, bLDark, aDark);
      }

      matrices.popPose();
   }

   private void renderRing2(PoseStack matrices, Immediate immediate, LivingEntity target, float tickDelta) {
      if (target == null) return;

      float alphaPC = (float) alpha.getValue();
      if (alphaPC <= 0.01F) return;

      this.hurtColorAnimation.update();
      float easedAnim = easeOutCubic(alphaPC);

      Vec3 targetPos = target.getPosition(tickDelta);
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      Vec3 renderPos = targetPos.subtract(cameraPos);

      float entityWidth = target.getBbWidth() * 0.9F;
      float entityHeight = target.getBbHeight();

      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      this.hurtColorAnimation.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float hurtBlend = (float) this.hurtColorAnimation.get();
      int baseColor = blendWithHurt(ColorUtil.fade(), ColorUtil.getColor(255, 0, 0, 255), hurtBlend);
      int headColor = ColorUtil.multAlpha(baseColor, easedAnim * 0.4F);

      double duration = 1800.0;
      double elapsed = System.currentTimeMillis() % duration;
      double t = elapsed / duration;
      double progress = 0.5 + 0.5 * Math.sin(t * 2.0 * Math.PI - Math.PI / 2.0);
      double eased = entityHeight / 1.25F * 0.3 * Math.cos(t * 2.0 * Math.PI);
      float headY = (float) (entityHeight * progress);
      float tailDeltaY = (float) eased;

      for (int i = 0; i < RING2_TAIL_SEGMENTS; i++) {
         ring2TailYOffsetFactors[i] = tailDeltaY * RING2_TAIL_PROGRESS[i];
         ring2TailColors[i] = ColorUtil.multAlpha(baseColor, easedAnim * RING2_TAIL_ALPHA[i]);
      }

      Vector3f rightAxis = new Vector3f(1.0F, 0.0F, 0.0F);
      Vector3f upAxis = new Vector3f(0.0F, 1.0F, 0.0F);
      mc.gameRenderer.mainCamera().rotation().transform(rightAxis);
      mc.gameRenderer.mainCamera().rotation().transform(upAxis);

      matrices.pushPose();
      matrices.translate(renderPos.x, renderPos.y, renderPos.z);
      Matrix4f matrix = matrices.last().pose();

      VertexConsumer buffer = immediate.getBuffer(RING2_GLOW_LAYER);

      for (int i = 0; i < RING2_TOTAL_POINTS; i++) {
         float localCenterX = RING2_COS[i] * entityWidth;
         float localCenterZ = RING2_SIN[i] * entityWidth;

         appendBillboardGlowQuad(buffer, matrix, localCenterX, headY, localCenterZ, RING2_HEAD_SIZE, rightAxis, upAxis, headColor);

         for (int t2 = 0; t2 < RING2_TAIL_SEGMENTS; t2++) {
            appendBillboardGlowQuad(
                    buffer, matrix,
                    localCenterX, headY + ring2TailYOffsetFactors[t2], localCenterZ,
                    RING2_HEAD_SIZE * RING2_TAIL_SIZE[t2],
                    rightAxis, upAxis,
                    ring2TailColors[t2]
            );
         }
      }

      matrices.popPose();
   }

   /**
    * Режим «Торус» для TargetESP: вокруг цели рендерится прозрачное кольцо-тор
    * (как отдельный модуль Torus, но постоянное — с пульсацией и анимацией
    * появления кольца target esp). Тор всегда обращён к цели горизонтально.
    */
   private void renderTorusRing(EventRender3D event, LivingEntity target) {
      if (target == null) return;

      float alphaPC = (float) alpha.getValue();
      if (alphaPC <= 0.01F) return;

      float tickDelta = event.getTickDelta();
      Vec3 targetPos = target.getPosition(tickDelta);
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
      Vec3 renderPos = targetPos.subtract(cameraPos);

      float entityWidth = target.getBbWidth() * 0.9F;
      float entityHeight = target.getBbHeight();

      // Пульс тела тора + вертикальное «дыхание» кольца.
      double duration = 1800.0;
      double t = (System.currentTimeMillis() % duration) / duration;
      float pulse = 1.0F + (float) Math.sin(t * 2.0 * Math.PI) * 0.15F;
      float ringY = (float) (entityHeight * (0.5 + 0.5 * Math.sin(t * 2.0 * Math.PI - Math.PI / 2.0)));

      // Размер и ширина — с ползунков.
      float outerRad = entityWidth * torusSize.get().floatValue() * pulse;
      float innerRad = entityWidth * torusWidth.get().floatValue() * pulse;

      // Тор лежит горизонтально вокруг цели: View · translate(target+ringY) · rotateX(90).
      PoseStack matrices = event.getMatrixStack();
      matrices.pushPose();
      matrices.translate(renderPos.x, renderPos.y + ringY, renderPos.z);
      matrices.rotate(Axis.XP.rotationDegrees(90.0F));
      Matrix4f mv = new Matrix4f(matrices.last().pose());
      matrices.popPose();

      // «Глубина» — перекрытие геометрией мира. Растворение (dissolve) через
      // opacity = alphaPC: плавное появление/исчезновение по шуму.
      boolean useDepth = torusDepth.get();
      TorusReflectionRenderer renderer = TorusReflectionRenderer.getInstance();
      if (renderer.begin(useDepth, event.getProjectionMatrix(), cameraPos.x, cameraPos.y, cameraPos.z, 0.5F, alphaPC)) {
         try {
            renderer.renderTorus(mv, outerRad, innerRad);
         } finally {
            renderer.end();
         }
      }

      // Свечение: аддитивный bloom-ореол вокруг тора (ползунок «Свечение», 0 = выкл).
      if (torusGlow.get().floatValue() > 0.01F) {
         this.renderTorusGlow(event, target, renderPos, outerRad, innerRad, ringY, alphaPC);
      }
   }

   /** Аддитивное bloom-свечение вокруг тора — кольцо мягких билбордов, цвет темы. */
   private void renderTorusGlow(EventRender3D event, LivingEntity target, Vec3 renderPos,
                                float outerRad, float innerRad, float ringY, float alphaPC) {
      this.hurtColorAnimation.update();
      int hurtTicks = target.hurtTime;
      float hurtPC = (float) Math.sin(hurtTicks * (Math.PI / 20));
      this.hurtColorAnimation.run(hurtPC, 0.4F, Easings.QUART_OUT);
      float hurtBlend = (float) this.hurtColorAnimation.get();
      int baseColor = blendWithHurt(ColorUtil.fade(), ColorUtil.getColor(255, 0, 0, 255), hurtBlend);
      int cr = ColorUtil.red(baseColor);
      int cg = ColorUtil.green(baseColor);
      int cb = ColorUtil.blue(baseColor);
      float glow = torusGlow.get().floatValue();
      // alpha^2 — свечение плавно тает вместе с растворением тора; масштаб от ползунка.
      int glowAlpha = Mth.clamp((int) (alphaPC * alphaPC * 130.0F * Math.min(glow, 1.5F)), 0, 255);
      if (glowAlpha <= 0) return;
      int glowColor = ColorUtil.getColor(cr, cg, cb, glowAlpha);

      float glowSize = innerRad * 2.0F * glow + 0.03F;
      int points = 64;

      Vector3f rightAxis = new Vector3f(1.0F, 0.0F, 0.0F);
      Vector3f upAxis = new Vector3f(0.0F, 1.0F, 0.0F);
      mc.gameRenderer.mainCamera().rotation().transform(rightAxis);
      mc.gameRenderer.mainCamera().rotation().transform(upAxis);

      PoseStack matrices = event.getMatrixStack();
      matrices.pushPose();
      matrices.translate(renderPos.x, renderPos.y + ringY, renderPos.z);
      Matrix4f matrix = matrices.last().pose();
      matrices.popPose();

      ByteBufferBuilder allocator = targetEspAllocator2;
      if (allocator == null) { allocator = new ByteBufferBuilder(131072); targetEspAllocator2 = allocator; }
      Immediate immediate = new Immediate();
      try {
         VertexConsumer buffer = immediate.getBuffer(RING2_GLOW_LAYER);
         for (int i = 0; i < points; i++) {
            double ang = 2.0 * Math.PI * i / points;
            float lx = (float) (Math.cos(ang) * outerRad);
            float lz = (float) (Math.sin(ang) * outerRad);
            this.appendBillboardGlowQuad(buffer, matrix, lx, 0.0F, lz, glowSize, rightAxis, upAxis, glowColor);
         }
         immediate.draw();
      } finally {
         // reused
      }
   }

   /** Геометрия тора (порт математики Relevant) для режима «Торус» TargetESP. */
   private void buildTorusRing(VertexConsumer buffer, Matrix4f m, float outerRad, float innerRad,
                               int sliceCount, int loopCount, int r, int g, int b, int alpha) {
      if (innerRad <= 0.0001F || outerRad <= 0.0001F || alpha <= 0) return;

      for (int i = 0; i < sliceCount; i++) {
         double theta = 2 * Math.PI * i / sliceCount;
         double nextTheta = 2 * Math.PI * (i + 1) / sliceCount;
         double cosT = Math.cos(theta), sinT = Math.sin(theta);
         double cosNT = Math.cos(nextTheta), sinNT = Math.sin(nextTheta);

         for (int j = 0; j < loopCount; j++) {
            double phi = 2 * Math.PI * j / loopCount;
            double nextPhi = 2 * Math.PI * (j + 1) / loopCount;
            double cosP = Math.cos(phi), sinP = Math.sin(phi);
            double cosNP = Math.cos(nextPhi), sinNP = Math.sin(nextPhi);

            // Тор лежит горизонтально (в плоскости XZ), тело поднимается по Y.
            float x1 = (float) ((outerRad + innerRad * cosT) * cosP);
            float z1 = (float) ((outerRad + innerRad * cosT) * sinP);
            float y1 = (float) (innerRad * sinT);

            float x2 = (float) ((outerRad + innerRad * cosNT) * cosP);
            float z2 = (float) ((outerRad + innerRad * cosNT) * sinP);
            float y2 = (float) (innerRad * sinNT);

            float x3 = (float) ((outerRad + innerRad * cosNT) * cosNP);
            float z3 = (float) ((outerRad + innerRad * cosNT) * sinNP);
            float y3 = (float) (innerRad * sinNT);

            float x4 = (float) ((outerRad + innerRad * cosT) * cosNP);
            float z4 = (float) ((outerRad + innerRad * cosT) * sinNP);
            float y4 = (float) (innerRad * sinT);

            buffer.addVertex(m, x1, y1, z1).setColor(r, g, b, alpha);
            buffer.addVertex(m, x2, y2, z2).setColor(r, g, b, alpha);
            buffer.addVertex(m, x3, y3, z3).setColor(r, g, b, alpha);
            buffer.addVertex(m, x4, y4, z4).setColor(r, g, b, alpha);
         }
      }
   }

   private void appendBillboardGlowQuad(VertexConsumer buffer, Matrix4f matrix, float centerX, float centerY, float centerZ, float size, Vector3f rightAxis, Vector3f upAxis, int color) {
      if (size <= 0.0F || ((color >> 24) & 0xFF) == 0) return;

      float half = size * 0.5F;
      float rx = rightAxis.x * half;
      float ry = rightAxis.y * half;
      float rz = rightAxis.z * half;
      float ux = upAxis.x * half;
      float uy = upAxis.y * half;
      float uz = upAxis.z * half;

      buffer.addVertex(matrix, centerX - rx + ux, centerY - ry + uy, centerZ - rz + uz)
              .setUv(0.0F, 1.0F)
              .setColor(color)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
      buffer.addVertex(matrix, centerX + rx + ux, centerY + ry + uy, centerZ + rz + uz)
              .setUv(1.0F, 1.0F)
              .setColor(color)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
      buffer.addVertex(matrix, centerX + rx - ux, centerY + ry - uy, centerZ + rz - uz)
              .setUv(1.0F, 0.0F)
              .setColor(color)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
      buffer.addVertex(matrix, centerX - rx - ux, centerY - ry - uy, centerZ - rz - uz)
              .setUv(0.0F, 0.0F)
              .setColor(color)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
   }

   /**
    * Режим «Ромб крутой» — теперь работает ТОЧНО как «Картинка»:
    * тот же расчёт размера (imageSize * 12 * scaleValue, кламп 30), та же
    * анимация вращения (rotProgress с SINE_BOTH), та же позиция (lastTargetPos
    * с оффсетом по lastTargetHeight), тот же drawBillboard с дистанционным
    * масштабированием и окрашиванием при ударе. Отличие только одно —
    * текстура square.png и ОДИН слой.
    */
   private void renderSquare(EventRender3D event, Immediate immediate, LivingEntity target) {
      if (this.lastTargetPos == null || this.appearValue <= 0.001F) return;

      float alphaPC = (float) alpha.getValue();
      if (alphaPC <= 0.01F) return;

      Vec3 cam = mc.gameRenderer.mainCamera().position();
      double worldX = this.lastTargetPos.x;
      double worldY = this.lastTargetPos.y + (this.lastTargetHeight + 0.4F) * 0.5F;
      double worldZ = this.lastTargetPos.z;

      double distSq = cam.distanceToSqr(worldX, worldY, worldZ);
      if (distSq > 10000.0) return;

      // Тот же расчёт размера, что и в renderMarker3D ("Картинка").
      float baseSize = (float) (double) imageSize.get() * 12.0F;
      float renderSize = baseSize * this.scaleValue;
      renderSize = Math.min(renderSize, 30.0F);

      // Тот же цикл вращения, что и в "Картинке" — единая переменная rotProgress.
      long now = System.currentTimeMillis();
      float dt = Math.max(0.001F, (float) (now - this.lastRotateUpdate) / 1000.0F);
      dt = Math.min(dt, 0.1F);
      this.lastRotateUpdate = now;
      float cycleDuration = Math.max(0.35F, 2.2F / (float) (double) rotateSpeed.get());

      for (this.rotProgress += dt / cycleDuration; this.rotProgress >= 1.0F; this.rotTo = this.rotTo > 0.0F ? -280.0F : 280.0F) {
         this.rotProgress--;
         this.rotFrom = this.rotTo;
      }

      float accel = (float) Easings.SINE_BOTH.ease(this.rotProgress);
      float rotation = Mth.lerp(accel, this.rotFrom, this.rotTo);

      // Окрашивание при ударе — как в "Картинке".
      float hurtPC = this.getHurtPC(this.lastTarget);
      int baseColor = ColorUtil.multAlpha(this.getESPColor(), this.appearValue);
      int redColor = ColorUtil.multAlpha(ColorUtil.getColor(255, 3, 3, 255), this.appearValue);
      int color = ColorUtil.overCol(baseColor, redColor, hurtPC);

      // ОДИН слой — квадратная текстура, тот же billboard, что и в "Картинке".
      VertexConsumer buf = immediate.getBuffer(IMAGE_SQUARE_LAYER);
      this.drawBillboard(buf, event.getMatrixStack(), cam, worldX, worldY, worldZ, renderSize, color, rotation);
   }

   private float easeOutCubic(float x) {
      return (float) (1 - Math.pow(1 - x, 3));
   }

   private double absSinAnimation(double step) {
      return Math.abs(Math.sin(step)) * 0.8D + 0.1D;
   }

   private int blendWithHurt(int baseColor, int hurtColor, float hurtBlend) {
      return hurtBlend <= 0.0F ? baseColor : ColorUtil.overCol(baseColor, hurtColor, hurtBlend);
   }

   private float animateTo(float current, float target, float delta) {
      if (current < target) {
         current = Math.min(current + delta, target);
      } else if (current > target) {
         current = Math.max(current - delta, target);
      }
      return current;
   }

   private float getDistanceScale(Vec3 cameraPos, double worldX, double worldY, double worldZ) {
      double dx = worldX - cameraPos.x;
      double dy = worldY - cameraPos.y;
      double dz = worldZ - cameraPos.z;
      double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (!Double.isFinite(distance)) return 0.1F;
      return (float) Mth.clamp(distance * 0.007, 0.1, 0.35);
   }

   private int getESPColor() {
      int color = ColorUtil.fade();
      if ((color >> 24 & 0xFF) == 0) {
         color |= 0xFF000000;
      }
      return color;
   }

   private int getFlowColor(int index) {
      int c1 = ColorUtil.fade();
      int c2 = ColorUtil.multDark(c1, 0.45F);
      int rgb = ColorUtil.fadeBetween(12.0F, index, c1, c2);
      return 0xFF000000 | (rgb & 0xFFFFFF);
   }

   private float getHurtPC(LivingEntity target) {
      if (hurtColor.get() && target != null) {
         float partialTicks = this.renderPartialTicks;
         float hurtTicks = Mth.clamp(target.hurtTime - partialTicks, 0.0F, 10.0F);
         float progress = hurtTicks / 10.0F;
         return progress * progress * (3.0F - 2.0F * progress);
      }
      return 0.0F;
   }

   private void drawBillboard(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos,
                              double worldX, double worldY, double worldZ, float baseScreenSize, int color, float rotation) {
      float distScale = this.getDistanceScale(cameraPos, worldX, worldY, worldZ);
      float half = baseScreenSize * distScale * 0.5F;
      this.drawBillboardInternal(buffer, matrices, cameraPos, worldX, worldY, worldZ, half, color, rotation);
   }

   private void drawBillboardInternal(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos,
                                      double worldX, double worldY, double worldZ, float half, int color, float rotation) {
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      int a = color >> 24 & 0xFF;
      if (a <= 0) return;
      if (!Float.isFinite(half) || half <= 0.0F || half > 5.0F) return;

      matrices.pushPose();
      matrices.translate(worldX - cameraPos.x, worldY - cameraPos.y, worldZ - cameraPos.z);
      matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
      matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));
      if (rotation != 0.0F) {
         matrices.rotate(Axis.ZP.rotationDegrees(rotation));
      }
      Matrix4f matrix = matrices.last().pose();
      texVertex(buffer, matrix, -half, -half, 0.0F, 0.0F, 1.0F, r, g, b, a);
      texVertex(buffer, matrix, -half, half, 0.0F, 0.0F, 0.0F, r, g, b, a);
      texVertex(buffer, matrix, half, half, 0.0F, 1.0F, 0.0F, r, g, b, a);
      texVertex(buffer, matrix, half, -half, 0.0F, 1.0F, 1.0F, r, g, b, a);
      matrices.popPose();
   }

   private static void texVertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z,
                                 float u, float v, int r, int g, int b, int a) {
      buffer.addVertex(matrix, x, y, z)
              .setColor(r, g, b, a)
              .setUv(u, v)
              .setOverlay(OverlayTexture.NO_OVERLAY)
              .setLight(15728880)
              .setNormal(0.0F, 0.0F, 1.0F);
   }

   private void renderMarker3D(EventRender3D event, Immediate immediate, LivingEntity target, boolean hasTarget) {
      this.markerImmediate = immediate;
      if (this.lastTargetPos == null || this.appearValue <= 0.001F) return;
      if (this.lastTarget != null && (!this.lastTarget.isAlive() || this.lastTarget.isRemoved())) {
         this.appearValue = Math.max(0.0F, this.appearValue - 0.08F);
         if (this.appearValue <= 0.001F) return;
      }

      Vec3 cam = mc.gameRenderer.mainCamera().position();
      double worldX = this.lastTargetPos.x;
      double worldY = this.lastTargetPos.y + (this.lastTargetHeight + 0.4F) * 0.5F;
      double worldZ = this.lastTargetPos.z;

      double distSq = cam.distanceToSqr(worldX, worldY, worldZ);
      if (distSq > 10000.0) return;

      float baseSize = (float) (double) imageSize.get() * 12.0F;
      float renderSize = baseSize * this.scaleValue;
      renderSize = Math.min(renderSize, 30.0F);

      long now = System.currentTimeMillis();
      float dt = Math.max(0.001F, (float) (now - this.lastRotateUpdate) / 1000.0F);
      dt = Math.min(dt, 0.1F);
      this.lastRotateUpdate = now;
      float cycleDuration = Math.max(0.35F, 2.2F / (float) (double) rotateSpeed.get());

      for (this.rotProgress += dt / cycleDuration; this.rotProgress >= 1.0F; this.rotTo = this.rotTo > 0.0F ? -280.0F : 280.0F) {
         this.rotProgress--;
         this.rotFrom = this.rotTo;
      }

      float accel = (float) Easings.SINE_BOTH.ease(this.rotProgress);
      float rotation = Mth.lerp(accel, this.rotFrom, this.rotTo);
      float hurtPC = this.getHurtPC(this.lastTarget);
      int baseColor = ColorUtil.multAlpha(this.getESPColor(), this.appearValue);
      int redColor = ColorUtil.multAlpha(ColorUtil.getColor(255, 3, 3, 255), this.appearValue);
      int color = ColorUtil.overCol(baseColor, redColor, hurtPC);
      boolean image4 = typeImage.is("Картинка 4");
      if (image4) {
         color = ColorUtil.overCol(color, ColorUtil.multAlpha(0xFFFFFFFF, this.appearValue), 0.2F);
      }

      if (image4) {
         int glowColor = ColorUtil.overCol(ColorUtil.multAlpha(this.getESPColor(), this.appearValue),
                 ColorUtil.multAlpha(0xFFFFFFFF, this.appearValue), 0.2F);
         VertexConsumer glowBuf = this.markerBuffer(GLOW_NO_DEPTH_LAYER);
         this.drawBillboard(glowBuf, event.getMatrixStack(), cam, worldX, worldY, worldZ, renderSize * 1.25F,
                 ColorUtil.multAlpha(glowColor, 0.2F), rotation);
      }

      VertexConsumer imgBuf = this.markerBuffer(this.imageLayer());
      this.drawBillboard(imgBuf, event.getMatrixStack(), cam, worldX, worldY, worldZ, renderSize, color, rotation);
      this.hitBubbleLastMarkerPos = new Vec3(worldX, worldY, worldZ);
   }

   private VertexConsumer markerBuffer(RenderType layer) {
      return this.markerImmediate.getBuffer(layer);
   }

   private Immediate markerImmediate;

   private boolean isHitBubbleActive() {
      return hitBubbleEffect.get() && typeTargetEsp.is("Картинка");
   }

   private vesence.utils.render.HitBubbleEffect.ColorMode hitBubbleColorMode() {
      if (hitBubbleColor.is("Радужный")) {
         return vesence.utils.render.HitBubbleEffect.ColorMode.RAINBOW;
      }
      return vesence.utils.render.HitBubbleEffect.ColorMode.CLIENT;
   }

   private int hitBubbleVoronoiPoints() {
      if (hitBubbleVoronoiPriority.is("Производительность")) return 4;
      if (hitBubbleVoronoiPriority.is("Сбалансированное")) return 16;
      if (hitBubbleVoronoiPriority.is("Множество")) return 45;
      if (hitBubbleVoronoiPriority.is("Ультра")) return 170;
      return 16;
   }

   private void tickHitBubbleEffect(boolean hasTarget) {
      if (!this.isHitBubbleActive()) {
         this.hitBubbleShatter.clear();
         this.hitBubbleShatterEmitted = false;
         return;
      }
      if (hasTarget) {
         this.hitBubbleShatterEmitted = false;
      } else {
         if (!this.hitBubbleShatterEmitted && this.hitBubbleLastMarkerPos != null
                 && mc.gameRenderer != null && mc.gameRenderer.mainCamera() != null) {
            float yaw = mc.gameRenderer.mainCamera().yRot();
            float pitch = mc.gameRenderer.mainCamera().xRot();
            this.hitBubbleShatter.spawn(this.hitBubbleLastMarkerPos, yaw, pitch, 1200.0f);
            this.hitBubbleShatterEmitted = true;
         }
      }
   }

   private void renderHitBubbleShatter(EventRender3D event) {
      if (!this.isHitBubbleActive()) return;
      if (this.hitBubbleShatter.isEmpty()) return;
      if (mc.gameRenderer == null || mc.gameRenderer.mainCamera() == null) return;
      Vec3 cam = mc.gameRenderer.mainCamera().position();
      int themeColor = this.getESPColor();
      int themeColor2 = ColorUtil.fade(90);

      if (targetEspAllocQuad == null) { targetEspAllocQuad = new ByteBufferBuilder(1048576); }
      if (targetEspAllocFill == null) { targetEspAllocFill = new ByteBufferBuilder(1048576); }
      Immediate imQuad = new Immediate();
      Immediate imFill = new Immediate();
      try {
         VertexConsumer quad = imQuad.getBuffer(this.bubbleQuadLayer());
         VertexConsumer fill = imFill.getBuffer(this.bubbleFillLayer());
         // line == null → белый каркас осколков отключён (полный порт harmony).
         this.hitBubbleShatter.render(event.getMatrixStack(), cam, quad, fill, null,
                 1.0f, this.hitBubbleColorMode(), themeColor, themeColor2,
                 hitBubbleVoronoi.get(), this.hitBubbleVoronoiPoints());
         imFill.draw();
         imQuad.draw();
      } finally {
         // reused
      }
   }

   private void drawChainsV2(EventRender3D event, Immediate immediate, LivingEntity target) {
      if (target == null || this.appearValue <= 0.01F) {
         return;
      }
      float delta = event.getTickDelta();
      Vec3 camPos = mc.gameRenderer.mainCamera().position();
      double tX = Mth.lerp(delta, target.xOld, target.getX()) - camPos.x;
      double tY = Mth.lerp(delta, target.yOld, target.getY()) - camPos.y;
      double tZ = Mth.lerp(delta, target.zOld, target.getZ()) - camPos.z;
      float hurtFactor = this.getHurtPC(target);
      float animationTime = target.tickCount + delta;
      float baseRadius = Math.max(0.2F, target.getBbWidth() * 0.90F * CHAINS_SCALE);
      float bandHeight = Math.max(0.35F, target.getBbHeight() * 0.45F * CHAINS_SCALE);
      float compressedRadius = baseRadius;
      float primaryBandHeight = bandHeight;
      float secondaryBandHeight = bandHeight * 0.8F;
      float roll = Mth.sin(animationTime * 0.22F) * 18.0F;
      float pitch = Mth.cos(animationTime * 0.18F) * 18.0F;
      float spin = animationTime * 5.0F;
      int red = ColorUtil.getColor(255, 0, 0, 255);
      int startColor = ColorUtil.overCol(this.getESPColor(), red, hurtFactor * 0.95F);
      int endColor = ColorUtil.overCol(ColorUtil.fade(90), red, hurtFactor * 0.95F);

      VertexConsumer buffer = immediate.getBuffer(CHAIN_LAYER);
      PoseStack matrices = event.getMatrixStack();
      matrices.pushPose();
      matrices.translate(tX, tY + target.getBbHeight() * 0.52, tZ);
      this.renderChainBandV2(buffer, matrices, compressedRadius * 0.9F, primaryBandHeight, roll, pitch, spin, this.appearValue, startColor, endColor, 0.0F);
      this.renderChainBandV2(buffer, matrices, compressedRadius * 0.98F, secondaryBandHeight, -roll, -pitch, -spin * 0.92F, this.appearValue * 0.92F, endColor, startColor, 0.38F);
      matrices.popPose();
   }

   private void renderChainBandV2(VertexConsumer buffer, PoseStack matrices, float radius, float bandHeight,
                                  float roll, float pitch, float spin, float alpha, int startColor, int endColor, float uvOffset) {
      matrices.pushPose();
      matrices.rotate(Axis.ZP.rotationDegrees(roll));
      matrices.rotate(Axis.XP.rotationDegrees(pitch));
      Matrix4f matrix = matrices.last().pose();
      float bottomY = -bandHeight * 0.5F;
      float topY = bandHeight * 0.5F;
      int segments = 64;
      float uvRepeats = 4.0F;

      for (int i = 0; i < segments; i++) {
         float progress0 = (float) i / segments;
         float progress1 = (float) (i + 1) / segments;
         float angle0 = progress0 * 360.0F + spin;
         float angle1 = progress1 * 360.0F + spin;
         float rad0 = (float) Math.toRadians(angle0);
         float rad1 = (float) Math.toRadians(angle1);
         float x0 = Mth.sin(rad0) * radius;
         float z0 = Mth.cos(rad0) * radius;
         float x1 = Mth.sin(rad1) * radius;
         float z1 = Mth.cos(rad1) * radius;
         float wobble0 = Mth.sin(progress0 * (float) (Math.PI * 2) + spin * 0.05F) * 0.06F;
         float wobble1 = Mth.sin(progress1 * (float) (Math.PI * 2) + spin * 0.05F) * 0.06F;
         int color0 = ColorUtil.multAlpha(ColorUtil.interpolate(startColor, endColor, progress0), alpha);
         int color1 = ColorUtil.multAlpha(ColorUtil.interpolate(startColor, endColor, progress1), alpha);
         float u0 = progress0 * uvRepeats + uvOffset;
         float u1 = progress1 * uvRepeats + uvOffset;
         int r0 = color0 >> 16 & 0xFF, g0 = color0 >> 8 & 0xFF, b0 = color0 & 0xFF, a0 = color0 >> 24 & 0xFF;
         int r1 = color1 >> 16 & 0xFF, g1 = color1 >> 8 & 0xFF, b1 = color1 & 0xFF, a1 = color1 >> 24 & 0xFF;
         texVertex(buffer, matrix, x0, bottomY + wobble0, z0, u0, 0.0F, r0, g0, b0, a0);
         texVertex(buffer, matrix, x1, bottomY + wobble1, z1, u1, 0.0F, r1, g1, b1, a1);
         texVertex(buffer, matrix, x1, topY + wobble1, z1, u1, 1.0F, r1, g1, b1, a1);
         texVertex(buffer, matrix, x0, topY + wobble0, z0, u0, 1.0F, r0, g0, b0, a0);
      }
      matrices.popPose();
   }

   private void drawGhostOrbits3D(EventRender3D event, Immediate immediate, LivingEntity target) {
      if (target == null || this.appearValue <= 0.001F || this.lastTargetPos == null) return;

      if (this.orbitLastTarget != target) {
         for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            this.orbitPositions[i] = null;
            this.orbitMotions[i] = Vec3.ZERO;
            this.orbitTrails[i].clear();
         }
         this.orbitLastTarget = target;
      }

      PoseStack matrices = event.getMatrixStack();
      Vec3 camPos = mc.gameRenderer.mainCamera().position();
      float partialTicks = event.getTickDelta();

      double tx = Mth.lerp(partialTicks, target.xOld, target.getX());
      double ty = Mth.lerp(partialTicks, target.yOld, target.getY());
      double tz = Mth.lerp(partialTicks, target.zOld, target.getZ());
      Vec3 targetCenter = new Vec3(tx, ty + target.getBbHeight() / 2.0, tz);

      long now = System.currentTimeMillis();
      if (this.orbitLastTime == 0L) this.orbitLastTime = now;
      float dtMs = now - this.orbitLastTime;
      this.orbitLastTime = now;
      float fpsFactor = 500.0F / Math.max(mc.getFps(), 10);
      this.orbitMovingAngle += (20.0F * dtMs / 16.667F) * (ORBIT_SPEED / 55.0F);

      boolean isHurt = target.hurtTime > 7;
      float shrinkTarget = isHurt ? 1.0F : 0.0F;
      this.orbitShrinkValue = this.animateTo(this.orbitShrinkValue, shrinkTarget, 0.06F);

      VertexConsumer buffer = immediate.getBuffer(ESP_BLOOM_NO_DEPTH_LAYER);
      int baseColor = this.getESPColor();
      float hurtPC = this.getHurtPC(target);
      int hurtRed = ColorUtil.getColor(255, 3, 3, 255);

      for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
         float angleOffset = i * 360.0F / ORBIT_PARTICLE_COUNT;
         float currentAngle = this.orbitMovingAngle + angleOffset;
         double radian = Math.toRadians(currentAngle);
         float orbitRadius = ORBIT_BASE_RADIUS - this.orbitShrinkValue * ORBIT_BASE_RADIUS;
         float ox = (float) Math.sin(radian) * orbitRadius;
         float oz = (float) Math.cos(radian) * orbitRadius;
         double oy = 0.3 * Math.sin(Math.toRadians(this.orbitMovingAngle / (i + 1.0F)));

         Vec3 targetGhostPos = targetCenter.add(ox, oy, oz);
         if (this.orbitPositions[i] == null || this.orbitPositions[i].distanceTo(targetGhostPos) > 10) {
            this.orbitPositions[i] = targetGhostPos;
            this.orbitMotions[i] = Vec3.ZERO;
         }

         float mul = ORBIT_BASE_MUL * fpsFactor;
         Vec3 diff = targetGhostPos.subtract(this.orbitPositions[i]);
         this.orbitMotions[i] = diff.multiply(mul, mul, mul);
         this.orbitPositions[i] = this.orbitPositions[i].add(this.orbitMotions[i]);

         java.util.List<Vec3> trail = this.orbitTrails[i];
         if (trail.isEmpty() || trail.get(0).distanceTo(this.orbitPositions[i]) > 0.01) {
            trail.add(0, this.orbitPositions[i]);
            while (trail.size() > ORBIT_TRAIL_LENGTH) trail.remove(trail.size() - 1);
         }

         int trailSize = trail.size();
         for (int j = 0; j < trailSize; j++) {
            Vec3 p = trail.get(j);
            float offset = 1.0F - (float) j / ORBIT_TRAIL_LENGTH;
            float opacity = (float) Math.pow(offset, 1.8) * this.appearValue * 0.7F;
            int color = ColorUtil.replAlpha(baseColor, (int) (opacity * 255));
            color = ColorUtil.overCol(color, ColorUtil.replAlpha(hurtRed, (int) (opacity * 255)), hurtPC);
            int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF, a = color >> 24 & 0xFF;
            float scale = ORBIT_SCALE_CACHE[Math.min((int) (offset * 100), 100)] * 0.8F;

            matrices.pushPose();
            matrices.translate(p.x - camPos.x, p.y - camPos.y, p.z - camPos.z);
            matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
            matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));
            Matrix4f matrix = matrices.last().pose();
            texVertex(buffer, matrix, -scale, scale, 0.0F, 0.0F, 1.0F, r, g, b, a);
            texVertex(buffer, matrix, scale, scale, 0.0F, 1.0F, 1.0F, r, g, b, a);
            texVertex(buffer, matrix, scale, -scale, 0.0F, 1.0F, 0.0F, r, g, b, a);
            texVertex(buffer, matrix, -scale, -scale, 0.0F, 0.0F, 0.0F, r, g, b, a);
            matrices.popPose();
         }

         if (!trail.isEmpty()) {
            Vec3 head = trail.get(0);
            float headScale = 0.35F * this.appearValue;
            int headBase = ColorUtil.replAlpha(baseColor, (int) (120 * this.appearValue));
            int headColor = ColorUtil.overCol(headBase, ColorUtil.replAlpha(hurtRed, (int) (120 * this.appearValue)), hurtPC);
            int r = headColor >> 16 & 0xFF, g = headColor >> 8 & 0xFF, b = headColor & 0xFF, a = headColor >> 24 & 0xFF;
            matrices.pushPose();
            matrices.translate(head.x - camPos.x, head.y - camPos.y, head.z - camPos.z);
            matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
            matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));
            Matrix4f matrix = matrices.last().pose();
            texVertex(buffer, matrix, -headScale, headScale, 0.0F, 0.0F, 1.0F, r, g, b, a);
            texVertex(buffer, matrix, headScale, headScale, 0.0F, 1.0F, 1.0F, r, g, b, a);
            texVertex(buffer, matrix, headScale, -headScale, 0.0F, 1.0F, 0.0F, r, g, b, a);
            texVertex(buffer, matrix, -headScale, -headScale, 0.0F, 0.0F, 0.0F, r, g, b, a);
            matrices.popPose();
         }
      }
   }

   private void addBMWGhosts(LivingEntity entity, int cornersCount, int maxTime, int colorBase) {
      float xzRange = 0.7F;
      float yRange = entity.getBbHeight();
      int delayXZ = (int) (double) bmwStrengthXZ.get();
      int delayY = (int) (double) bmwStrengthY.get();
      long time = System.currentTimeMillis();
      float rotateProgress = (float) (time % delayXZ) / delayXZ;
      float xzRotate = rotateProgress * 360.0F;
      float yProgress = (float) (time % delayY) / delayY;
      float yLrpPC = 0.5F - 0.5F * Mth.cos(yProgress * (float) (Math.PI * 2));

      for (int corner = 0; corner < cornersCount; corner++) {
         float cornersPC = (float) corner / cornersCount;
         double yawRad = Math.toRadians(Mth.wrapDegrees(cornersPC * 360.0F + xzRotate));
         float offsetX = -((float) Math.sin(yawRad)) * xzRange;
         float offsetY = yRange * yLrpPC;
         float offsetZ = (float) Math.cos(yawRad) * xzRange;
         this.bmwPoints.add(new GlowPoint(offsetX, offsetY, offsetZ, maxTime, colorBase));
      }
   }

   private void drawBMW3D(EventRender3D event, Immediate immediate) {
      if (this.bmwPoints.isEmpty() || this.appearValue <= 0.001F) return;
      LivingEntity renderTarget = this.lastTarget != null ? this.lastTarget : this.lastHandledTarget;
      if (renderTarget == null && this.lastTargetPos == null) return;

      float partialTicks = event.getTickDelta();
      Vec3 basePos;
      if (renderTarget != null && renderTarget.isAlive()) {
         basePos = new Vec3(
                 Mth.lerp(partialTicks, renderTarget.xOld, renderTarget.getX()),
                 Mth.lerp(partialTicks, renderTarget.yOld, renderTarget.getY()),
                 Mth.lerp(partialTicks, renderTarget.zOld, renderTarget.getZ()));
      } else {
         basePos = this.lastTargetPos;
      }
      if (basePos == null) return;

      Vec3 cam = mc.gameRenderer.mainCamera().position();
      float hurtPC = this.getHurtPC(renderTarget);
      float fixedScreenSize = 6.0F;
      VertexConsumer buffer = immediate.getBuffer(ESP_BLOOM_NO_DEPTH_LAYER);
      PoseStack matrices = event.getMatrixStack();
      int hurtRed = ColorUtil.getColor(255, 3, 3, 255);

      for (GlowPoint point : this.bmwPoints) {
         float timePC = point.getTimeProgress();
         float trailFactor = 1.0F - timePC * 0.6F;
         double worldX = basePos.x + point.x;
         double worldY = basePos.y + point.y;
         double worldZ = basePos.z + point.z;
         float sz = fixedScreenSize * trailFactor;
         int alphaI = (int) (255.0F * this.appearValue * trailFactor * 0.8F);
         alphaI = Math.max(0, Math.min(255, alphaI));
         int col = ColorUtil.replAlpha(point.baseColor, alphaI);
         int redC = ColorUtil.replAlpha(hurtRed, alphaI);
         int finalColor = ColorUtil.overCol(col, redC, hurtPC);
         this.drawBillboard(buffer, matrices, cam, worldX, worldY, worldZ, sz, finalColor, 0.0F);
      }
   }

   private void drawCompass3D(EventRender3D event, Immediate immediate, LivingEntity target) {
      if (target == null || this.appearValue <= 0.001F || this.lastTargetPos == null) return;
      PoseStack matrices = event.getMatrixStack();
      Vec3 camPos = mc.gameRenderer.mainCamera().position();
      float partialTicks = event.getTickDelta();
      double x = Mth.lerp(partialTicks, target.xOld, target.getX()) - camPos.x;
      double y = Mth.lerp(partialTicks, target.yOld, target.getY()) - camPos.y;
      double z = Mth.lerp(partialTicks, target.zOld, target.getZ()) - camPos.z;
      float height = target.getBbHeight();

      final double FLOOR_OFFSET = 0.15;
      short period = 1500;
      double t = System.currentTimeMillis() % (long) period;
      boolean ascending = t > (period / 2);
      float progress = (float) (t / (period / 2.0));
      if (ascending) progress -= 1.0F;
      else progress = 1.0F - progress;
      progress = progress < 0.5F ? 2.0F * progress * progress :
              (float) (1.0 - Math.pow(-2.0F * progress + 2.0F, 2.0) / 2.0);
      double yOffset = height / 2.0F * (progress > 0.5F ? 1.0F - progress : progress) * (ascending ? -1 : 1);

      matrices.pushPose();
      matrices.translate(x, y + height * progress + yOffset + FLOOR_OFFSET, z);

      float hurtPC = this.getHurtPC(target);
      float hurtTime = Mth.clamp((target.hurtTime - (target.hurtTime != 0 ? partialTicks : 0.0F)) / 10.0F, 0.0F, 1.0F);
      long timeMs = (long) ((System.currentTimeMillis() - this.compassTimestamp4) / 2.5F);
      long nanoTime = System.nanoTime();
      float deltaTime = (nanoTime - this.compassTimestamp5) / 2_000_000.0F;
      this.compassTimestamp5 = nanoTime;
      this.compassValue23 += hurtTime * deltaTime;

      VertexConsumer buffer = immediate.getBuffer(ESP_BLOOM_LAYER);
      int baseColor = this.getESPColor();
      int hurtRed = ColorUtil.getColor(255, 3, 3, 255);

      for (int layer = 0; layer < 4; layer++) {
         for (int i = 0; i < 15; i++) {
            matrices.pushPose();
            float particleProgress = i / 14.0F;
            float sizeP = (0.5F * (1.0F - particleProgress) + 0.5F * particleProgress) * this.appearValue;
            float angle = 0.2F * (timeMs + this.compassValue23 - i * 3.5F) / 15.0F;
            boolean firstHalf = particleProgress < 0.5F;
            float wave = firstHalf ? particleProgress * 2.0F : (1.0F - particleProgress) * 2.0F;
            double amplitude = Math.sin(wave * Math.PI) * 2.0;
            java.util.Random random = new java.util.Random(i * 12345L);
            double offsetX = (random.nextDouble() - 0.5) * amplitude;
            double offsetY = (random.nextDouble() - 0.5) * amplitude;
            double offsetZ = (random.nextDouble() - 0.5) * amplitude;
            double animOffsetX = offsetX * this.appearValue - offsetX;
            double animOffsetY = offsetY * this.appearValue - offsetY;
            double animOffsetZ = offsetZ * this.appearValue - offsetZ;
            double radius = 0.7;

            switch (layer) {
               case 0:
                  matrices.translate(Math.cos(angle) * radius + animOffsetX, animOffsetY, Math.sin(angle) * radius + animOffsetZ);
                  break;
               case 1:
                  matrices.translate(-Math.sin(angle) * radius + animOffsetX, animOffsetY, Math.cos(angle) * radius + animOffsetZ);
                  break;
               case 2:
                  matrices.translate(-Math.cos(angle) * radius + animOffsetX, animOffsetY, -Math.sin(angle) * radius + animOffsetZ);
                  break;
               case 3:
                  matrices.translate(Math.sin(angle) * radius + animOffsetX, animOffsetY, -Math.cos(angle) * radius + animOffsetZ);
                  break;
            }

            float particleSize = sizeP * 0.5F;
            int particleColor = ColorUtil.replAlpha(baseColor, (int) (160 * this.appearValue));
            particleColor = ColorUtil.overCol(particleColor, ColorUtil.replAlpha(hurtRed, (int) (160 * this.appearValue)), hurtPC);
            int r = particleColor >> 16 & 0xFF, g = particleColor >> 8 & 0xFF, b = particleColor & 0xFF, a = particleColor >> 24 & 0xFF;

            matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
            matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));
            Matrix4f matrix = matrices.last().pose();
            texVertex(buffer, matrix, -particleSize, -particleSize, 0.0F, 1.0F, 1.0F, r, g, b, a);
            texVertex(buffer, matrix, particleSize, -particleSize, 0.0F, 0.0F, 1.0F, r, g, b, a);
            texVertex(buffer, matrix, particleSize, particleSize, 0.0F, 0.0F, 0.0F, r, g, b, a);
            texVertex(buffer, matrix, -particleSize, particleSize, 0.0F, 1.0F, 0.0F, r, g, b, a);
            matrices.popPose();
         }
      }
      matrices.popPose();
   }

   private void drawSkull3D(EventRender3D event, Immediate immediate, LivingEntity target) {
      if (target == null || this.appearValue <= 0.001F) return;

      PoseStack matrices = event.getMatrixStack();
      Vec3 cam = mc.gameRenderer.mainCamera().position();
      float camYaw = mc.gameRenderer.mainCamera().yRot();
      float camPitch = mc.gameRenderer.mainCamera().xRot();
      float partialTicks = event.getTickDelta();
      double tx = Mth.lerp(partialTicks, target.xOld, target.getX());
      double ty = Mth.lerp(partialTicks, target.yOld, target.getY()) + target.getBbHeight() / 2.0;
      double tz = Mth.lerp(partialTicks, target.zOld, target.getZ());

      long now = System.currentTimeMillis();
      if (this.lastSkullTime == 0L) this.lastSkullTime = now;
      float dt = (float) (now - this.lastSkullTime) / 1000.0F;
      this.lastSkullTime = now;

      float hpPC = target.getMaxHealth() > 0.0F ? target.getHealth() / target.getMaxHealth() : 1.0F;
      int state = hpPC >= 0.66F ? 0 : (hpPC >= 0.33F ? 1 : 2);
      if (this.skullCurrentState == -1) {
         this.skullCurrentState = state;
         this.skullNextState = state;
         this.skullFadeProgress = 1.0F;
      }
      if (state != this.skullCurrentState && state != this.skullNextState) {
         this.skullNextState = state;
         this.skullFadeProgress = 0.0F;
         this.skullShakeStart = now;
      }
      if (this.skullFadeProgress < 1.0F) {
         this.skullFadeProgress = Math.min(1.0F, this.skullFadeProgress + dt / 0.3F);
         if (this.skullFadeProgress >= 1.0F) this.skullCurrentState = this.skullNextState;
      }

      float shakeX = 0.0F;
      float shakeY = 0.0F;
      long shakeAge = now - this.skullShakeStart;
      if (this.skullShakeStart > 0L && shakeAge < 400L) {
         float k = 1.0F - (float) shakeAge / 400.0F;
         shakeX = (float) (Math.sin(shakeAge / 22.0) * 0.1 * k);
         shakeY = (float) (Math.cos(shakeAge / 17.0) * 0.1 * k);
      }

      double rx = tx - cam.x + shakeX;
      double ry = ty - cam.y + shakeY;
      double rz = tz - cam.z;
      float scale = (1.0F - this.appearValue * 0.2F) * 0.95F;

      int theme = this.getFlowColor(0);
      float r = Math.min(1.0F, (theme >> 16 & 0xFF) / 255.0F * 1.5F);
      float g = Math.min(1.0F, (theme >> 8 & 0xFF) / 255.0F * 1.5F);
      float b = Math.min(1.0F, (theme & 0xFF) / 255.0F * 1.5F);

      if (this.skullFadeProgress < 1.0F) {
         VertexConsumer b1 = immediate.getBuffer(this.skullLayer(this.skullCurrentState));
         this.drawSkullQuad(b1, matrices, rx, ry, rz, scale, r, g, b, this.appearValue * (1.0F - this.skullFadeProgress), camYaw, camPitch);
      }
      VertexConsumer b2 = immediate.getBuffer(this.skullLayer(this.skullNextState));
      this.drawSkullQuad(b2, matrices, rx, ry, rz, scale, r, g, b, this.appearValue * this.skullFadeProgress, camYaw, camPitch);
   }

   private void drawSkullQuad(VertexConsumer buffer, PoseStack matrices, double x, double y, double z,
                              float size, float r, float g, float b, float alpha, float camYaw, float camPitch) {
      matrices.pushPose();
      matrices.translate(x, y, z);
      matrices.rotate(Axis.YP.rotationDegrees(-camYaw));
      matrices.rotate(Axis.XP.rotationDegrees(camPitch));
      Matrix4f matrix = matrices.last().pose();
      float half = size / 2.0F;
      int ri = (int) (r * 255.0F);
      int gi = (int) (g * 255.0F);
      int bi = (int) (b * 255.0F);
      int ai = (int) (alpha * 255.0F);
      texVertex(buffer, matrix, -half, -half, 0.0F, 0.0F, 1.0F, ri, gi, bi, ai);
      texVertex(buffer, matrix, half, -half, 0.0F, 1.0F, 1.0F, ri, gi, bi, ai);
      texVertex(buffer, matrix, half, half, 0.0F, 1.0F, 0.0F, ri, gi, bi, ai);
      texVertex(buffer, matrix, -half, half, 0.0F, 0.0F, 0.0F, ri, gi, bi, ai);
      matrices.popPose();
   }

   private void renderPentagram3D(EventRender3D event, Immediate immediate, LivingEntity target) {
      if (target == null || !target.isAlive()) {
         if (this.lastTargetPos == null || this.appearValue <= 0.001F) return;
      }
      float show = Mth.clamp(this.appearValue, 0.0F, 1.0F);
      if (show <= 0.001F) return;

      float partialTicks = event.getTickDelta();
      PoseStack matrices = event.getMatrixStack();
      Vec3 cameraPos = mc.gameRenderer.mainCamera().position();

      double x, y, z;
      float targetWidth;
      if (target != null && target.isAlive()) {
         x = Mth.lerp(partialTicks, target.xOld, target.getX());
         y = Mth.lerp(partialTicks, target.yOld, target.getY()) + 0.01;
         z = Mth.lerp(partialTicks, target.zOld, target.getZ());
         targetWidth = target.getBbWidth();
      } else {
         x = this.lastTargetPos.x;
         y = this.lastTargetPos.y + 0.01;
         z = this.lastTargetPos.z;
         targetWidth = this.lastTargetWidth;
      }

      float hurtPC = this.getHurtPC(target);
      float speed = Math.max(0.1F, (float) (double) pentagramSpeed.get());
      float ticks = mc.player != null ? mc.player.tickCount + partialTicks : partialTicks;
      float rotation = ticks * 18.0F * speed;
      float pulse = 1.0F + 0.06F * Mth.sin(ticks * 0.12F);
      float scale = (0.82F + targetWidth * 0.55F) * show * pulse;

      VertexConsumer circleBuffer = immediate.getBuffer(PENTAGRAM_CIRCLE_LAYER);
      this.renderPentagramGlow(circleBuffer, matrices, cameraPos, x, y, z, rotation, scale * 1.60F, show * 1.05F, hurtPC);
      this.renderPentagramTextureLayer(circleBuffer, matrices, cameraPos, x, y, z, rotation, scale * 0.80F, show, hurtPC);

      this.updateAndSpawnPentagramParticles(target, scale * 0.80F);
      VertexConsumer bloomBuffer = immediate.getBuffer(ESP_BLOOM_LAYER);
      this.renderPentagramParticles(bloomBuffer, matrices, cameraPos, partialTicks, show, hurtPC);
   }

   private void updateAndSpawnPentagramParticles(LivingEntity target, float pentagramRadius) {
      long now = System.currentTimeMillis();
      if (this.lastPentagramParticleTime == 0L) {
         this.lastPentagramParticleTime = now;
      }
      float dtMs = Math.min((float) (now - this.lastPentagramParticleTime), 50.0F);
      this.lastPentagramParticleTime = now;
      float maxHeight = target != null ? target.getBbHeight() * 2.2F : 3.5F;

      this.pentagramParticles.removeIf(p -> {
         p.update(dtMs, now, target, maxHeight);
         if (p.shouldRemove(now)) {
            this.pentagramPool.offerFirst(p);
            return true;
         }
         return false;
      });

      if (target != null && target.isAlive() && this.appearValue > 0.1F && this.pentagramParticles.size() < MAX_PENTAGRAM_PARTICLES) {
         int spawnCount = (int) (dtMs / PENTAGRAM_PARTICLE_SPAWN_INTERVAL_MS);
         spawnCount = Math.max(1, Math.min(spawnCount, 4));
         for (int i = 0; i < spawnCount && this.pentagramParticles.size() < MAX_PENTAGRAM_PARTICLES; i++) {
            this.spawnPentagramParticle(target, pentagramRadius);
         }
      }
   }

   private void spawnPentagramParticle(LivingEntity target, float pentagramRadius) {
      double angle = Math.random() * Math.PI * 2.0;
      double dist = Math.sqrt(Math.random()) * pentagramRadius * 0.9F;
      double offsetX = Math.cos(angle) * dist;
      double offsetZ = Math.sin(angle) * dist;
      double offsetY = 0.02;
      float vy = 0.028F + (float) Math.random() * 0.022F;
      float vx = (float) ((Math.random() - 0.5) * 0.004);
      float vz = (float) ((Math.random() - 0.5) * 0.004);
      float tangentSpeed = 0.003F + (float) Math.random() * 0.003F;
      vx += (float) (-Math.sin(angle) * tangentSpeed);
      vz += (float) (Math.cos(angle) * tangentSpeed);
      float sizeP = 0.04F + (float) Math.random() * 0.06F;
      int maxLife = 800 + (int) (Math.random() * 600);
      int colorSeed = (int) (Math.random() * 360);

      PentParticle particle = this.pentagramPool.pollFirst();
      if (particle == null) {
         particle = new PentParticle(target, offsetX, offsetY, offsetZ, vx, vy, vz, sizeP, maxLife, colorSeed);
      } else {
         particle.init(target, offsetX, offsetY, offsetZ, vx, vy, vz, sizeP, maxLife, colorSeed);
      }
      this.pentagramParticles.add(particle);
   }

   private void renderPentagramParticles(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos,
                                         float partialTicks, float globalAlpha, float hurtPC) {
      if (this.pentagramParticles.isEmpty()) return;
      long now = System.currentTimeMillis();

      for (PentParticle particle : this.pentagramParticles) {
         float alpha = particle.getAlpha(now) * globalAlpha;
         if (alpha <= 0.01F) continue;

         double px = particle.getRenderX(partialTicks) - cameraPos.x;
         double py = particle.getRenderY(partialTicks) - cameraPos.y;
         double pz = particle.getRenderZ(partialTicks) - cameraPos.z;

         int color = this.pentagramColorHurt(alpha * 0.85F, hurtPC);
         int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF, a = color >> 24 & 0xFF;
         float sizeP = particle.size * (0.6F + alpha * 0.4F);

         matrices.pushPose();
         matrices.translate(px, py, pz);
         matrices.rotate(Axis.YP.rotationDegrees(-mc.gameRenderer.mainCamera().yRot()));
         matrices.rotate(Axis.XP.rotationDegrees(mc.gameRenderer.mainCamera().xRot()));
         Matrix4f matrix = matrices.last().pose();
         texVertex(buffer, matrix, -sizeP, -sizeP, 0.0F, 0.0F, 0.0F, r, g, b, a);
         texVertex(buffer, matrix, sizeP, -sizeP, 0.0F, 1.0F, 0.0F, r, g, b, a);
         texVertex(buffer, matrix, sizeP, sizeP, 0.0F, 1.0F, 1.0F, r, g, b, a);
         texVertex(buffer, matrix, -sizeP, sizeP, 0.0F, 0.0F, 1.0F, r, g, b, a);
         matrices.popPose();
      }
   }

   private void renderPentagramTextureLayer(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos,
                                            double x, double y, double z, float rotation, float scale, float alpha, float hurtPC) {
      if (scale <= 0.01F || alpha <= 0.01F) return;
      int color = this.pentagramColorHurt(alpha, hurtPC);
      int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF, a = color >> 24 & 0xFF;

      matrices.pushPose();
      matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
      matrices.rotate(Axis.XP.rotationDegrees(90.0F));
      matrices.rotate(Axis.ZP.rotationDegrees(rotation));
      matrices.scale(scale, scale, scale);
      Matrix4f matrix = matrices.last().pose();
      texVertex(buffer, matrix, -1.0F, 1.0F, 0.0F, 0.0F, 1.0F, r, g, b, a);
      texVertex(buffer, matrix, 1.0F, 1.0F, 0.0F, 1.0F, 1.0F, r, g, b, a);
      texVertex(buffer, matrix, 1.0F, -1.0F, 0.0F, 1.0F, 0.0F, r, g, b, a);
      texVertex(buffer, matrix, -1.0F, -1.0F, 0.0F, 0.0F, 0.0F, r, g, b, a);
      matrices.popPose();
   }

   private void renderPentagramGlow(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos,
                                    double x, double y, double z, float rotation, float radius, float alpha, float hurtPC) {
      if (alpha <= 0.01F || radius <= 0.01F) return;
      matrices.pushPose();
      matrices.translate(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
      matrices.rotate(Axis.XP.rotationDegrees(90.0F));
      matrices.rotate(Axis.ZP.rotationDegrees(rotation));
      Matrix4f matrix = matrices.last().pose();

      int layers = 70;
      float maxHeight = radius * 0.1F;
      float expand = radius * 0.28F;

      for (int i = 0; i < layers; i++) {
         float progress = (float) i / (float) layers;
         float yOffset = maxHeight * progress;
         float layerAlpha = alpha * (1.0F - progress) * (1.0F - progress) * 0.14F;
         if (layerAlpha <= 0.01F) continue;
         float layerRadius = radius + expand * progress;
         float half = layerRadius / 2.0F;
         float zOffset = -yOffset;
         int color = this.pentagramColorHurt(layerAlpha, hurtPC);
         int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF, a = color >> 24 & 0xFF;
         texVertex(buffer, matrix, -half, half, zOffset, 0.0F, 1.0F, r, g, b, a);
         texVertex(buffer, matrix, half, half, zOffset, 1.0F, 1.0F, r, g, b, a);
         texVertex(buffer, matrix, half, -half, zOffset, 1.0F, 0.0F, r, g, b, a);
         texVertex(buffer, matrix, -half, -half, zOffset, 0.0F, 0.0F, r, g, b, a);
      }
      matrices.popPose();
   }

   private int pentagramColor(float alpha) {
      int themeColor = ColorUtil.fade();
      int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
      return (a << 24) | (themeColor & 0x00FFFFFF);
   }

   private int pentagramColorHurt(float alpha, float hurtPC) {
      int base = this.pentagramColor(alpha);
      if (hurtPC <= 0.0F) return base;
      int a = (int) (Mth.clamp(alpha, 0.0F, 1.0F) * 255.0F);
      int red = (a << 24) | 0xFF3333;
      return ColorUtil.overCol(base, red, hurtPC);
   }

   private static class GlowPoint {
      final float x;
      final float y;
      final float z;
      final long startTime;
      final int maxLife;
      final int baseColor;

      GlowPoint(float x, float y, float z, int maxLife, int baseColor) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.startTime = System.currentTimeMillis();
         this.maxLife = maxLife;
         this.baseColor = baseColor;
      }

      boolean shouldRemove() {
         return System.currentTimeMillis() - this.startTime >= this.maxLife;
      }

      float getTimeProgress() {
         return Mth.clamp((float) (System.currentTimeMillis() - this.startTime) / this.maxLife, 0.0F, 1.0F);
      }
   }

   private static class PentParticle {
      double x, y, z;
      double worldX, worldY, worldZ;
      float vx, vy, vz;
      long spawnTime;
      int maxLifeMs;
      float size;
      int colorSeed;
      LivingEntity entity;
      boolean fading;
      long fadeStartTime;
      static final long FADE_DURATION_MS = 280L;

      PentParticle(LivingEntity entity, double x, double y, double z, float vx, float vy, float vz, float size, int maxLifeMs, int colorSeed) {
         this.init(entity, x, y, z, vx, vy, vz, size, maxLifeMs, colorSeed);
      }

      void init(LivingEntity entity, double x, double y, double z, float vx, float vy, float vz, float size, int maxLifeMs, int colorSeed) {
         this.entity = entity;
         this.x = x;
         this.y = y;
         this.z = z;
         this.vx = vx;
         this.vy = vy;
         this.vz = vz;
         this.size = size;
         this.maxLifeMs = maxLifeMs;
         this.colorSeed = colorSeed;
         this.spawnTime = System.currentTimeMillis();
         this.fading = false;
         this.fadeStartTime = 0L;
         this.worldX = 0.0;
         this.worldY = 0.0;
         this.worldZ = 0.0;
      }

      void update(float dtMs, long now, LivingEntity currentTarget, float maxHeight) {
         float step = dtMs / 16.667F;
         if (!this.fading) {
            this.x += this.vx * step;
            this.y += this.vy * step;
            this.z += this.vz * step;
            this.vx *= 0.985F;
            this.vz *= 0.985F;
            if (this.y >= maxHeight) {
               this.beginFade(now);
               return;
            }
            boolean targetLost = currentTarget == null || this.entity == null || !this.entity.isAlive() || this.entity != currentTarget;
            if (targetLost || (now - this.spawnTime) >= this.maxLifeMs) {
               this.beginFade(now);
            }
         }
      }

      boolean shouldRemove(long now) {
         return this.fading && (now - this.fadeStartTime) >= FADE_DURATION_MS;
      }

      float getAlpha(long now) {
         if (!this.fading) {
            float fadeIn = Mth.clamp((float) (now - this.spawnTime) / 120.0F, 0.0F, 1.0F);
            float lifeProgress = Mth.clamp((float) (now - this.spawnTime) / (float) this.maxLifeMs, 0.0F, 1.0F);
            float fadeOut = 1.0F - lifeProgress * lifeProgress;
            return fadeIn * fadeOut;
         } else {
            return 1.0F - Mth.clamp((float) (now - this.fadeStartTime) / (float) FADE_DURATION_MS, 0.0F, 1.0F);
         }
      }

      private void beginFade(long now) {
         if (!this.fading) {
            if (this.entity != null) {
               this.worldX = this.entity.getX() + this.x;
               this.worldY = this.entity.getY() + this.y;
               this.worldZ = this.entity.getZ() + this.z;
            } else {
               this.worldX = this.x;
               this.worldY = this.y;
               this.worldZ = this.z;
            }
            this.fadeStartTime = now;
            this.fading = true;
            this.entity = null;
         }
      }

      double getRenderX(float partialTicks) {
         if (!this.fading && this.entity != null) {
            return Mth.lerp(partialTicks, this.entity.xOld, this.entity.getX()) + this.x;
         }
         return this.worldX;
      }

      double getRenderY(float partialTicks) {
         if (!this.fading && this.entity != null) {
            return Mth.lerp(partialTicks, this.entity.yOld, this.entity.getY()) + this.y;
         }
         return this.worldY;
      }

      double getRenderZ(float partialTicks) {
         if (!this.fading && this.entity != null) {
            return Mth.lerp(partialTicks, this.entity.zOld, this.entity.getZ()) + this.z;
         }
         return this.worldZ;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class OldCubeParticle {
      double x;
      double y;
      double z;
      double posX;
      double posY;
      double posZ;
      double motionX;
      double motionY;
      double motionZ;
      long time;
      LivingEntity entity;
      Animation animation = new EaseInOutQuad(500, 1.0);
      private double velocityY;

      public OldCubeParticle(LivingEntity entity, double x, double y, double z) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.entity = entity;
         this.time = System.currentTimeMillis();
         this.velocityY = Mathf.getRandomNumberBetween(0.01F, 0.04F);
      }

      public long getTime() {
         return this.time;
      }

      public void update(float partialTicks) {
         long currentTime = System.currentTimeMillis();
         long elapsed = currentTime - this.getTime();
         this.animation.setDirection(elapsed <= 800L ? Direction.FORWARDS : Direction.BACKWARDS);
         this.y = this.y + this.velocityY * (TargetESP.oldCubeDeltaTime * 60.0F);
         if (this.entity != null) {
            Vec3 lerpedPos = this.entity.getPosition(partialTicks);
            this.motionX = this.x + lerpedPos.x;
            this.motionY = this.y + lerpedPos.y;
            this.motionZ = this.z + lerpedPos.z;
         }
      }

      public void render(
              PoseStack matrixStack,
              Immediate immediate,
              int color,
              int glowCol,
              float alphaPC,
              float atts,
              float partialTicks,
              Vec3 cameraPos,
              float pitch,
              float yaw,
              RenderType glowLayer) {
         long currentTime = System.currentTimeMillis();
         double rotation = (currentTime - this.getTime()) / 10.0;
         this.posX = Mathf.interpolate(this.posX, this.motionX - cameraPos.x, 0.2F);
         this.posY = Mathf.interpolate(this.posY, this.motionY - cameraPos.y, 0.2F);
         this.posZ = Mathf.interpolate(this.posZ, this.motionZ - cameraPos.z, 0.2F);
         float animOutput = this.animation.getOutput();
         if (!(animOutput <= 0.0F)) {
            float pulse = 1.0F + 0.15F * (float) Math.sin((currentTime - this.getTime()) / 400.0);
            float cubeSize = 0.12F + 0.04F * animOutput;
            matrixStack.pushPose();
            matrixStack.translate(this.posX, this.posY, this.posZ);
            matrixStack.pushPose();
            matrixStack.rotate(Axis.XP.rotationDegrees((float) rotation));
            matrixStack.rotate(Axis.YP.rotationDegrees((float) rotation));
            matrixStack.rotate(Axis.ZP.rotationDegrees((float) rotation));
            Matrix4f matrix = matrixStack.last().pose();
            int cubeColor = ColorUtil.multAlpha(color, 0.5F * animOutput);
            VertexConsumer buffer = immediate.getBuffer(TargetESP.COLOR_QUADS_LAYER);
            TargetESP.drawColorCube(buffer, matrix, cubeColor, cubeSize);
            int lineColor = ColorUtil.multAlpha(color, alphaPC * animOutput);
            VertexConsumer lineBuffer = immediate.getBuffer(TargetESP.CUBE_LINES_LAYER);
            TargetESP.drawCubeLines(lineBuffer, matrix, lineColor, cubeSize);
            matrixStack.popPose();
            matrixStack.pushPose();
            matrixStack.rotate(Axis.YP.rotationDegrees(-yaw));
            matrixStack.rotate(Axis.XP.rotationDegrees(pitch));
            VertexConsumer glowBuffer = immediate.getBuffer(glowLayer);
            Matrix4f glowMatrix = matrixStack.last().pose();
            float glowSize = cubeSize * 3.0F;
            matrixStack.scale(glowSize, glowSize, glowSize);
            int glowColorWithAlpha = ColorUtil.reAlphaInt(glowCol, (int) (125.0F * alphaPC * animOutput));
            TargetESP.drawGradientQuad(glowBuffer, glowMatrix, glowColorWithAlpha,
                    ColorUtil.getAlpha(glowColorWithAlpha));
            matrixStack.popPose();
            matrixStack.popPose();
         }
      }
   }
}