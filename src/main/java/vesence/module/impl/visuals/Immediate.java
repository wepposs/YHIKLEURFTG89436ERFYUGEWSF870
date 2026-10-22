package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.rendertype.RenderType;
import vesence.utils.render.world.WorldRenderer;

/**
 * Батчер геометрии по слоям — замена удалённому в 26.3 {@code MultiBufferSource.immediate}.
 *
 * <p>Изначально этот класс собирал mesh и тут же рисовал его своим
 * {@code RenderPass} прямо из {@code EventRender3D}. В 26.3 так нельзя: событие
 * приходит внутрь отрисовки мира, когда кадровый граф ещё кодируется, и
 * создание собственного прохода там либо вешает кадр, либо не даёт ничего —
 * геометрия собирается и молча выбрасывается. Именно поэтому после порта
 * перестала рисоваться почти вся визуальная часть мода.
 *
 * <p>Теперь отправка отложенная: {@link WorldRenderer.ImmediateBuffers} ставит
 * задачу в пост-GUI проход, где кадровый граф уже не кодируется. API класса
 * остался прежним, поэтому все модули, которые его используют, заработали без
 * правок.
 *
 * <p><b>Один экземпляр на всех.</b> Это не оптимизация, а условие корректности.
 * {@code ByteBufferBuilder} — bump-аллокатор: если каждый модуль создаёт свой
 * батчер, они растут в разных аренах и все, кроме последнего, до экрана не
 * доходят. А если закрывать арену сразу после отправки, модуль, который пишет
 * вершины повторно, попадает в освобождённую память — это уже не «не рисуется»,
 * а падение JVM с {@code EXCEPTION_ACCESS_VIOLATION}. Поэтому арена одна,
 * живёт весь сеанс и переиспользуется между кадрами.
 *
 * <p>Флага «уже отправлено» здесь намеренно нет. Отправка идемпотентна: слои
 * общие, первый же поставленный в очередь проход выводит всё накопленное, а
 * остальные находят пустые буферы и ничего не делают. Флаг был бы опасен —
 * если очередь задач переполнится и её очистят, он залипнет и отрисовка
 * замолчит навсегда.
 */
public final class Immediate implements AutoCloseable {

   /**
    * Размер арены.
    *
    * <p>Арена общая, поэтому запас взят с большим избытком: вершина
    * {@code POSITION_COLOR} занимает 16 байт, и 4 МиБ — это четверть миллиона
    * вершин, то есть кадр целиком со всеми эффектами мода.
    */
   private static final int BUFFER_CAPACITY_BYTES = 4 * 1024 * 1024;

   /** Арена на весь сеанс: освобождать её между кадрами нельзя (см. описание класса). */
   private static ByteBufferBuilder allocator;

   /** Общий батчер: все модули пишут в одни и те же builders. */
   private static WorldRenderer.ImmediateBuffers buffers;

   public Immediate() {
      ensureShared();
   }

   private static void ensureShared() {
      if (buffers == null) {
         allocator = new ByteBufferBuilder(BUFFER_CAPACITY_BYTES);
         buffers = WorldRenderer.ImmediateBuffers.create(allocator);
      }
   }

   public VertexConsumer getBuffer(RenderType layer) {
      ensureShared();
      return buffers.getBuffer(layer);
   }

   /**
    * Ставит накопленную геометрию в очередь пост-GUI прохода.
    *
    * <p>Вызывать можно сколько угодно раз за кадр: слои общие, поэтому первый
    * проход выводит всё, а последующие не находят геометрии.
    */
   public void draw() {
      ensureShared();
      buffers.draw();
   }

   /** То же, но с явным целевым таргетом. */
   public void draw(RenderTarget target) {
      ensureShared();
      buffers.draw(target);
   }

   /** Сбрасывает накопленное, ничего не рисуя. */
   public void reset() {
      ensureShared();
      buffers.discard();
   }

   @Override
   public void close() {
      this.reset();
   }
}
