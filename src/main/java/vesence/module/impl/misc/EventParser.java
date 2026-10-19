package vesence.module.impl.misc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.utils.waypoint.EventWaypointStorage;

/**
 * Порт EventParser из RelevantPremiumpp4.
 *
 * <p>Парсит серверные сообщения об ивентах ("Ивент ... Координаты: [x y z]")
 * и ставит ивент-метку через {@link EventWaypointStorage}. При заходе на
 * сервер шлёт команду {@code event delay} (через 5 тиков после смены мира),
 * а при выходе — очищает ивент-метки. Раз в 20 тиков чистит протухшие метки.
 */
@IModule(name = "EventParser", description = "Авто-метка ивента при входе на сервер", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class EventParser extends Module {

   private static final Pattern P_NAME = Pattern.compile("\\[(\\d+)\\]\\s+([^\\[\\\\\\n]+?):\\s*(?:\\\\n|\\n|$)");
   private static final Pattern P_RARITY = Pattern.compile("Уровень лута:\\s*([^\\\\\\n|\\[]+)");
   private static final Pattern P_COORDS = Pattern.compile("Координаты:\\s*\\[(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\]");

   private boolean wasConnected = false;
   private int ttlTick = 0;
   private int lastWorldId = 0;
   private int delayTicks = 0;
   private boolean pendingCmd = false;

   @EventInit
   public void onUpdate(EventUpdate event) {
      boolean connected = mc.player != null && mc.world != null;

      if (this.wasConnected && !connected) {
         EventWaypointStorage.getInstance().clearEventWaypoints();
         this.wasConnected = false;
         this.lastWorldId = 0;
         return;
      }

      this.wasConnected = connected;
      if (!connected) {
         return;
      }

      if (++this.ttlTick >= 20) {
         this.ttlTick = 0;
         EventWaypointStorage.getInstance().tickEventWaypoints();
      }

      int worldId = System.identityHashCode(mc.world);
      if (worldId != this.lastWorldId) {
         this.lastWorldId = worldId;
         this.delayTicks = 5;
         this.pendingCmd = true;
      }

      if (this.pendingCmd && --this.delayTicks <= 0) {
         this.pendingCmd = false;
         if (mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendChatCommand("event delay");
         }
      }
   }

   @EventInit
   public void onPacket(EventPacket event) {
      if (event.getType() != EventPacket.Type.RECEIVE) {
         return;
      }

      if (!(event.getPacket() instanceof GameMessageS2CPacket packet)) {
         return;
      }

      String raw = packet.content().getString();
      if (raw == null || !raw.contains("Ивент")) {
         return;
      }

      // Один Matcher вместо двух (раньше создавался отдельный для проверки .find()
      // и ещё один для цикла — лишняя аллокация + повторный проход regex).
      Matcher coordMatcher = P_COORDS.matcher(raw);
      if (!coordMatcher.find()) {
         return;
      }

      do {
         int coordPos = coordMatcher.start();
         int x = Integer.parseInt(coordMatcher.group(1));
         int y = Integer.parseInt(coordMatcher.group(2));
         int z = Integer.parseInt(coordMatcher.group(3));
         String before = raw.substring(0, coordPos);
         String name = null;
         Matcher nameMatcher = P_NAME.matcher(before);

         while (nameMatcher.find()) {
            String candidate = nameMatcher.group(2).trim();
            if (!candidate.contains("До следующего") && !candidate.isEmpty()) {
               name = candidate;
            }
         }

         if (name != null) {
            String rarity = "";
            Matcher rarityMatcher = P_RARITY.matcher(before);

            while (rarityMatcher.find()) {
               rarity = rarityMatcher.group(1).trim();
            }

            EventWaypointStorage.getInstance().addEventWaypointSilent(name, rarity, x, y, z);
         }
      } while (coordMatcher.find());
   }
}
