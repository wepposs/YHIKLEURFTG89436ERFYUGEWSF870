package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;

/**
 * ChorusESP — показывает, куда телепортирует Chorus Fruit.
 *
 * <p>Пока хорус в руке или его едят — подсвечивается ВЕСЬ возможный радиус
 * телепорта (±8 блоков) цветом темы клиента, а самая вероятная клетка — зелёным
 * (расчёт по точному ванильному алгоритму телепорта).</p>
 *
 * <p>В момент реального телепорта модуль ловит серверный пакет позиции
 * ({@link PlayerPositionLookS2CPacket}) и запоминает ТОЧНУЮ клетку приземления —
 * это гарантированные 100% данные самой игры, а не догадка. Эта клетка
 * подсвечивается зелёным и держится заданное время, чтобы было видно, куда
 * именно закинуло.</p>
 */
@IModule(name = "Chorus ESP", description = "Куда телепортирует хорус: зона + точная точка из пакета", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class ChorusESP extends Module {

   private static final int MAX_DESCENT = 40;
   /** Зелёный — самая вероятная / подтверждённая точка телепорта. */
   private static final int GREEN = 0xFF37FF74;

   public final BooleanSetting fill = new BooleanSetting("Заливка", true);
   public final BooleanSetting outline = new BooleanSetting("Обводка", true);
   public final BooleanSetting beam = new BooleanSetting("Луч к цели", true);
   public final SliderSetting holdTime = new SliderSetting("Держать цель, сек", 6.0, 1.0, 15.0, 0.5, false);

   private static final RenderPipeline FILL_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
               .withLocation(Identifier.of("vesence", "pipeline/chorus_esp_fill"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.TRANSLUCENT)
               .build());
   private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
         RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
               .withLocation(Identifier.of("vesence", "pipeline/chorus_esp_line"))
               .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.DEBUG_LINES)
               .withCull(false)
               .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
               .withDepthWrite(false)
               .withBlend(BlendFunction.TRANSLUCENT)
               .build());

   private static final RenderLayer FILL_LAYER = RenderLayer.of("chorus_esp_fill",
         RenderSetup.builder(FILL_PIPELINE).expectedBufferSize(2097152).translucent().build());
   private static final RenderLayer LINE_LAYER = RenderLayer.of("chorus_esp_line",
         RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(1048576).translucent().build());

   private BufferAllocator allocator = null;

   // Возможная зона телепорта (клетки приземления) + самая вероятная клетка.
   private final List<BlockPos> zoneCells = new ArrayList<>();
   private BlockPos predictedBest = null;
   private long lastComputeMs = 0L;
   private long lastPlayerKey = Long.MIN_VALUE;

   // Захват реального телепорта из пакета.
   private volatile long lastChorusMs = 0L;
   private volatile boolean teleportPending = false;
   private volatile long pendingSinceMs = 0L;
   private volatile Vec3d prePos = null;
   private BlockPos confirmedSpot = null;
   private long confirmedUntilMs = 0L;

   public ChorusESP() {
      addSettings(fill, outline, beam, holdTime);
   }

   @Override
   public void onDisable() {
      super.onDisable();
      this.zoneCells.clear();
      this.predictedBest = null;
      this.confirmedSpot = null;
      this.teleportPending = false;
      if (this.allocator != null) {
         try {
            this.allocator.close();
         } catch (Exception ignored) {
         }
         this.allocator = null;
      }
   }

   // ------------------------------------------------------------------
   //  Ловим реальный телепорт: серверный пакет позиции во время еды хоруса
   // ------------------------------------------------------------------

   @EventInit
   public void onPacket(EventPacket e) {
      if (!this.enable) return;
      if (e.isSend()) return;
      if (!(e.getPacket() instanceof PlayerPositionLookS2CPacket)) return;
      if (mc.player == null) return;
      // Считаем телепортом хоруса только пакет позиции сразу после/во время еды.
      if (System.currentTimeMillis() - this.lastChorusMs > 1200L) return;
      this.teleportPending = true;
      this.pendingSinceMs = System.currentTimeMillis();
      this.prePos = mc.player.getEntityPos();
   }

   // ------------------------------------------------------------------
   //  Рендер каждый кадр
   // ------------------------------------------------------------------

   @EventInit
   public void onRender3D(EventRender3D event) {
      if (!this.enable) return;
      if (mc.world == null || mc.player == null) return;

      try {
         long now = System.currentTimeMillis();

         boolean eating = isEatingChorus();
         if (eating) this.lastChorusMs = now;
         boolean showField = eating || isHoldingChorus();

         // Как только сервер реально телепортнул — фиксируем точную клетку.
         if (this.teleportPending) {
            Vec3d pre = this.prePos;
            boolean moved = pre != null && mc.player.getEntityPos().squaredDistanceTo(pre) > 0.25;
            if (moved || now - this.pendingSinceMs > 150L) {
               this.confirmedSpot = mc.player.getBlockPos();
               this.confirmedUntilMs = now + (long) (this.holdTime.get() * 1000.0);
               this.teleportPending = false;
            }
         }

         boolean confirmedActive = this.confirmedSpot != null && now < this.confirmedUntilMs;

         if (showField) {
            recomputeThrottled();
         } else {
            this.zoneCells.clear();
            this.predictedBest = null;
         }

         boolean fieldActive = showField && !this.zoneCells.isEmpty();
         if (!fieldActive && !confirmedActive) return;

         render(event, fieldActive, confirmedActive, now);
      } catch (Exception ex) {
         this.zoneCells.clear();
      }
   }

   private boolean isHoldingChorus() {
      return mc.player.getMainHandStack().isOf(Items.CHORUS_FRUIT)
            || mc.player.getOffHandStack().isOf(Items.CHORUS_FRUIT);
   }

   private boolean isEatingChorus() {
      return mc.player.isUsingItem() && mc.player.getActiveItem().isOf(Items.CHORUS_FRUIT);
   }

   // ------------------------------------------------------------------
   //  Расчёт возможной зоны и самой вероятной клетки
   // ------------------------------------------------------------------

   private void recomputeThrottled() {
      long now = System.currentTimeMillis();
      long playerKey = mc.player.getBlockPos().asLong();
      if (!this.zoneCells.isEmpty() && now - this.lastComputeMs < 150L && playerKey == this.lastPlayerKey) {
         return;
      }
      this.lastComputeMs = now;
      this.lastPlayerKey = playerKey;
      compute();
   }

   private void compute() {
      this.zoneCells.clear();
      this.predictedBest = null;

      final double px = mc.player.getX();
      final double py = mc.player.getY();
      final double pz = mc.player.getZ();
      final int floorPy = MathHelper.floor(py);
      final int minY = mc.world.getBottomY();

      final int cxMin = MathHelper.floor(px - 8.0);
      final int cxMax = MathHelper.floor(px + 8.0);
      final int czMin = MathHelper.floor(pz - 8.0);
      final int czMax = MathHelper.floor(pz + 8.0);

      // Сырые веса вероятности по клетке приземления (ключ = BlockPos.asLong).
      final Map<Long, Double> raw = new HashMap<>();
      final BlockPos.Mutable cursor = new BlockPos.Mutable();

      for (int cx = cxMin; cx <= cxMax; cx++) {
         double wx = overlap(px - 8.0, px + 8.0, cx) / 16.0;
         if (wx <= 0.0) continue;
         for (int cz = czMin; cz <= czMax; cz++) {
            double wz = overlap(pz - 8.0, pz + 8.0, cz) / 16.0;
            if (wz <= 0.0) continue;
            if (!mc.world.getChunkManager().isChunkLoaded(cx >> 4, cz >> 4)) continue;

            double columnWeight = wx * wz;
            for (int dy = -8; dy <= 7; dy++) {
               int startY = floorPy + dy;
               if (startY <= minY) continue;
               int landingY = descend(cursor, cx, startY, cz, minY);
               if (landingY == Integer.MIN_VALUE) continue;
               long key = BlockPos.asLong(cx, landingY, cz);
               raw.merge(key, columnWeight / 16.0, Double::sum);
            }
         }
      }

      if (raw.isEmpty()) return;

      double bestWeight = -1.0;
      double bestDist = Double.MAX_VALUE;
      long bestKey = 0L;
      for (Map.Entry<Long, Double> e : raw.entrySet()) {
         BlockPos pos = BlockPos.fromLong(e.getKey());
         if (!isValidLanding(pos)) continue;
         this.zoneCells.add(pos);

         double weight = e.getValue();
         double ddx = (pos.getX() + 0.5) - px;
         double ddz = (pos.getZ() + 0.5) - pz;
         double dist = ddx * ddx + ddz * ddz;
         if (weight > bestWeight + 1.0E-9 || (Math.abs(weight - bestWeight) <= 1.0E-9 && dist < bestDist)) {
            bestWeight = weight;
            bestDist = dist;
            bestKey = e.getKey();
         }
      }

      if (!this.zoneCells.isEmpty()) {
         this.predictedBest = BlockPos.fromLong(bestKey);
      }
   }

   /** Длина пересечения непрерывного интервала [lo; hi] с клеткой [cell; cell+1]. */
   private static double overlap(double lo, double hi, int cell) {
      double a = Math.max(lo, cell);
      double b = Math.min(hi, cell + 1.0);
      return Math.max(0.0, b - a);
   }

   /** Спуск вниз до первого блока, который «держит движение». Возвращает Y ступней. */
   private int descend(BlockPos.Mutable cursor, int cx, int startY, int cz, int minY) {
      int limit = Math.max(minY, startY - MAX_DESCENT);
      for (int y = startY; y > limit; y--) {
         cursor.set(cx, y - 1, cz);
         BlockState below = mc.world.getBlockState(cursor);
         if (below.blocksMovement()) {
            return y;
         }
      }
      return Integer.MIN_VALUE;
   }

   /** Помещается ли игрок в клетке приземления без коллизий и жидкости. */
   private boolean isValidLanding(BlockPos pos) {
      double x = pos.getX() + 0.5;
      double y = pos.getY();
      double z = pos.getZ() + 0.5;
      Box box = new Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3).contract(1.0E-7);
      try {
         return mc.world.isSpaceEmpty(mc.player, box) && !mc.world.containsFluid(box);
      } catch (Exception ex) {
         return false;
      }
   }

   // ------------------------------------------------------------------
   //  Отрисовка
   // ------------------------------------------------------------------

   private void render(EventRender3D event, boolean fieldActive, boolean confirmedActive, long now) {
      Vec3d cam = mc.gameRenderer.getCamera().getCameraPos();
      MatrixStack matrices = event.getMatrixStack();
      Matrix4f matrix = matrices.peek().getPositionMatrix();

      if (this.allocator == null) {
         this.allocator = new BufferAllocator(2097152);
      }
      Immediate immediate = VertexConsumerProvider.immediate(this.allocator);

      int theme = setAlpha(Renderer2D.ColorUtil.getClientColor(), 255);
      float pulse = (float) ((Math.sin(now / 1000.0 * Math.PI * 2.0 * 0.6) + 1.0) * 0.5); // 0..1

      try {
         VertexConsumer quads = this.fill.get() ? immediate.getBuffer(FILL_LAYER) : null;
         boolean needLines = this.outline.get() || this.beam.get();
         VertexConsumer lines = needLines ? immediate.getBuffer(LINE_LAYER) : null;

         // Вся зона возможного телепорта + зелёная предсказанная клетка.
         if (fieldActive) {
            for (BlockPos cell : this.zoneCells) {
               if (confirmedActive && cell.equals(this.confirmedSpot)) continue;
               boolean best = !confirmedActive && cell.equals(this.predictedBest);
               int color = best ? GREEN : theme;
               int fillAlpha = best ? (int) (150 + 55 * pulse) : (int) (75 + 30 * pulse);
               int lineAlpha = best ? 255 : 180;
               if (quads != null) {
                  fillBox(quads, matrix, cell, cam, setAlpha(color, fillAlpha));
               }
               if (lines != null && this.outline.get()) {
                  outlineBox(lines, matrix, cell, cam, setAlpha(color, lineAlpha));
               }
               if (lines != null && this.beam.get() && best) {
                  drawBeam(lines, matrix, cell, cam, GREEN, pulse);
               }
            }
         }

         // Точная (100%) клетка из пакета телепорта — поверх всего.
         if (confirmedActive) {
            int fillAlpha = (int) (165 + 60 * pulse);
            if (quads != null) {
               fillBox(quads, matrix, this.confirmedSpot, cam, setAlpha(GREEN, fillAlpha));
            }
            if (lines != null && this.outline.get()) {
               outlineBox(lines, matrix, this.confirmedSpot, cam, setAlpha(GREEN, 255));
            }
            if (lines != null && this.beam.get()) {
               drawBeam(lines, matrix, this.confirmedSpot, cam, GREEN, pulse);
            }
         }

         immediate.draw();
      } catch (Exception ex) {
         if (this.allocator != null) {
            try {
               this.allocator.close();
            } catch (Exception ignored) {
            }
            this.allocator = null;
         }
      }
   }

   private void fillBox(VertexConsumer buf, Matrix4f m, BlockPos pos, Vec3d cam, int color) {
      float r = ((color >> 16) & 0xFF) / 255f;
      float g = ((color >> 8) & 0xFF) / 255f;
      float b = (color & 0xFF) / 255f;
      float a = ((color >> 24) & 0xFF) / 255f;
      float aB = a;
      float aT = a * 0.35f;

      float x0 = (float) (pos.getX() - cam.x);
      float y0 = (float) (pos.getY() - cam.y);
      float z0 = (float) (pos.getZ() - cam.z);
      float x1 = x0 + 1f;
      float y1 = y0 + 1f;
      float z1 = z0 + 1f;

      buf.vertex(m, x0, y0, z0).color(r, g, b, aB);
      buf.vertex(m, x1, y0, z0).color(r, g, b, aB);
      buf.vertex(m, x1, y0, z1).color(r, g, b, aB);
      buf.vertex(m, x0, y0, z1).color(r, g, b, aB);

      buf.vertex(m, x0, y1, z0).color(r, g, b, aT);
      buf.vertex(m, x0, y1, z1).color(r, g, b, aT);
      buf.vertex(m, x1, y1, z1).color(r, g, b, aT);
      buf.vertex(m, x1, y1, z0).color(r, g, b, aT);

      buf.vertex(m, x0, y0, z0).color(r, g, b, aB);
      buf.vertex(m, x0, y1, z0).color(r, g, b, aT);
      buf.vertex(m, x1, y1, z0).color(r, g, b, aT);
      buf.vertex(m, x1, y0, z0).color(r, g, b, aB);

      buf.vertex(m, x0, y0, z1).color(r, g, b, aB);
      buf.vertex(m, x1, y0, z1).color(r, g, b, aB);
      buf.vertex(m, x1, y1, z1).color(r, g, b, aT);
      buf.vertex(m, x0, y1, z1).color(r, g, b, aT);

      buf.vertex(m, x0, y0, z0).color(r, g, b, aB);
      buf.vertex(m, x0, y0, z1).color(r, g, b, aB);
      buf.vertex(m, x0, y1, z1).color(r, g, b, aT);
      buf.vertex(m, x0, y1, z0).color(r, g, b, aT);

      buf.vertex(m, x1, y0, z0).color(r, g, b, aB);
      buf.vertex(m, x1, y1, z0).color(r, g, b, aT);
      buf.vertex(m, x1, y1, z1).color(r, g, b, aT);
      buf.vertex(m, x1, y0, z1).color(r, g, b, aB);
   }

   private void outlineBox(VertexConsumer buf, Matrix4f m, BlockPos pos, Vec3d cam, int color) {
      float x0 = (float) (pos.getX() - cam.x);
      float y0 = (float) (pos.getY() - cam.y);
      float z0 = (float) (pos.getZ() - cam.z);
      float x1 = x0 + 1f;
      float y1 = y0 + 1f;
      float z1 = z0 + 1f;

      line(buf, m, x0, y0, z0, x1, y0, z0, color, color);
      line(buf, m, x1, y0, z0, x1, y0, z1, color, color);
      line(buf, m, x1, y0, z1, x0, y0, z1, color, color);
      line(buf, m, x0, y0, z1, x0, y0, z0, color, color);

      line(buf, m, x0, y1, z0, x1, y1, z0, color, color);
      line(buf, m, x1, y1, z0, x1, y1, z1, color, color);
      line(buf, m, x1, y1, z1, x0, y1, z1, color, color);
      line(buf, m, x0, y1, z1, x0, y1, z0, color, color);

      line(buf, m, x0, y0, z0, x0, y1, z0, color, color);
      line(buf, m, x1, y0, z0, x1, y1, z0, color, color);
      line(buf, m, x1, y0, z1, x1, y1, z1, color, color);
      line(buf, m, x0, y0, z1, x0, y1, z1, color, color);
   }

   private void drawBeam(VertexConsumer buf, Matrix4f m, BlockPos pos, Vec3d cam, int color, float pulse) {
      float x0 = (float) (pos.getX() - cam.x);
      float yTop = (float) (pos.getY() - cam.y) + 1f;
      float z0 = (float) (pos.getZ() - cam.z);
      float x1 = x0 + 1f;
      float z1 = z0 + 1f;
      float cx = x0 + 0.5f;
      float cz = z0 + 0.5f;
      float height = 4.0f + 1.5f * pulse;
      int base = setAlpha(color, clampAlpha((int) (210 * (0.6f + 0.4f * pulse))));
      int top = setAlpha(color, 0);

      line(buf, m, x0, yTop, z0, x0, yTop + height, z0, base, top);
      line(buf, m, x1, yTop, z0, x1, yTop + height, z0, base, top);
      line(buf, m, x1, yTop, z1, x1, yTop + height, z1, base, top);
      line(buf, m, x0, yTop, z1, x0, yTop + height, z1, base, top);
      line(buf, m, cx, yTop, cz, cx, yTop + height, cz, base, top);
   }

   private static void line(VertexConsumer buf, Matrix4f m, float x1, float y1, float z1, float x2, float y2, float z2, int c1, int c2) {
      buf.vertex(m, x1, y1, z1).color(((c1 >> 16) & 0xFF) / 255f, ((c1 >> 8) & 0xFF) / 255f, (c1 & 0xFF) / 255f, ((c1 >> 24) & 0xFF) / 255f);
      buf.vertex(m, x2, y2, z2).color(((c2 >> 16) & 0xFF) / 255f, ((c2 >> 8) & 0xFF) / 255f, (c2 & 0xFF) / 255f, ((c2 >> 24) & 0xFF) / 255f);
   }

   private static int setAlpha(int color, int alpha) {
      return (color & 0x00FFFFFF) | ((clampAlpha(alpha) & 0xFF) << 24);
   }

   private static int clampAlpha(int alpha) {
      return Math.max(0, Math.min(255, alpha));
   }
}
