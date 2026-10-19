package vesence.utils.debug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Фоновый «сторож зависаний» (ANR-style watchdog) для диагностики подвисаний
 * клиента Vesence.
 *
 * <h2>Идея</h2>
 * Всё тяжёлое крутится на ГЛАВНОМ (render) потоке: {@code tick()}, отрисовка,
 * диспетчеризация событий {@link vesence.event.EventManager}. Если поток
 * застрял (тяжёлый цикл, блокирующий I/O, GC-пауза, дедлок) — игра «фризит».
 * Отследить это ИЗНУТРИ главного потока нельзя — он же и завис.
 *
 * <p>Поэтому диагностика вынесена в ОТДЕЛЬНЫЙ демон-поток. Главный поток лишь
 * дёшево «отмечается» ({@link #beat(String)}) в начале каждого кадра/тика.
 * Сторож раз в {@link #POLL_MS} мс смотрит, как давно был последний удар пульса.
 * Если тишина дольше порога — главный поток завис, и сторож снимает СТЕК
 * главного потока (это и есть «из-за чего подвисает»), состояние всех потоков,
 * память, GC, ищет дедлоки и пишет ВСЁ во временный файл.
 *
 * <h2>Куда пишет</h2>
 * {@code <run>/vesence-debug/freeze-<сессия>.log} (для {@code gradlew runClient}
 * рабочая папка — это {@code run/}). Если недоступно — во временную папку ОС.
 * Каждая запись сразу flush-ится, чтобы данные пережили жёсткое завершение
 * зависшего клиента. Первая запись делается ПОСЛЕ старта игрового цикла, так
 * что путь резолвится, когда рабочая директория уже готова.
 */
public final class FreezeWatchdog {

   private FreezeWatchdog() {}

   // ─── Пороги (мс) ────────────────────────────────────────────────────────
   /** Пауза дольше этого = видимый фриз, снимаем полный дамп. */
   private static volatile long freezeThresholdMs =
      Long.getLong("vesence.debug.freezeMs", 300L);
   /** Пауза [lag..freeze) = короткий лаг-спайк, только считаем. */
   private static volatile long lagThresholdMs =
      Long.getLong("vesence.debug.lagMs", 100L);
   private static final long POLL_MS = Long.getLong("vesence.debug.pollMs", 25L);
   private static final long RESAMPLE_MS = Long.getLong("vesence.debug.resampleMs", 250L);
   private static final long HEALTH_INTERVAL_MS = Long.getLong("vesence.debug.healthMs", 30_000L);
   private static final long SLOW_HANDLER_MS = Long.getLong("vesence.debug.handlerMs", 5L);

   // ─── Пульс главного потока ─────────────────────────────────────────────────
   private static volatile long lastBeatNs = System.nanoTime();
   private static volatile String phase = "init";
   private static volatile long phaseStartNs = System.nanoTime();
   private static volatile Thread mainThread;

   /** Метка текущей диспетчеризации события (ставит EventManager). */
   private static volatile Class<?> dispatchEvent;
   private static volatile Object dispatchListener;
   private static volatile Method dispatchMethod;
   private static volatile long dispatchStartNs;

   /** Явно размеченная тяжёлая секция на главном потоке. */
   private static volatile String currentMainSection;
   private static volatile long currentMainSectionStartNs;

   /** Быстрый флаг «сторож работает» — читается на горячем пути EventManager. */
   public static volatile boolean TRACKING = false;

   // ─── Состояние сторожа ─────────────────────────────────────────────────────
   private static final AtomicBoolean running = new AtomicBoolean(false);
   private static Thread watchdogThread;
   private static volatile File logFile;
   private static final Object writeLock = new Object();
   private static long startWallMs;

   private static final AtomicLong freezeCount = new AtomicLong();
   private static final AtomicLong lagSpikeCount = new AtomicLong();
   private static volatile long longestFreezeMs = 0L;
   private static volatile long totalFreezeMs = 0L;

   private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

   private static final SimpleDateFormat TS =
      new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT);
   private static final SimpleDateFormat FILE_TS =
      new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT);

   private static final ConcurrentHashMap<String, HandlerStat> handlerStats = new ConcurrentHashMap<>();
   private static final ConcurrentHashMap<String, HandlerStat> sectionStats = new ConcurrentHashMap<>();

   // ══════════════════════════════════════════════════════════════════════════
   //  Публичное API управления
   // ══════════════════════════════════════════════════════════════════════════

   public static synchronized void start() {
      if (running.get()) {
         return;
      }
      running.set(true);
      startWallMs = System.currentTimeMillis();
      lastBeatNs = System.nanoTime();

      watchdogThread = new Thread(FreezeWatchdog::loop, "vesence-FreezeWatchdog");
      watchdogThread.setDaemon(true);
      watchdogThread.setPriority(Thread.NORM_PRIORITY - 1);
      watchdogThread.start();

      // TRACKING включаем только после успешного старта потока.
      TRACKING = true;
   }

   public static synchronized void stop() {
      if (!running.get()) {
         return;
      }
      running.set(false);
      TRACKING = false;
      Thread t = watchdogThread;
      if (t != null) {
         t.interrupt();
      }
      watchdogThread = null;
      write("=== WATCHDOG STOPPED " + TS.format(new Date()) + " ===\n");
   }

   public static boolean isRunning() {
      return running.get();
   }

   /**
    * Отметка пульса главного потока. Вызывать в начале кадра (render) и тика.
    * Дёшево: пара volatile-записей, без аллокаций.
    */
   public static void beat(String phaseName) {
      long now = System.nanoTime();
      if (mainThread == null) {
         mainThread = Thread.currentThread();
      }
      if (phase != phaseName) {
         phase = phaseName;
         phaseStartNs = now;
      }
      lastBeatNs = now;
   }

   public static void setFreezeThresholdMs(long ms) {
      freezeThresholdMs = Math.max(50L, ms);
   }

   public static long getFreezeThresholdMs() {
      return freezeThresholdMs;
   }

   public static File getLogFile() {
      return logFile;
   }

   // ══════════════════════════════════════════════════════════════════════════
   //  Интеграция с EventManager (вызывается ТОЛЬКО когда TRACKING == true)
   // ══════════════════════════════════════════════════════════════════════════

   public static void onEventStart(Class<?> eventClass) {
      dispatchEvent = eventClass;
      dispatchStartNs = System.nanoTime();
   }

   public static void onHandlerEnter(Object listener, Method method) {
      dispatchListener = listener;
      dispatchMethod = method;
   }

   public static void onHandlerExit(Object listener, Method method, long elapsedNs) {
      if (elapsedNs >= SLOW_HANDLER_MS * 1_000_000L) {
         String key = listener.getClass().getSimpleName() + "#" + method.getName()
            + " (" + (dispatchEvent == null ? "?" : dispatchEvent.getSimpleName()) + ")";
         handlerStats.computeIfAbsent(key, k -> new HandlerStat()).record(elapsedNs);
      }
   }

   public static void onEventEnd() {
      dispatchListener = null;
      dispatchMethod = null;
   }

   /**
    * Открывает «тяжёлую секцию» — обернуть подозрительно долгие операции:
    * <pre>
    *   long s = FreezeWatchdog.sectionEnter("neuralPredict");
    *   try { ... } finally { FreezeWatchdog.sectionExit("neuralPredict", s); }
    * </pre>
    * @return момент старта в нс (0 если сторож выключен)
    */
   public static long sectionEnter(String name) {
      if (!TRACKING) {
         return 0L;
      }
      long now = System.nanoTime();
      if (Thread.currentThread() == mainThread) {
         currentMainSection = name;
         currentMainSectionStartNs = now;
      }
      return now;
   }

   public static void sectionExit(String name, long startNs) {
      if (startNs == 0L) {
         return;
      }
      long elapsed = System.nanoTime() - startNs;
      if (elapsed >= 1_000_000L) {
         sectionStats.computeIfAbsent(name, k -> new HandlerStat()).record(elapsed);
      }
      if (Thread.currentThread() == mainThread) {
         currentMainSection = null;
      }
   }

   // ══════════════════════════════════════════════════════════════════════════
   //  Основной цикл сторожа
   // ══════════════════════════════════════════════════════════════════════════

   private static void loop() {
      long lastHealthMs = System.currentTimeMillis();
      long[] gcBaseline = gcSnapshot();
      boolean headerWritten = false;

      boolean inFreeze = false;
      long freezeStartWallMs = 0L;
      long lastSampleNs = 0L;
      int sampleCount = 0;
      String firstCulprit = null;

      while (running.get()) {
         try {
            Thread.sleep(POLL_MS);
         } catch (InterruptedException e) {
            if (!running.get()) {
               break;
            }
            continue;
         }

         Thread mt = mainThread;
         if (mt == null) {
            // Игровой цикл ещё не начался (нет ни одного beat).
            continue;
         }

         // Шапку пишем ЗДЕСЬ (а не в start): к этому моменту рабочая папka
         // клиента уже готова, путь резолвится корректно.
         if (!headerWritten) {
            writeHeader();
            headerWritten = true;
         }

         long now = System.nanoTime();
         long stallMs = (now - lastBeatNs) / 1_000_000L;

         if (stallMs >= freezeThresholdMs) {
            if (!inFreeze) {
               inFreeze = true;
               freezeStartWallMs = System.currentTimeMillis();
               lastSampleNs = now;
               sampleCount = 1;
               long[] gcNow = gcSnapshot();
               firstCulprit = topCulprit(mt.getStackTrace());
               writeFreezeBegin(mt, stallMs, gcNow, gcBaseline);
               gcBaseline = gcNow;
            } else if (now - lastSampleNs >= RESAMPLE_MS * 1_000_000L) {
               lastSampleNs = now;
               sampleCount++;
               writeFreezeSample(mt, stallMs, sampleCount);
            }
         } else {
            if (inFreeze) {
               inFreeze = false;
               long durMs = System.currentTimeMillis() - freezeStartWallMs;
               freezeCount.incrementAndGet();
               totalFreezeMs += durMs;
               if (durMs > longestFreezeMs) {
                  longestFreezeMs = durMs;
               }
               long[] gcNow = gcSnapshot();
               writeFreezeEnd(durMs, sampleCount, firstCulprit, gcNow, gcBaseline);
               gcBaseline = gcNow;
            } else if (stallMs >= lagThresholdMs) {
               lagSpikeCount.incrementAndGet();
            }
         }

         long nowWall = System.currentTimeMillis();
         if (nowWall - lastHealthMs >= HEALTH_INTERVAL_MS) {
            lastHealthMs = nowWall;
            long[] gcNow = gcSnapshot();
            writeHealth(gcNow, gcBaseline);
            gcBaseline = gcNow;
         }
      }
   }

   // ══════════════════════════════════════════════════════════════════════════
   //  Форматирование и запись
   // ══════════════════════════════════════════════════════════════════════════

   private static void writeHeader() {
      StringBuilder sb = new StringBuilder(512);
      sb.append("================================================================\n");
      sb.append(" Vesence FreezeWatchdog — журнал диагностики подвисаний\n");
      sb.append("================================================================\n");
      sb.append("Старт сессии : ").append(TS.format(new Date())).append('\n');
      sb.append("Порог фриза  : ").append(freezeThresholdMs).append(" мс\n");
      sb.append("Порог лага   : ").append(lagThresholdMs).append(" мс\n");
      sb.append("Опрос        : ").append(POLL_MS).append(" мс\n");
      sb.append("JVM          : ").append(System.getProperty("java.version"))
        .append(" / ").append(System.getProperty("os.name")).append('\n');
      Runtime rt = Runtime.getRuntime();
      sb.append("CPU ядер     : ").append(rt.availableProcessors()).append('\n');
      sb.append("Куча (max)   : ").append(mb(rt.maxMemory())).append(" МБ\n");
      sb.append("----------------------------------------------------------------\n");
      sb.append("При подвисании главного потока фиксируется его стек — верхний\n");
      sb.append("кадр кода клиента (vesence.*) = вероятная причина зависания.\n");
      sb.append("----------------------------------------------------------------\n\n");
      write(sb.toString());
   }

   private static void writeFreezeBegin(Thread mt, long stallMs, long[] gcNow, long[] gcBase) {
      StringBuilder sb = new StringBuilder(2048);
      sb.append("\n########## ФРИЗ НАЧАЛО ").append(TS.format(new Date())).append(" ##########\n");
      sb.append("Главный поток не отвечает уже ~").append(stallMs).append(" мс\n");
      sb.append("Фаза петли        : ").append(phase)
        .append("  (в фазе ~").append((System.nanoTime() - phaseStartNs) / 1_000_000L).append(" мс)\n");
      appendDispatchInfo(sb);
      appendMemory(sb);
      appendGc(sb, gcNow, gcBase);
      appendMainStack(sb, mt);
      appendDeadlocks(sb);
      appendOtherRunnable(sb, mt);
      appendTopSections(sb);
      appendTopHandlers(sb);
      sb.append("(снимок #1; если фриз продлится — будут доп. снимки)\n");
      write(sb.toString());
   }

   private static void writeFreezeSample(Thread mt, long stallMs, int n) {
      StringBuilder sb = new StringBuilder(1024);
      sb.append("\n----- ФРИЗ снимок #").append(n)
        .append(" (не отвечает ~").append(stallMs).append(" мс) ")
        .append(TS.format(new Date())).append(" -----\n");
      sb.append("Фаза: ").append(phase).append('\n');
      appendDispatchInfo(sb);
      appendMainStack(sb, mt);
      write(sb.toString());
   }

   private static void writeFreezeEnd(long durMs, int samples, String culprit, long[] gcNow, long[] gcBase) {
      StringBuilder sb = new StringBuilder(512);
      sb.append("\n########## ФРИЗ КОНЕЦ ").append(TS.format(new Date())).append(" ##########\n");
      sb.append("Длительность      : ").append(durMs).append(" мс\n");
      sb.append("Снимков стека     : ").append(samples).append('\n');
      sb.append("Вероятная причина : ").append(culprit == null ? "н/д" : culprit).append('\n');
      long gcDeltaMs = gcTimeDelta(gcNow, gcBase);
      if (gcDeltaMs > 0) {
         sb.append("GC за фриз        : +").append(gcDeltaMs)
           .append(" мс пауз GC (").append(gcCountDelta(gcNow, gcBase)).append(" сборок)");
         if (gcDeltaMs >= durMs * 0.5) {
            sb.append("  <== ФРИЗ В ОСНОВНОМ ИЗ-ЗА GC");
         }
         sb.append('\n');
      }
      sb.append("Итого фризов за сессию: ").append(freezeCount.get())
        .append(", самый долгий: ").append(longestFreezeMs).append(" мс\n");
      sb.append("############################################################\n");
      write(sb.toString());
   }

   private static void writeHealth(long[] gcNow, long[] gcBase) {
      long gcDelta = gcTimeDelta(gcNow, gcBase);
      Runtime rt = Runtime.getRuntime();
      long usedMb = mb(rt.totalMemory() - rt.freeMemory());
      StringBuilder sb = new StringBuilder(512);
      sb.append("\n· здоровье ").append(TS.format(new Date()))
        .append(" | аптайм ").append((System.currentTimeMillis() - startWallMs) / 1000L).append("с")
        .append(" | фризов ").append(freezeCount.get())
        .append(" (макс ").append(longestFreezeMs).append("мс)")
        .append(" | лаг-спайков ").append(lagSpikeCount.get())
        .append(" | память ").append(usedMb).append("МБ")
        .append(" | GC ").append(gcDelta).append("мс/").append(HEALTH_INTERVAL_MS / 1000L).append("с\n");
      String top = topHandlersLine();
      if (top != null) {
         sb.append("  медленные обработчики: ").append(top).append('\n');
      }
      String sects = topSectionsLine();
      if (sects != null) {
         sb.append("  тяжёлые секции: ").append(sects).append('\n');
      }
      write(sb.toString());
   }

   /**
    * Немедленный полный снимок состояния (по команде {@code .debug dump}).
    * @return абсолютный путь к файлу журнала
    */
   public static String dumpNow() {
      ensureLogFile();
      StringBuilder sb = new StringBuilder(4096);
      sb.append("\n========== РУЧНОЙ ДАМП ").append(TS.format(new Date())).append(" ==========\n");
      long stallMs = (System.nanoTime() - lastBeatNs) / 1_000_000L;
      sb.append("Пульс гл. потока  : ").append(stallMs).append(" мс назад")
        .append(stallMs >= freezeThresholdMs ? "  <== СЕЙЧАС ФРИЗ" : "").append('\n');
      sb.append("Фаза петли        : ").append(phase).append('\n');
      appendDispatchInfo(sb);
      appendMemory(sb);
      appendGc(sb, gcSnapshot(), gcSnapshot());
      sb.append("Фризов за сессию  : ").append(freezeCount.get())
        .append(" | лаг-спайков: ").append(lagSpikeCount.get())
        .append(" | самый долгий: ").append(longestFreezeMs).append(" мс\n");
      Thread mt = mainThread;
      if (mt != null) {
         appendMainStack(sb, mt);
      }
      appendDeadlocks(sb);
      appendAllThreadsBrief(sb);
      appendTopSections(sb);
      appendTopHandlers(sb);
      sb.append("======================================================\n");
      write(sb.toString());
      File f = logFile;
      return f == null ? "(файл журнала недоступен)" : f.getAbsolutePath();
   }

   public static String statusLine() {
      long stallMs = (System.nanoTime() - lastBeatNs) / 1_000_000L;
      return String.format(Locale.ROOT,
         "Debug %s | фризов: %d (макс %d мс, сумм %d мс) | лаг-спайков: %d | пульс: %d мс назад | порог: %d мс",
         running.get() ? "ON" : "off",
         freezeCount.get(), longestFreezeMs, totalFreezeMs, lagSpikeCount.get(),
         stallMs, freezeThresholdMs);
   }

   // ─── секции дампа ─────────────────────────────────────────────────────────

   private static void appendDispatchInfo(StringBuilder sb) {
      String sec = currentMainSection;
      if (sec != null) {
         long inSec = (System.nanoTime() - currentMainSectionStartNs) / 1_000_000L;
         sb.append("Тяжёлая секция    : ").append(sec)
           .append("  (выполняется ~").append(inSec).append(" мс)  <== ВОТ ГДЕ ВИСНЕМ\n");
      }
      Class<?> ev = dispatchEvent;
      Object li = dispatchListener;
      Method me = dispatchMethod;
      if (ev != null) {
         sb.append("Событие в работе  : ").append(ev.getSimpleName());
         long inEvent = (System.nanoTime() - dispatchStartNs) / 1_000_000L;
         sb.append("  (диспетчеризуется ~").append(inEvent).append(" мс)\n");
      }
      if (li != null && me != null) {
         sb.append("Обработчик        : ")
           .append(li.getClass().getName()).append('#').append(me.getName())
           .append("  <== скорее всего он и висит\n");
      }
   }

   private static void appendMemory(StringBuilder sb) {
      Runtime rt = Runtime.getRuntime();
      long max = rt.maxMemory();
      long used = rt.totalMemory() - rt.freeMemory();
      sb.append("Память куча       : ").append(mb(used)).append(" / ").append(mb(max))
        .append(" МБ (").append(pct(used, max)).append("%)");
      if (pct(used, max) >= 92) {
         sb.append("  <== куча почти полна, вероятны GC-паузы");
      }
      sb.append('\n');
   }

   private static void appendGc(StringBuilder sb, long[] now, long[] base) {
      long dtMs = gcTimeDelta(now, base);
      long dc = gcCountDelta(now, base);
      sb.append("GC всего          : ").append(now[0]).append(" сборок, ")
        .append(now[1]).append(" мс пауз");
      if (dc > 0) {
         sb.append("  (с прошлого замера +").append(dc).append(" / +").append(dtMs).append(" мс)");
      }
      sb.append('\n');
   }

   private static void appendMainStack(StringBuilder sb, Thread mt) {
      sb.append("--- СТЕК ГЛАВНОГО ПОТОКА (\"").append(mt.getName())
        .append("\", state=").append(mt.getState()).append(") ---\n");
      StackTraceElement[] st = mt.getStackTrace();
      if (st.length == 0) {
         sb.append("  (стек пуст — возможно, поток в нативном коде)\n");
      }
      for (StackTraceElement e : st) {
         sb.append("    at ").append(e).append('\n');
      }
      sb.append('\n');
   }

   private static void appendDeadlocks(StringBuilder sb) {
      try {
         long[] ids = THREADS.findDeadlockedThreads();
         if (ids == null || ids.length == 0) {
            return;
         }
         sb.append("!!! ОБНАРУЖЕН ДЕДЛОК потоков: ").append(ids.length).append(" !!!\n");
         ThreadInfo[] infos = THREADS.getThreadInfo(ids, true, true);
         for (ThreadInfo ti : infos) {
            if (ti == null) {
               continue;
            }
            sb.append("  \"").append(ti.getThreadName()).append("\" state=").append(ti.getThreadState());
            LockInfo lock = ti.getLockInfo();
            if (lock != null) {
               sb.append(" ждёт ").append(lock);
            }
            if (ti.getLockOwnerName() != null) {
               sb.append(" держит ").append(ti.getLockOwnerName());
            }
            sb.append('\n');
            for (StackTraceElement e : ti.getStackTrace()) {
               sb.append("      at ").append(e).append('\n');
            }
         }
         sb.append('\n');
      } catch (Throwable ignored) {
      }
   }

   private static void appendOtherRunnable(StringBuilder sb, Thread mt) {
      try {
         Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
         List<Map.Entry<Thread, StackTraceElement[]>> interesting = new ArrayList<>();
         for (Map.Entry<Thread, StackTraceElement[]> en : all.entrySet()) {
            Thread t = en.getKey();
            if (t == mt || t == watchdogThread) {
               continue;
            }
            Thread.State s = t.getState();
            if (s == Thread.State.BLOCKED || (s == Thread.State.RUNNABLE && en.getValue().length > 0)) {
               interesting.add(en);
            }
         }
         if (interesting.isEmpty()) {
            return;
         }
         sb.append("--- Другие активные потоки (могут держать лок/грузить CPU) ---\n");
         int shown = 0;
         for (Map.Entry<Thread, StackTraceElement[]> en : interesting) {
            if (shown++ >= 12) {
               sb.append("  ... и ещё ").append(interesting.size() - 12).append(" потоков\n");
               break;
            }
            Thread t = en.getKey();
            sb.append("  \"").append(t.getName()).append("\" ").append(t.getState()).append('\n');
            StackTraceElement[] st = en.getValue();
            int frames = Math.min(4, st.length);
            for (int i = 0; i < frames; i++) {
               sb.append("      at ").append(st[i]).append('\n');
            }
         }
         sb.append('\n');
      } catch (Throwable ignored) {
      }
   }

   private static void appendAllThreadsBrief(StringBuilder sb) {
      try {
         Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
         sb.append("--- Все потоки (").append(all.size()).append("): состояние + верхний кадр ---\n");
         all.entrySet().stream()
            .sorted(Comparator.comparing(e -> e.getKey().getName()))
            .forEach(en -> {
               Thread t = en.getKey();
               StackTraceElement[] st = en.getValue();
               sb.append("  ").append(pad(t.getState().toString(), 13)).append(' ').append(t.getName());
               if (st.length > 0) {
                  sb.append("  | ").append(st[0]);
               }
               sb.append('\n');
            });
         sb.append('\n');
      } catch (Throwable ignored) {
      }
   }

   private static void appendTopSections(StringBuilder sb) {
      List<Map.Entry<String, HandlerStat>> list = new ArrayList<>(sectionStats.entrySet());
      if (list.isEmpty()) {
         return;
      }
      list.sort((a, b) -> Long.compare(b.getValue().maxNs, a.getValue().maxNs));
      sb.append("--- Тяжёлые размеченные секции (агрегат за сессию) ---\n");
      for (Map.Entry<String, HandlerStat> e : list) {
         HandlerStat s = e.getValue();
         sb.append(String.format(Locale.ROOT, "  %-24s x%-6d max %.1f мс, avg %.2f мс, сумм %.0f мс\n",
            e.getKey(), s.count, s.maxNs / 1e6, (s.totalNs / (double) s.count) / 1e6, s.totalNs / 1e6));
      }
      sb.append('\n');
   }

   private static void appendTopHandlers(StringBuilder sb) {
      List<Map.Entry<String, HandlerStat>> list = new ArrayList<>(handlerStats.entrySet());
      if (list.isEmpty()) {
         return;
      }
      list.sort((a, b) -> Long.compare(b.getValue().maxNs, a.getValue().maxNs));
      sb.append("--- Самые медленные обработчики событий (агрегат за сессию) ---\n");
      int shown = 0;
      for (Map.Entry<String, HandlerStat> e : list) {
         if (shown++ >= 12) {
            break;
         }
         HandlerStat s = e.getValue();
         sb.append(String.format(Locale.ROOT, "  %-54s x%-5d max %.1f мс, avg %.1f мс\n",
            e.getKey(), s.count, s.maxNs / 1e6, (s.totalNs / (double) s.count) / 1e6));
      }
      sb.append('\n');
   }

   private static String topHandlersLine() {
      List<Map.Entry<String, HandlerStat>> list = new ArrayList<>(handlerStats.entrySet());
      if (list.isEmpty()) {
         return null;
      }
      list.sort((a, b) -> Long.compare(b.getValue().maxNs, a.getValue().maxNs));
      StringBuilder sb = new StringBuilder();
      int shown = 0;
      for (Map.Entry<String, HandlerStat> e : list) {
         if (shown++ >= 3) {
            break;
         }
         if (shown > 1) {
            sb.append("; ");
         }
         sb.append(e.getKey()).append(String.format(Locale.ROOT, " max %.1fмс", e.getValue().maxNs / 1e6));
      }
      return sb.toString();
   }

   private static String topSectionsLine() {
      List<Map.Entry<String, HandlerStat>> list = new ArrayList<>(sectionStats.entrySet());
      if (list.isEmpty()) {
         return null;
      }
      list.sort((a, b) -> Long.compare(b.getValue().maxNs, a.getValue().maxNs));
      StringBuilder sb = new StringBuilder();
      int shown = 0;
      for (Map.Entry<String, HandlerStat> e : list) {
         if (shown++ >= 4) {
            break;
         }
         if (shown > 1) {
            sb.append("; ");
         }
         HandlerStat s = e.getValue();
         sb.append(e.getKey()).append(String.format(Locale.ROOT, " x%d max %.1fмс", s.count, s.maxNs / 1e6));
      }
      return sb.toString();
   }

   private static String topCulprit(StackTraceElement[] st) {
      for (StackTraceElement e : st) {
         String cn = e.getClassName();
         if (cn.startsWith("vesence.") || cn.startsWith("xyz.")) {
            return e.getClassName() + "." + e.getMethodName() + "():" + e.getLineNumber();
         }
      }
      return st.length > 0 ? (st[0].getClassName() + "." + st[0].getMethodName()) : null;
   }

   // ─── GC ────────────────────────────────────────────────────────────────────

   private static long[] gcSnapshot() {
      long c = 0;
      long t = 0;
      for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
         long cc = gc.getCollectionCount();
         long tt = gc.getCollectionTime();
         if (cc > 0) {
            c += cc;
         }
         if (tt > 0) {
            t += tt;
         }
      }
      return new long[] {c, t};
   }

   private static long gcTimeDelta(long[] now, long[] base) {
      return Math.max(0, now[1] - base[1]);
   }

   private static long gcCountDelta(long[] now, long[] base) {
      return Math.max(0, now[0] - base[0]);
   }

   // ─── файл ─────────────────────────────────────────────────────────────────

   private static void ensureLogFile() {
      if (logFile != null) {
         return;
      }
      synchronized (writeLock) {
         if (logFile != null) {
            return;
         }
         File dir = resolveDir();
         //noinspection ResultOfMethodCallIgnored
         dir.mkdirs();
         logFile = new File(dir, "freeze-" + FILE_TS.format(new Date()) + ".log");
      }
   }

   private static File resolveDir() {
      // Рабочая папка процесса. Для `gradlew runClient` это папка run/ проекта.
      try {
         String userDir = System.getProperty("user.dir");
         if (userDir != null && !userDir.isEmpty()) {
            return new File(userDir, "vesence-debug");
         }
      } catch (Throwable ignored) {
      }
      return new File(System.getProperty("java.io.tmpdir", "."), "vesence-debug");
   }

   private static void write(String text) {
      ensureLogFile();
      File f = logFile;
      if (f == null) {
         return;
      }
      synchronized (writeLock) {
         try (Writer w = new OutputStreamWriter(new FileOutputStream(f, true), StandardCharsets.UTF_8)) {
            w.write(text);
            w.flush();
         } catch (Throwable t) {
            // Диагностика не должна ронять клиент.
         }
      }
   }

   // ─── утилиты ────────────────────────────────────────────────────────────────

   private static long mb(long bytes) {
      return bytes / (1024L * 1024L);
   }

   private static long pct(long part, long whole) {
      return whole <= 0 ? 0 : (part * 100L) / whole;
   }

   private static String pad(String s, int len) {
      if (s.length() >= len) {
         return s;
      }
      StringBuilder sb = new StringBuilder(s);
      while (sb.length() < len) {
         sb.append(' ');
      }
      return sb.toString();
   }

   private static final class HandlerStat {
      volatile long count;
      volatile long totalNs;
      volatile long maxNs;

      synchronized void record(long ns) {
         count++;
         totalNs += ns;
         if (ns > maxNs) {
            maxNs = ns;
         }
      }
   }
}
