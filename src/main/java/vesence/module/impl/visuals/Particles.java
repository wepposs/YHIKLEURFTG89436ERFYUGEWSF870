package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.Entity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.entity.projectile.TridentEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Mth;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.RotationAxis;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.client.util.math.PoseStack.Entry;
import net.minecraft.client.render.MultiBufferSource.Immediate;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import vesence.event.EventInit;
import vesence.event.impl.EventChangeWorld;
import vesence.event.impl.EventPacket;
import vesence.event.player.AttackEvent;
import vesence.event.player.EventMotion;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.module.api.setting.impl.TitleSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.Mathf;
import vesence.utils.other.StopWatch;
import vesence.utils.player.PlayerUtil;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.animation.util.Animation;
import vesence.utils.render.animation.util.Easings;
import vesence.module.api.Module;

@IModule(name = "Particles", description = "Кастомные частицы при попадании и ударах", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Particles extends Module {
   public static MultiBooleanSetting events = new MultiBooleanSetting(
           "Спавнить при", new BooleanSetting("Атаке", true), new BooleanSetting("Бросок", true),
           new BooleanSetting("В мире", false), new BooleanSetting("Тотем", true));
   public static ModeSetting particleBase = new ModeSetting("Тип партиклов", "1", "1", "2");
   public static ModeSetting particleMode = new ModeSetting(
           "Тип частиц", "Bloom", "Bloom", "Star", "Snow", "Heart", "Dollar", "Triangle", "Sakura", "Genshin", "Rhombus")
           .hidden(() -> particleBase.is("2"));
   public static ModeSetting particleMode2 = new ModeSetting("Тип частиц 2", "Dollar", "Dollar", "Bitcoin", "Star")
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting size = new SliderSetting("Размер", 0.5F, 0.0F, 1.0F, 0.1F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting attackTitle = new TitleSetting("Атака").hidden(() -> particleBase.is("2"));
   public static SliderSetting attackCount = new SliderSetting("Кол-во атака", 10, 1, 50, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting attackSpeed = new SliderSetting("Скорость атаки", 0.1F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting attackLifetime = new SliderSetting("Время атаки", 2.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting throwTitle = new TitleSetting("Бросок").hidden(() -> particleBase.is("2"));
   public static SliderSetting throwCount = new SliderSetting("Кол-во бросок", 15, 1, 50, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting throwSpeed = new SliderSetting("Скорость броска", 0.15F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting throwLifetime = new SliderSetting("Время броска", 3.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting totemTitle = new TitleSetting("Тотем").hidden(() -> particleBase.is("2"));
   public static SliderSetting totemCount = new SliderSetting("Кол-во тотем", 30, 1, 100, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting totemSpeed = new SliderSetting("Скорость тотема", 0.2F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting totemLifetime = new SliderSetting("Время тотема", 4.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting worldTitle = new TitleSetting("В мире").hidden(() -> particleBase.is("2"));
   public static SliderSetting worldCount = new SliderSetting("Кол-во мир", 5, 1, 50, 1, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting worldSpeed = new SliderSetting("Скорость мира", 0.05F, 0.01F, 2, 0.01F, false).hidden(() -> particleBase.is("2"));
   public static SliderSetting worldLifetime = new SliderSetting("Время мира", 5.0F, 0.5F, 10.0F, 0.5F, false).hidden(() -> particleBase.is("2"));

   public static TitleSetting attackTitle2 = new TitleSetting("Атака 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting attackCount2 = new SliderSetting("Кол-во атака 2", 10, 1, 50, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting attackSpeed2 = new SliderSetting("Скорость атаки 2", 0.5F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting attackLifetime2 = new SliderSetting("Время атаки 2", 4.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   public static TitleSetting throwTitle2 = new TitleSetting("Бросок 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting throwCount2 = new SliderSetting("Кол-во бросок 2", 15, 1, 50, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting throwSpeed2 = new SliderSetting("Скорость броска 2", 0.5F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting throwLifetime2 = new SliderSetting("Время броска 2", 4.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   public static TitleSetting totemTitle2 = new TitleSetting("Тотем 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting totemCount2 = new SliderSetting("Кол-во тотем 2", 30, 1, 100, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting totemSpeed2 = new SliderSetting("Скорость тотема 2", 0.5F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting totemLifetime2 = new SliderSetting("Время тотема 2", 4.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   public static TitleSetting worldTitle2 = new TitleSetting("В мире 2").hidden(() -> particleBase.is("1"));
   public static SliderSetting worldCount2 = new SliderSetting("Кол-во мир 2", 5, 1, 50, 1, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting worldSpeed2 = new SliderSetting("Скорость мира 2", 0.3F, 0.01F, 2, 0.01F, false)
           .hidden(() -> particleBase.is("1"));
   public static SliderSetting worldLifetime2 = new SliderSetting("Время мира 2", 5.0F, 0.5F, 10.0F, 0.5F, false)
           .hidden(() -> particleBase.is("1"));

   private static final int QUAD_BUFFER_SIZE_BYTES = 1024;
   private long lastUpdateTime = System.nanoTime();
   private static final String PIPELINE_NAMESPACE = "vesence";
   private static final RenderPipeline TEXTURED_QUADS_PIPELINE = RenderPipelines.register(
           RenderPipeline.builder(new Snippet[] { RenderPipelines.POSITION_TEX_COLOR_SNIPPET })
                   .withLocation(ResourceLocation.of("vesence", "textures/world/textured_quads"))
                   .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                   .withCull(false)
                   .withDepthTestFunction(DepthTestFunction.LEQUAL_DEPTH_TEST)
                   .withDepthWrite(false)
                   .withBlend(BlendFunction.LIGHTNING)
                   .build());
   private static final ResourceLocation BLOOM_TEXTURE = ResourceLocation.of("vesence", "textures/world/dashbloom.png");
   private static final ResourceLocation BLOOM_SOFT_TEXTURE = ResourceLocation.of("vesence", "textures/world/dashbloomsample.png");

   public static TitleSetting glowTitle = new TitleSetting("Свечение");
   public static BooleanSetting glowEnabled = new BooleanSetting("Свечение", true);
   public static BooleanSetting glowLighting = new BooleanSetting("Подсветка", true)
           .hidden(() -> !glowEnabled.get());
   public static SliderSetting glowIntensity = new SliderSetting("Сила свечения", 0.6F, 0.0F, 1.5F, 0.05F, false)
           .hidden(() -> !glowEnabled.get());
   public static SliderSetting glowRadius = new SliderSetting("Радиус свечения", 2.5F, 1.0F, 6.0F, 0.1F, false)
           .hidden(() -> !glowEnabled.get());

   private static final Map<ParticleType, RenderType> RENDER_LAYER_CACHE = new ConcurrentHashMap<>();
   private static final Random CACHED_RANDOM = Random.create();
   // Переиспользуемые BlockPos (рендер/тик — один поток), чтобы не аллоцировать на каждую проверку блока.
   private static final BlockPos.Mutable SHARED_SOLID_POS = new BlockPos.Mutable();
   private static final BlockPos.Mutable SHARED_AIR_POS = new BlockPos.Mutable();
   private static RenderType cachedBloomLayer = null;
   private static RenderType cachedBloomSoftLayer = null;
   private final List<Particle> targetParticles = new ArrayList<>();
   private final List<Particle> worldParticles = new ArrayList<>();
   private final List<Particle> flameParticles = new ArrayList<>();
   private final List<Particle> totemParticles = new ArrayList<>();
   private final List<VegaParticle> vegaParticles = new ArrayList<>();
   private static final Vector3f REUSABLE_NORMAL = new Vector3f(0.0F, 0.0F, 1.0F);
   private long totemPopTime = 0L;
   private Entity totemPopEntity = null;
   private static final long TOTEM_POP_DURATION = 1500L;
   private ByteBufferBuilder allocator = null;
   private Immediate immediate = null;
   // Значения, вычисляемые ОДИН раз за кадр (чтобы не боксить Double и не дёргать камеру на каждую частицу).
   private final Quaternionf frameCamRot = new Quaternionf();
   private boolean frameGlow;
   private boolean frameGlowLight;
   private float frameGlowRadius;
   private float frameGlowIntensity;
   private float frameVegaSize;
   private ParticleType frameVegaType = ParticleType.VEGA_DOLLAR;
   private float framePartialTicks;
   private final Matrix4f particleMatrix = new Matrix4f();

   /** Проверка «блок сплошной» без аллокации BlockPos (значение идентично PlayerUtil.isBlockSolid). */
   static boolean isSolidAt(double x, double y, double z) {
      if (Module.mc.level == null) return false;
      BlockPos.Mutable p = SHARED_SOLID_POS.set(Mth.floor(x), Mth.floor(y), Mth.floor(z));
      return Module.mc.level.getBlockState(p).isFullCube(Module.mc.level, p);
   }

   public Particles() {
      this.addSettings(new Setting[] { events, particleBase, particleMode, particleMode2, size,
              attackTitle, attackCount, attackSpeed, attackLifetime,
              throwTitle, throwCount, throwSpeed, throwLifetime,
              totemTitle, totemCount, totemSpeed, totemLifetime,
              worldTitle, worldCount, worldSpeed, worldLifetime,
              attackTitle2, attackCount2, attackSpeed2, attackLifetime2,
              throwTitle2, throwCount2, throwSpeed2, throwLifetime2,
              totemTitle2, totemCount2, totemSpeed2, totemLifetime2,
              worldTitle2, worldCount2, worldSpeed2, worldLifetime2,
              glowTitle, glowEnabled, glowLighting, glowIntensity, glowRadius });
   }

   private void clear() {
      this.targetParticles.clear();
      this.flameParticles.clear();
      this.worldParticles.clear();
      this.totemParticles.clear();
      this.vegaParticles.clear();
   }

   private void spawnParticle(List<Particle> particles, Vec3 position, Vec3 velocity) {
      float particleSize = (float) (0.05F + size.get() * 0.2F);
      int color;
      if (vesence.renderengine.render.Renderer2D.ColorUtil.isDualColor()) {
         // Двухцветная тема: половина частиц — Цвет 1, половина — Цвет 2.
         color = (particles.size() % 2 == 0)
                 ? vesence.renderengine.render.Renderer2D.ColorUtil.getClientColor1()
                 : vesence.renderengine.render.Renderer2D.ColorUtil.getClientColor2();
      } else {
         color = ColorUtil.fade(particles.size() * 100);
      }
      String var7 = particleMode.get();

      ParticleType type = switch (var7) {
         case "Heart" -> ParticleType.HEART;
         case "Star" -> ParticleType.STAR;
         case "Snow" -> ParticleType.SNOW;
         case "Bloom" -> ParticleType.BLOOM;
         case "Dollar" -> ParticleType.DOLLAR;
         case "Triangle" -> ParticleType.TRIANGLE;
         case "Sakura" -> ParticleType.SAKURA;
         case "Genshin" -> ParticleType.GEMINI;
         case "Rhombus" -> ParticleType.SIMS;
         default -> ParticleType.BLOOM;
      };
      particles.add(
              new Particle(
                      type,
                      position.add(0.0, particleSize, 0.0),
                      velocity,
                      particles.size(),
                      (int) Mathf.step(Mathf.randomValue(0.0F, 360.0F), 15.0),
                      color,
                      particleSize,
                      0.2F));
   }

   private void spawnTotemParticle(Vec3 position, Vec3 velocity, int color) {
      float particleSize = (float) (0.05F + size.get() * 0.2F);
      String var7 = particleMode.get();

      ParticleType type = switch (var7) {
         case "Heart" -> ParticleType.HEART;
         case "Star" -> ParticleType.STAR;
         case "Snow" -> ParticleType.SNOW;
         case "Bloom" -> ParticleType.BLOOM;
         case "Dollar" -> ParticleType.DOLLAR;
         case "Triangle" -> ParticleType.TRIANGLE;
         case "Sakura" -> ParticleType.SAKURA;
         case "Genshin" -> ParticleType.GEMINI;
         case "Rhombus" -> ParticleType.SIMS;
         default -> ParticleType.BLOOM;
      };
      totemParticles.add(
              new Particle(
                      type,
                      position.add(0.0, particleSize, 0.0),
                      velocity,
                      totemParticles.size(),
                      (int) Mathf.step(Mathf.randomValue(0.0F, 360.0F), 15.0),
                      color,
                      particleSize,
                      0.2F));
   }

   private void spawnVegaParticle(Vec3 position, long lifetime, float speed) {
      boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
      vegaParticles.add(new VegaParticle(position, lower, lifetime, speed));
   }

   @EventInit
   public void onEvent(AttackEvent event) {
      if (!this.enable) {
         return;
      }

      Entity target = event.getTarget();
      if (events.get("Атаке")) {
         if (particleBase.is("2")) {
            boolean lowestAndRotates = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
            float w = target.getWidth() / 2.0F;
            float h = target.getHeight();
            int count = attackCount2.get().intValue();
            for (int i = 0; i < count; i++) {
               Vec3 vec = new Vec3(target.getX(), target.getY(), target.getZ()).add(
                       (-w) + (w * 2.0F) * Math.random(),
                       h * Math.random(),
                       (-w) + (w * 2.0F) * Math.random());
               VegaParticle vp = new VegaParticle(vec, lowestAndRotates,
                       (long)(attackLifetime2.get() * 1000), attackSpeed2.get().floatValue());
               vegaParticles.add(vp);
            }
            return;
         }
         int count = attackCount.get().intValue();
         for (int i = 0; i < count; i++) {
            this.spawnParticle(
                    this.targetParticles,
                    new Vec3(target.getX(), target.getY() + Mathf.randomValue(0.0F, target.getHeight()), target.getZ()),
                    new Vec3(
                            Mathf.randomValue(-1.0F, 1.0F) * attackSpeed.get() * 10,
                            Mathf.randomValue(-1.0F, 1.0F) * attackSpeed.get() * 10,
                            Mathf.randomValue(-1.0F, 1.0F) * attackSpeed.get() * 10));
         }
      }
   }

   @EventInit
   public void onEvent(EventMotion event) {
      if (!this.enable) {
         return;
      }

      if (events.get("Тотем")) {
         if (mc.player != null && totemPopEntity != null) {
            long timeSincePop = System.currentTimeMillis() - totemPopTime;
            if (totemPopTime > 0L && timeSincePop < TOTEM_POP_DURATION) {
               Entity popEntity = totemPopEntity;
               if (popEntity.isAlive()) {
                  int count = particleBase.is("2") ? totemCount2.get().intValue() : totemCount.get().intValue();
                  float speed = particleBase.is("2") ? totemSpeed2.get().floatValue() : totemSpeed.get().floatValue();
                  long lifetime = particleBase.is("2")
                          ? (long)(totemLifetime2.get() * 1000)
                          : (long)(totemLifetime.get() * 1000);

                  for (int i = 0; i < count; i++) {
                     boolean isGreen = i % 2 == 0;
                     int color = isGreen ? ColorUtil.getColor(125, 255, 125) : ColorUtil.getColor(255, 255, 125);

                     Vec3 pos = new Vec3(
                             popEntity.getX() + Mth.nextDouble(CACHED_RANDOM, -0.3, 0.3),
                             popEntity.getY() + popEntity.getHeight() / 2 + Mth.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                             popEntity.getZ() + Mth.nextDouble(CACHED_RANDOM, -0.3, 0.3)
                     );
                     Vec3 velocity = new Vec3(
                             Mth.nextDouble(CACHED_RANDOM, -1.0, 1.0) * 6.0 * speed,
                             Mth.nextDouble(CACHED_RANDOM, -1.0, 1.0) * 6.0 * speed,
                             Mth.nextDouble(CACHED_RANDOM, -1.0, 1.0) * 6.0 * speed
                     );

                     if (particleBase.is("2")) {
                        boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
                        VegaParticle vp = new VegaParticle(pos, lower, lifetime, speed);
                        vp.color = color;
                        vegaParticles.add(vp);
                     } else {
                        this.spawnTotemParticle(pos, velocity, color);
                     }
                  }
               }
            } else {
               totemPopEntity = null;
            }
         }
      }

      if (events.get("Бросок")) {
         if (mc.level == null) {
            return;
         }

         for (Entity entity : mc.level.getEntities()) {
            if ((entity instanceof EnderPearlEntity || entity instanceof ArrowEntity || entity instanceof TridentEntity)
                    && (!(entity instanceof TridentEntity trident) || !trident.onGround())) {
               boolean isMoving = entity.lastX != entity.getX()
                       || entity.lastY != entity.getY()
                       || entity.lastZ != entity.getZ();
               if (isMoving) {
                  Vec3 pos = new Vec3(entity.getX(), entity.getY(), entity.getZ());

                  if (particleBase.is("2")) {
                     int count = throwCount2.get().intValue();
                     float speed = throwSpeed2.get().floatValue();
                     long lifetime = (long)(throwLifetime2.get() * 1000);
                     for (int i = 0; i < count; i++) {
                        boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
                        VegaParticle vp = new VegaParticle(
                                new Vec3(
                                        pos.x + Mth.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.y + Mth.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.z + Mth.nextDouble(CACHED_RANDOM, -0.2, 0.2)),
                                lower, lifetime, speed);
                        vegaParticles.add(vp);
                     }
                  } else {
                     int count = throwCount.get().intValue();
                     for (int i = 0; i < count; i++) {
                        this.spawnParticle(
                                this.flameParticles,
                                new Vec3(
                                        pos.x + Mth.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.y + Mth.nextDouble(CACHED_RANDOM, -0.2, 0.2),
                                        pos.z + Mth.nextDouble(CACHED_RANDOM, -0.2, 0.2)),
                                new Vec3(
                                        Mth.nextDouble(CACHED_RANDOM, -1.0, 1.0) * throwSpeed.get() * 10,
                                        Mth.nextDouble(CACHED_RANDOM, -0.3, 0.3) * throwSpeed.get() * 10,
                                        Mth.nextDouble(CACHED_RANDOM, -1.0, 1.0) * throwSpeed.get() * 10));
                     }
                  }
               }
            }
         }
      }

      if (events.get("В мире")) {
         if (mc.level == null || mc.player == null) {
            return;
         }

         int r = 12;

         if (particleBase.is("2")) {
            int count = worldCount2.get().intValue();
            float speed = worldSpeed2.get().floatValue();
            long lifetime = (long)(worldLifetime2.get() * 1000);
            for (int i = 0; i < count; i++) {
               Vec3 additional = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                       .add(Mathf.randomValue((float) (-r), (float) r), 0.0, Mathf.randomValue((float) (-r), (float) r));
               BlockPos topPos = mc.level.getTopPosition(Type.MOTION_BLOCKING, BlockPos.ofFloored(additional));
               double x = topPos.getX() + Mathf.randomValue(0.0F, 1.0F);
               double z = topPos.getZ() + Mathf.randomValue(0.0F, 1.0F);
               double y = mc.player.getY() + Mathf.randomValue(mc.player.getHeight(), (float) r);
               Vec3 spawnPos = new Vec3(x, y, z);

               while (!mc.level.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.level.getTopYInclusive()) {
                  spawnPos = spawnPos.add(0.0, 1.0, 0.0);
               }

               boolean lower = particleMode2.is("Dollar") || particleMode2.is("Bitcoin");
               VegaParticle vp = new VegaParticle(spawnPos, lower, lifetime, speed);
               vegaParticles.add(vp);
            }
         } else {
            int count = worldCount.get().intValue();
            for (int i = 0; i < count; i++) {
               Vec3 additional = new Vec3(mc.player.getX(), mc.player.getY(), mc.player.getZ())
                       .add(Mathf.randomValue((float) (-r), (float) r), 0.0, Mathf.randomValue((float) (-r), (float) r));
               BlockPos topPos = mc.level.getTopPosition(Type.MOTION_BLOCKING, BlockPos.ofFloored(additional));
               double x = topPos.getX() + Mathf.randomValue(0.0F, 1.0F);
               double z = topPos.getZ() + Mathf.randomValue(0.0F, 1.0F);
               double y = mc.player.getY() + Mathf.randomValue(mc.player.getHeight(), (float) r);
               Vec3 spawnPos = new Vec3(x, y, z);

               while (!mc.level.isAir(BlockPos.ofFloored(spawnPos)) && spawnPos.y < mc.level.getTopYInclusive()) {
                  spawnPos = spawnPos.add(0.0, 1.0, 0.0);
               }

               this.spawnParticle(
                       this.worldParticles,
                       spawnPos,
                       new Vec3(
                               mc.player.getDeltaMovement().x + Mathf.randomValue(-2.0F, 2.0F) * worldSpeed.get() * 10,
                               Mathf.randomValue(-0.2, 0.2) * worldSpeed.get() * 10,
                               mc.player.getDeltaMovement().z + Mathf.randomValue(-2.0F, 2.0F) * worldSpeed.get() * 10));
            }
         }
      }

      this.removeExpiredParticles(this.targetParticles, (long)(attackLifetime.get() * 1000));
      this.removeExpiredParticles(this.flameParticles, (long)(throwLifetime.get() * 1000));
      this.removeExpiredParticles(this.worldParticles, (long)(worldLifetime.get() * 1000));
      this.removeExpiredParticles(this.totemParticles, (long)(totemLifetime.get() * 1000));

      for (int i = 0; i < this.vegaParticles.size(); i++) {
         VegaParticle part = this.vegaParticles.get(i);
         if (part == null) continue;
         part.updatePart();
         if (part.toRemove) {
            this.vegaParticles.remove(i--);
         }
      }
   }

   @EventInit
   public void onEvent(EventPacket event) {
      if (!this.enable) return;
      if (events.get("Тотем") && event.getType() == EventPacket.Type.RECEIVE) {
         if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket) {
            if (statusPacket.getStatus() == 35) {
               Entity entity = statusPacket.getEntity(mc.level);
               if (entity != null) {
                  totemPopTime = System.currentTimeMillis();
                  totemPopEntity = entity;
               }
            }
         }
      }
   }

   @EventInit
   public void onEvent(EventRender3D event) {
      PoseStack matrix = event.getMatrixStack();
      Vec3 cameraPos = mc.gameRenderer.getCamera().getCameraPos();
      long now = System.nanoTime();
      double deltaTime = (now - this.lastUpdateTime) / 1.0E9;
      this.lastUpdateTime = now;

      // Кэш значений на кадр — устраняет боксинг Double и повторные вызовы на каждую частицу.
      this.frameCamRot.set(mc.gameRenderer.getCamera().getRotation());
      this.frameGlow = glowEnabled.get();
      this.frameGlowLight = glowLighting.get();
      this.frameGlowRadius = glowRadius.get().floatValue();
      this.frameGlowIntensity = glowIntensity.get().floatValue();
      this.frameVegaSize = (float) (0.1 + size.get() * 0.4);
      this.frameVegaType = switch (particleMode2.get()) {
         case "Bitcoin" -> ParticleType.VEGA_BITCOIN;
         case "Star" -> ParticleType.VEGA_STAR;
         default -> ParticleType.VEGA_DOLLAR;
      };
      this.framePartialTicks = mc.getRenderTickCounter().getDynamicDeltaTicks();

      if (allocator == null) {
         allocator = new ByteBufferBuilder(2097152);
         immediate = MultiBufferSource.immediate(allocator);
      }

      try {
         Matrix4f base = matrix.last().getPositionMatrix();

         if (!particleBase.is("2")) {
            double friction = Math.pow(0.999, deltaTime * 60.0);
            // Физика + фейд — один проход.
            this.updateParticleList(this.targetParticles, 400L, 600L, deltaTime, friction);
            this.updateParticleList(this.flameParticles, 700L, 1200L, deltaTime, friction);
            this.updateParticleList(this.worldParticles, 1500L, 2200L, deltaTime, friction);
            this.updateParticleList(this.totemParticles, 1000L, 2000L, deltaTime, friction);

            // БАТЧИНГ ПО СЛОЯМ: сперва ВСЕ основные квады, затем bloom, затем soft.
            // В 1.21.11 переключение RenderType у immediate = дорогой flush/draw,
            // поэтому раньше выходило ~3 draw-call'а НА КАЖДУЮ частицу. Теперь ~3 на кадр.
            this.emitMainQuads(this.targetParticles, base, cameraPos, immediate);
            this.emitMainQuads(this.flameParticles, base, cameraPos, immediate);
            this.emitMainQuads(this.worldParticles, base, cameraPos, immediate);
            this.emitMainQuads(this.totemParticles, base, cameraPos, immediate);
            if (this.frameGlow) {
               this.emitGlowQuads(this.targetParticles, base, cameraPos, immediate, false);
               this.emitGlowQuads(this.flameParticles, base, cameraPos, immediate, false);
               this.emitGlowQuads(this.worldParticles, base, cameraPos, immediate, false);
               this.emitGlowQuads(this.totemParticles, base, cameraPos, immediate, false);
               if (this.frameGlowLight) {
                  this.emitGlowQuads(this.targetParticles, base, cameraPos, immediate, true);
                  this.emitGlowQuads(this.flameParticles, base, cameraPos, immediate, true);
                  this.emitGlowQuads(this.worldParticles, base, cameraPos, immediate, true);
                  this.emitGlowQuads(this.totemParticles, base, cameraPos, immediate, true);
               }
            }
         }

         // Vega — тоже батчингом по слоям.
         this.emitVegaMain(base, cameraPos, immediate);
         if (this.frameGlow) {
            this.emitVegaGlow(base, cameraPos, immediate, false);
            if (this.frameGlowLight) {
               this.emitVegaGlow(base, cameraPos, immediate, true);
            }
         }
         immediate.draw();
      } catch (Exception e) {
         if (allocator != null) { try { allocator.close(); } catch (Exception ignored) {} }
         allocator = null;
         immediate = null;
      }
   }

   @Override
   public void onDisable() {
      this.clear();
      if (allocator != null) {
         try { allocator.close(); } catch (Exception ignored) {}
         allocator = null;
         immediate = null;
      }
   }

   private void removeExpiredParticles(List<Particle> particles, long lifespan) {
      particles.removeIf(particle -> particle.time().finished(lifespan) && particle.animation.get() <= 0.0F);
   }

   private void updateParticleList(List<Particle> particles, long fadeInTime, long fadeOutTime, double deltaTime, double friction) {
      if (particles.isEmpty()) return;
      for (Particle particle : particles) {
         particle.update(true, deltaTime, friction);
         boolean notFinishedFadeIn = !particle.time().finished(fadeInTime);
         boolean finishedFadeOut = particle.time().finished(fadeOutTime);
         if (notFinishedFadeIn) {
            particle.animation().run(1.0, 0.4, Easings.QUAD_OUT, true);
         } else if (finishedFadeOut) {
            particle.animation().run(0.0, 0.4, Easings.QUAD_OUT, true);
         }
         if (particle.animation.isAlive()) {
            particle.animation.update();
         }
      }
   }

   private Matrix4f buildParticleMatrix(Matrix4f base, Vec3 cameraPos, float x, float y, float z, int rotateDeg) {
      return this.particleMatrix.set(base)
              .translate((float) (x - cameraPos.x), (float) (y - cameraPos.y), (float) (z - cameraPos.z))
              .rotate(this.frameCamRot)
              .rotateZ((float) Math.toRadians(rotateDeg));
   }

   private RenderType layerFor(ParticleType type) {
      return RENDER_LAYER_CACHE.computeIfAbsent(
              type,
              t -> {
                 ResourceLocation texture = t.texture();
                 return RenderType.of(texture.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", texture).build());
              });
   }

   private RenderType bloomLayer() {
      if (cachedBloomLayer == null) {
         cachedBloomLayer = RenderType.of(BLOOM_TEXTURE.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", BLOOM_TEXTURE).build());
      }
      return cachedBloomLayer;
   }

   private RenderType bloomSoftLayer() {
      if (cachedBloomSoftLayer == null) {
         cachedBloomSoftLayer = RenderType.of(BLOOM_SOFT_TEXTURE.toString(), RenderSetup.builder(TEXTURED_QUADS_PIPELINE).expectedBufferSize(1024).translucent().texture("Sampler0", BLOOM_SOFT_TEXTURE).build());
      }
      return cachedBloomSoftLayer;
   }

   /** Основные квады частиц — все в один слой (минимум переключений). */
   private void emitMainQuads(List<Particle> particles, Matrix4f base, Vec3 cameraPos, Immediate immediate) {
      if (particles.isEmpty()) return;
      for (Particle particle : particles) {
         int alpha = (int) (particle.animation.get() * 255.0F);
         if (alpha <= 0) continue;
         int color = particle.color();
         float pos = particle.size;
         Matrix4f m = this.buildParticleMatrix(base, cameraPos, (float) particle.posX, (float) particle.posY, (float) particle.posZ, particle.rotate());
         VertexConsumer buffer = immediate.getBuffer(this.layerFor(particle.type()));
         this.drawTexturedQuad(buffer, m, -pos, -pos, pos * 2.0F, pos * 2.0F, color, alpha);
         if (particle.type == ParticleType.BLOOM) {
            this.drawTexturedQuad(buffer, m, -pos / 2.0F, -pos / 2.0F, pos, pos, color, alpha);
         }
      }
   }

   /** Bloom (soft=false) или подсветка (soft=true) — все в один слой. */
   private void emitGlowQuads(List<Particle> particles, Matrix4f base, Vec3 cameraPos, Immediate immediate, boolean soft) {
      if (particles.isEmpty()) return;
      float radiusMul = this.frameGlowRadius;
      float intensity = this.frameGlowIntensity;
      VertexConsumer buffer = immediate.getBuffer(soft ? this.bloomSoftLayer() : this.bloomLayer());
      for (Particle particle : particles) {
         int alpha = (int) (particle.animation.get() * 255.0F);
         if (alpha <= 0) continue;
         int color = particle.color();
         float pos = particle.size;
         if (soft) {
            int lightAlpha = (int) (alpha * 0.22F * intensity);
            if (lightAlpha <= 0) continue;
            float lightPos = pos * radiusMul * 1.75F;
            Matrix4f m = this.buildParticleMatrix(base, cameraPos, (float) particle.posX, (float) particle.posY, (float) particle.posZ, particle.rotate());
            this.drawTexturedQuad(buffer, m, -lightPos, -lightPos, lightPos * 2.0F, lightPos * 2.0F, color, lightAlpha);
         } else {
            int bloomAlpha = (int) (alpha * 0.42F * intensity);
            if (bloomAlpha <= 0) continue;
            float bloomPos = pos * radiusMul;
            Matrix4f m = this.buildParticleMatrix(base, cameraPos, (float) particle.posX, (float) particle.posY, (float) particle.posZ, particle.rotate());
            this.drawTexturedQuad(buffer, m, -bloomPos, -bloomPos, bloomPos * 2.0F, bloomPos * 2.0F, color, bloomAlpha);
         }
      }
   }

   private int vegaColor(VegaParticle part, float i, boolean colorize, int totalCount) {
      if (part.color >= 0) {
         return part.color;
      } else if (colorize) {
         int c1 = ColorUtil.fade(10, (int) (i * 5.0F), ColorUtil.fade(), ColorUtil.multDark(ColorUtil.fade(), 0.5F));
         int c2 = ColorUtil.fade(10, (int) (i * 5.0F) + 120, ColorUtil.fade(), ColorUtil.multDark(ColorUtil.fade(), 0.5F));
         return ColorUtil.getOverallColorFrom(c1, c2, Mathf.clamp(0.0F, 1.0F, i / Math.max(1, totalCount)));
      } else {
         return ColorUtil.getColor(160);
      }
   }

   private Matrix4f buildVegaMatrix(VegaParticle part, Matrix4f base, Vec3 cameraPos, float pSize) {
      float ax = (float) part.posX;
      float ay = (float) part.posY + pSize * 0.5F;
      float az = (float) part.posZ;
      Matrix4f m = this.particleMatrix.set(base)
              .translate((float) (ax - cameraPos.x), (float) (ay - cameraPos.y), (float) (az - cameraPos.z));
      if (part.lower) {
         float rotY = (float) Mathf.lerp(part.prevRotY, part.rotY, this.framePartialTicks);
         float rotX = (float) Mathf.lerp(part.prevRotX, part.rotX, this.framePartialTicks);
         m.rotateY((float) Math.toRadians(rotY));
         m.rotateX((float) Math.toRadians(rotX));
         if (this.frameVegaType != ParticleType.VEGA_BITCOIN) {
            m.scale(2.0F, 2.0F, 2.0F);
         }
      } else {
         m.rotate(this.frameCamRot);
      }
      m.scale(-pSize, -pSize, -pSize);
      float spinAngle = part.time.elapsedTime() / (float) part.maxTime * 1200.0F * (part.motionXSign > 0.0F ? 1.0F : -1.0F);
      m.rotateZ((float) Math.toRadians(spinAngle));
      return m;
   }

   private void emitVegaMain(Matrix4f base, Vec3 cameraPos, Immediate immediate) {
      if (this.vegaParticles.isEmpty()) return;
      float pSize = this.frameVegaSize;
      boolean colorize = particleMode2.is("Star");
      int totalCount = this.vegaParticles.size();
      VertexConsumer buffer = immediate.getBuffer(this.layerFor(this.frameVegaType));
      float i = 0.0F;
      for (VegaParticle part : this.vegaParticles) {
         if (part == null || part.toRemove) continue;
         int alpha = (int) (part.alphaPC * 255.0F);
         if (alpha > 5) {
            int color = this.vegaColor(part, i, colorize, totalCount);
            Matrix4f m = this.buildVegaMatrix(part, base, cameraPos, pSize);
            this.drawTexturedQuad(buffer, m, 0.0F, 0.0F, 1.0F, 1.0F, color, alpha);
         }
         i += 1.0F;
      }
   }

   private void emitVegaGlow(Matrix4f base, Vec3 cameraPos, Immediate immediate, boolean soft) {
      if (this.vegaParticles.isEmpty()) return;
      float pSize = this.frameVegaSize;
      boolean colorize = particleMode2.is("Star");
      int totalCount = this.vegaParticles.size();
      float radiusMul = this.frameGlowRadius * 0.5F;
      float intensity = this.frameGlowIntensity;
      VertexConsumer buffer = immediate.getBuffer(soft ? this.bloomSoftLayer() : this.bloomLayer());
      float i = 0.0F;
      for (VegaParticle part : this.vegaParticles) {
         if (part == null || part.toRemove) continue;
         int alpha = (int) (part.alphaPC * 255.0F);
         if (alpha > 5) {
            if (soft) {
               int lightAlpha = (int) (alpha * 0.22F * intensity);
               if (lightAlpha > 0) {
                  int color = this.vegaColor(part, i, colorize, totalCount);
                  Matrix4f m = this.buildVegaMatrix(part, base, cameraPos, pSize);
                  float lightExt = radiusMul * 1.75F;
                  float x1 = 0.5F - lightExt;
                  float bw = 2.0F * lightExt;
                  this.drawTexturedQuad(buffer, m, x1, x1, bw, bw, color, lightAlpha);
               }
            } else {
               int bloomAlpha = (int) (alpha * 0.42F * intensity);
               if (bloomAlpha > 0) {
                  int color = this.vegaColor(part, i, colorize, totalCount);
                  Matrix4f m = this.buildVegaMatrix(part, base, cameraPos, pSize);
                  float bloomExt = radiusMul;
                  float x1 = 0.5F - bloomExt;
                  float bw = 2.0F * bloomExt;
                  this.drawTexturedQuad(buffer, m, x1, x1, bw, bw, color, bloomAlpha);
               }
            }
         }
         i += 1.0F;
      }
   }

   private void drawTexturedQuad(VertexConsumer buffer, Matrix4f matrix, float x, float y,
                                 float width, float height, int color, int alpha) {
      // Формат слоя — POSITION_TEXTURE_COLOR, поэтому normal/overlay/light не нужны
      // (были no-op). Пишем только позицию/цвет/uv — меньше работы на вершину.
      int r = color >> 16 & 0xFF;
      int g = color >> 8 & 0xFF;
      int b = color & 0xFF;
      float x2 = x + width;
      float y2 = y + height;
      buffer.vertex(matrix, x, y, 0.0F).color(r, g, b, alpha).texture(0.0F, 1.0F);
      buffer.vertex(matrix, x2, y, 0.0F).color(r, g, b, alpha).texture(1.0F, 1.0F);
      buffer.vertex(matrix, x2, y2, 0.0F).color(r, g, b, alpha).texture(1.0F, 0.0F);
      buffer.vertex(matrix, x, y2, 0.0F).color(r, g, b, alpha).texture(0.0F, 0.0F);
   }

   @Override
   public void toggle() {
      super.toggle();
      this.clear();
   }

   @Environment(EnvType.CLIENT)
   public static class Particle {
      private AABB box;
      private final ParticleType type;
      private double posX, posY, posZ;
      private double velX, velY, velZ;
      private final int index;
      private final int rotate;
      private final int color;
      private final float size;
      private static final double BASE_VELOCITY = 0.05;
      private final double speedMultiplier;
      private final StopWatch time = new StopWatch();
      private final Animation animation = new Animation();

      public Particle(ParticleType type, Vec3 position, Vec3 velocity, int index, int rotate, int color,
                      float size, double speedMultiplier) {
         double halfSize = size / 2.0;
         this.box = new AABB(
                 new Vec3(position.x - halfSize, position.y - halfSize, position.z - halfSize),
                 new Vec3(position.x + halfSize, position.y + halfSize, position.z + halfSize));
         this.type = type;
         this.posX = position.x;
         this.posY = position.y;
         this.posZ = position.z;
         double vm = 0.05;
         this.velX = velocity.x * vm;
         this.velY = velocity.y * vm;
         this.velZ = velocity.z * vm;
         this.index = index;
         this.rotate = rotate;
         this.color = color;
         this.size = size;
         this.speedMultiplier = speedMultiplier;
         this.time.reset();
      }

      public void update(boolean physic, double deltaTime, double friction) {
         if (physic && Module.mc.level != null) {
            double velMagSq = this.velX * this.velX
                    + this.velY * this.velY
                    + this.velZ * this.velZ;
            if (velMagSq > 1.0E-4) {
               if (Particles.isSolidAt(this.posX, this.posY, this.posZ + this.velZ)) {
                  this.velX *= 1.35F; this.velY *= 1.35F; this.velZ *= -1.1;
               }

               if (Particles.isSolidAt(this.posX, this.posY + this.velY, this.posZ)) {
                  this.velX *= 1.35F; this.velY *= -1.1; this.velZ *= 1.35F;
               }

               if (Particles.isSolidAt(this.posX + this.velX, this.posY, this.posZ)) {
                  this.velX *= -1.1; this.velY *= 1.35F; this.velZ *= 1.35F;
               }
            }

            this.velX = this.velX * friction;
            this.velY = this.velY * friction - 2.0E-5;
            this.velZ = this.velZ * friction;
         }

         double deltaMultiplier = deltaTime * 60.0 * this.speedMultiplier;
         this.posX += this.velX * deltaMultiplier;
         this.posY += this.velY * deltaMultiplier;
         this.posZ += this.velZ * deltaMultiplier;
         this.box = null;
      }

      @Generated
      public AABB box() {
         if (this.box == null) {
            double halfSize = this.size / 2.0;
            this.box = new AABB(
                    new Vec3(this.posX - halfSize, this.posY - halfSize, this.posZ - halfSize),
                    new Vec3(this.posX + halfSize, this.posY + halfSize, this.posZ + halfSize));
         }
         return this.box;
      }

      @Generated
      public ParticleType type() {
         return this.type;
      }

      @Generated
      public Vec3 position() {
         return new Vec3(this.posX, this.posY, this.posZ);
      }

      @Generated
      public Vec3 velocity() {
         return new Vec3(this.velX, this.velY, this.velZ);
      }

      @Generated
      public int index() {
         return this.index;
      }

      @Generated
      public int rotate() {
         return this.rotate;
      }

      @Generated
      public int color() {
         return this.color;
      }

      @Generated
      public float size() {
         return this.size;
      }

      @Generated
      public double speedMultiplier() {
         return this.speedMultiplier;
      }

      @Generated
      public StopWatch time() {
         return this.time;
      }

      @Generated
      public Animation animation() {
         return this.animation;
      }
   }

   @Environment(EnvType.CLIENT)
   public static class VegaParticle {
      private static final float ALPHA_SPEED = 0.035F;
      public boolean toRemove = false;
      public float alphaPC = 0.0F;
      public double posX, posY, posZ;

      private float motionX;
      private float motionY;
      private float motionZ;
      public final float motionXSign;
      private final float speed;

      private final StopWatch time = new StopWatch();
      public final long maxTime;
      public final boolean lower;

      private float rotY, rotX;
      private float rotSpeedY, rotSpeedX;
      private float prevRotY, prevRotX;

      public int color = -1;

      public VegaParticle(Vec3 vec, boolean lower, long maxTime, float speed) {
         this.posX = vec.x;
         this.posY = vec.y;
         this.posZ = vec.z;
         this.speed = Math.max(0.01F, speed);

         float baseMotion = 0.15F * this.speed;
         this.motionX = Mathf.randomValue(-baseMotion, baseMotion);
         this.motionY = Mathf.randomValue(-baseMotion * 0.3F, baseMotion * 0.4F);
         this.motionZ = Mathf.randomValue(-baseMotion, baseMotion);
         this.motionXSign = this.motionX;
         this.maxTime = maxTime;
         this.lower = lower;
         this.time.reset();

         if (this.lower) {
            this.rotY = -360.0F + 720.0F * (float) Math.random();
            this.rotX = -180.0F + 360.0F * (float) Math.random();
            this.rotSpeedY = this.rotY / 10.0F;
            this.rotSpeedX = this.rotX / 10.0F;
            this.prevRotY = this.rotY;
            this.prevRotX = this.rotX;
         }
      }

      public void updatePart() {
         long elapsed = this.time.elapsedTime();
         if (elapsed > this.maxTime) {
            this.alphaPC = Math.max(0.0F, this.alphaPC - ALPHA_SPEED);
         } else {
            this.alphaPC = Math.min(1.0F, this.alphaPC + ALPHA_SPEED);
         }
         if (this.alphaPC < 0.005F && elapsed > this.maxTime) {
            this.toRemove = true;
         }
         if (this.toRemove) {
            return;
         }
         this.gravityAndMove();
      }

      private void gravityAndMove() {
         if (this.lower) {
            this.prevRotY = this.rotY;
            this.prevRotX = this.rotX;
            this.rotY = this.rotY + this.rotSpeedY;
            this.rotX = this.rotX + this.rotSpeedX;
            this.rotSpeedY = this.rotSpeedY * 0.96F;
            this.rotSpeedX = this.rotSpeedX * 0.96F;
         }

         if (mc.level != null) {
            boolean collideX = !mc.level.isAir(SHARED_AIR_POS.set(
                    Mth.floor(this.posX + this.motionX * 2.0F), Mth.floor(this.posY - this.motionY + 0.1), Mth.floor(this.posZ)));
            boolean collideY = !mc.level.isAir(SHARED_AIR_POS.set(
                    Mth.floor(this.posX), Mth.floor(this.posY + this.motionY * 2.0F), Mth.floor(this.posZ)));
            boolean collideZ = !mc.level.isAir(SHARED_AIR_POS.set(
                    Mth.floor(this.posX), Mth.floor(this.posY - this.motionY + 0.1), Mth.floor(this.posZ + this.motionZ * 2.0F)));

            if (this.motionY != 0.0F) {
               this.motionY -= this.lower ? 0.003333F : 0.01F;
            }

            if (collideY) {
               this.motionY = -this.motionY * (this.lower ? 0.7F : 0.9F);
            }
            if (collideX) {
               this.motionX *= this.lower ? -0.6F : -1.0F;
            }
            if (collideZ) {
               this.motionZ *= this.lower ? -0.6F : -1.0F;
            }
         }

         this.posX += this.motionX;
         this.posY += this.motionY;
         this.posZ += this.motionZ;
      }
   }

   @Environment(EnvType.CLIENT)
   static enum ParticleType {
      HEART("heart", true),
      STAR("star", true),
      SNOW("snowflake", true),
      BLOOM("firefly", true),
      DOLLAR("dollar", true),
      TRIANGLE("triangle", true),
      SAKURA("sakura", true),
      GEMINI("genshin", true),
      SIMS("rhombus", true),
      VEGA_DOLLAR("vega_dollar", true),
      VEGA_BITCOIN("vega_bitcoin", true),
      VEGA_STAR("vega_star", true);

      private final ResourceLocation texture;
      private final boolean rotatable;

      private ParticleType(String name, boolean rotatable) {
         this.texture = ResourceLocation.of("vesence", "textures/world/" + name + ".png");
         this.rotatable = rotatable;
      }

      @Generated
      public ResourceLocation texture() {
         return this.texture;
      }

      @Generated
      public boolean rotatable() {
         return this.rotatable;
      }
   }
}
