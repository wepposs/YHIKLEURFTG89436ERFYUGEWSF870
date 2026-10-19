package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import vesence.utils.waypoint.Waypoint;
import vesence.utils.waypoint.WaypointManager;

import java.util.List;

@Environment(EnvType.CLIENT)
public final class WaypointCommand implements Command {

   private static final WaypointCommand INSTANCE = new WaypointCommand();
   private static final List<String> ALIASES = List.of(".way", ".waypoint", ".wp");

   private WaypointCommand() {
   }

   public static WaypointCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "Waypoint";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
      return ".way <add <name> [x y z]|remove <name>|list|clear>";
   }

   @Override
   public String description() {
      return "Управление мини-вейпоинтами в мире";
   }

   @Override
   public void execute(CommandContext ctx, String args) throws CommandException {
      WaypointManager manager = WaypointManager.getInstance();

      if (args == null || args.trim().isEmpty()) {
         ctx.sendInfo("Использование: " + usage());
         return;
      }

      String[] parts = args.trim().split("\\s+");
      String sub = parts[0].toLowerCase();

      switch (sub) {
         case "add": {
            if (parts.length < 2) {
               ctx.sendError("Использование: .way add <name> [x y z]");
               return;
            }
            String name = parts[1];

            double x;
            double y;
            double z;
            if (parts.length >= 5) {
               try {
                  x = Double.parseDouble(parts[2]);
                  y = Double.parseDouble(parts[3]);
                  z = Double.parseDouble(parts[4]);
               } catch (NumberFormatException e) {
                  ctx.sendError("Координаты должны быть числами: .way add <name> <x> <y> <z>");
                  return;
               }
            } else {
               MinecraftClient mc = ctx.client();
               if (mc.player == null) {
                  ctx.sendError("Игрок не найден — укажи координаты: .way add <name> <x> <y> <z>");
                  return;
               }
               BlockPos pos = mc.player.getBlockPos();
               x = pos.getX();
               y = pos.getY();
               z = pos.getZ();
            }

            manager.add(name, x, y, z);
            ctx.sendSuccess("Вейпоинт §f" + name + "§a добавлен на §f"
                  + (int) Math.floor(x) + ", " + (int) Math.floor(y) + ", " + (int) Math.floor(z));
            break;
         }
         case "remove":
         case "rem":
         case "del":
         case "delete": {
            if (parts.length < 2) {
               ctx.sendError("Использование: .way remove <name>");
               return;
            }
            String name = parts[1];
            if (manager.remove(name)) {
               ctx.sendSuccess("Вейпоинт §f" + name + "§a удалён.");
            } else {
               ctx.sendError("Вейпоинт §f" + name + "§c не найден.");
            }
            break;
         }
         case "list":
         case "ls": {
            List<Waypoint> all = manager.getWaypoints();
            if (all.isEmpty()) {
               ctx.sendInfo("Вейпоинтов нет. Добавь: .way add <name> [x y z]");
               return;
            }
            ctx.sendSuccess("Вейпоинты (" + all.size() + "):");
            for (Waypoint w : all) {
               ctx.sendInfo("§f" + w.name + " §7| §f"
                     + (int) Math.floor(w.x) + ", " + (int) Math.floor(w.y) + ", " + (int) Math.floor(w.z));
            }
            break;
         }
         case "clear": {
            int count = manager.clear();
            ctx.sendSuccess("Удалено вейпоинтов: §f" + count);
            break;
         }
         default:
            ctx.sendInfo("Использование: " + usage());
            break;
      }
   }
}
