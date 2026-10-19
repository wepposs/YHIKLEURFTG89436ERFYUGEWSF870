package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

import vesence.utils.debug.FreezeWatchdog;

/**
 * {@code .debug} — управление сторожем зависаний (FreezeWatchdog).
 *
 * <p>Сторож работает В ФОНЕ и пишет диагностику подвисаний во временный файл
 * {@code <run>/vesence-debug/freeze-<сессия>.log}. Когда игра «фризит», в файл
 * попадает стек главного потока (из-за чего висим), состояние потоков, память,
 * GC и дедлоки.
 *
 * <ul>
 *   <li>{@code .debug} / {@code .debug status} — краткий статус.</li>
 *   <li>{@code .debug on} / {@code .debug off} — включить/выключить.</li>
 *   <li>{@code .debug dump} — снять полный снимок ПРЯМО СЕЙЧАС (когда лагает).</li>
 *   <li>{@code .debug threshold <мс>} — порог срабатывания фриза.</li>
 *   <li>{@code .debug file} — путь к файлу журнала.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class DebugCommand implements Command {

   private static final DebugCommand INSTANCE = new DebugCommand();
   private static final List<String> ALIASES = List.of(".debug", ".dbg");

   private DebugCommand() {
   }

   public static DebugCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "Debug";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
      return ".debug <status|on|off|dump|file|threshold <мс>>";
   }

   @Override
   public String description() {
      return "Сторож зависаний: пишет причину фризов в файл";
   }

   @Override
   public void execute(CommandContext ctx, String args) throws CommandException {
      String sub = args == null ? "" : args.trim().toLowerCase();

      if (sub.isEmpty() || sub.equals("status")) {
         status(ctx);
         return;
      }

      if (sub.equals("on")) {
         FreezeWatchdog.start();
         ctx.sendSuccess("Сторож зависаний ВКЛючён. Диагностика пишется в фоне.");
         ctx.sendInfo("Файл: " + fileLine());
         return;
      }

      if (sub.equals("off")) {
         FreezeWatchdog.stop();
         ctx.sendSuccess("Сторож зависаний выключен.");
         return;
      }

      if (sub.equals("dump")) {
         if (!FreezeWatchdog.isRunning()) {
            FreezeWatchdog.start();
         }
         String path = FreezeWatchdog.dumpNow();
         ctx.sendSuccess("Снимок состояния записан в файл:");
         ctx.sendInfo(path);
         return;
      }

      if (sub.equals("file")) {
         ctx.sendInfo("Файл журнала: " + fileLine());
         return;
      }

      if (sub.startsWith("threshold")) {
         String[] parts = sub.split("\\s+");
         if (parts.length < 2) {
            ctx.sendError("Использование: .debug threshold <мс>");
            return;
         }
         try {
            long ms = Long.parseLong(parts[1]);
            FreezeWatchdog.setFreezeThresholdMs(ms);
            ctx.sendSuccess("Порог фриза = " + FreezeWatchdog.getFreezeThresholdMs() + " мс");
         } catch (NumberFormatException e) {
            ctx.sendError("Порог должен быть числом (мс): .debug threshold 300");
         }
         return;
      }

      ctx.sendInfo("Использование: " + usage());
   }

   private void status(CommandContext ctx) {
      ctx.sendSuccess(FreezeWatchdog.statusLine());
      ctx.sendInfo("Файл: " + fileLine());
      ctx.sendInfo("Подсказка: .debug dump — снять снимок сейчас; .debug threshold <мс> — порог.");
   }

   private String fileLine() {
      java.io.File f = FreezeWatchdog.getLogFile();
      return f == null ? "(появится при первом событии)" : f.getAbsolutePath();
   }
}
