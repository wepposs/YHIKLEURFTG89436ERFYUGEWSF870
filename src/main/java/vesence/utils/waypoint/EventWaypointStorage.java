package vesence.utils.waypoint;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.render.EventScreenPre;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.Mathf;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.Font;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище и рендер ивент-меток (порт EventWaypointStorage из RelevantPremiumpp4).
 *
 * <p>Ивент-метки эфемерны: TTL 5 минут и очистка при выходе с сервера
 * (см. {@link vesence.module.impl.misc.EventParser}), поэтому хранятся только
 * в памяти — сохранять их на диск смысла нет (они бы всё равно протухли).
 *
 * <p>Рендер повторяет донора: компактный тёмный прямоугольник + иконка типа
 * ивента + название + редкость (цвет по редкости) + дистанция. Проекция
 * мира в экран — через {@link Mathf#worldSpaceToScreenSpace(Vec3)} (как у
 * обычных вейпоинтов vesence).
 */
@Environment(EnvType.CLIENT)
public final class EventWaypointStorage {

   private static final EventWaypointStorage INSTANCE = new EventWaypointStorage();

   public static EventWaypointStorage getInstance() {
      return INSTANCE;
   }

   private static final long EVENT_TTL_MS = 300000L;

   private static final ResourceLocation WAYPOINT_ICON = ResourceLocation.of("vesence", "textures/events/waypoint.png");
   private static final ResourceLocation ICON_MAYAK = ResourceLocation.of("vesence", "textures/events/may.png");
   private static final ResourceLocation ICON_VULKAN = ResourceLocation.of("vesence", "textures/events/vul.png");
   private static final ResourceLocation ICON_METEOR = ResourceLocation.of("vesence", "textures/events/met.png");
   private static final ResourceLocation ICON_GEYSER = ResourceLocation.of("vesence", "textures/events/gez.png");

   private final Minecraft mc = Minecraft.getInstance();
   private final Map<String, Entry> waypoints = new ConcurrentHashMap<>();

   private EventWaypointStorage() {
   }

   public void addEventWaypoint(String name, String rarity, int x, int y, int z) {
      this.addEventWaypointInternal(name, rarity, x, y, z, true);
   }

   /** То же, что {@link #addEventWaypoint}, но без сообщения в чат (авто-добавление парсером). */
   public void addEventWaypointSilent(String name, String rarity, int x, int y, int z) {
      this.addEventWaypointInternal(name, rarity, x, y, z, false);
   }

   private void addEventWaypointInternal(String name, String rarity, int x, int y, int z, boolean notify) {
      this.clearEventWaypoints();
      this.waypoints.put(name, new Entry(x, y, z, rarity == null ? "" : rarity, System.currentTimeMillis()));
      if (notify && mc.player != null) {
         mc.player.sendMessage(net.minecraft.text.Component.literal(
               "[EventParser] " + name + (rarity != null && !rarity.isEmpty() ? " [" + rarity + "]" : "")
                     + " -> " + x + " " + y + " " + z), false);
      }
   }

   public void clearEventWaypoints() {
      this.waypoints.clear();
   }

   public void clearAll() {
      this.waypoints.clear();
   }

   public boolean isEmpty() {
      return this.waypoints.isEmpty();
   }

   public int size() {
      return this.waypoints.size();
   }

   public Map<String, Entry> getWaypoints() {
      return this.waypoints;
   }

   /** Удаляет протухшие (старше 5 минут) ивент-метки. */
   public void tickEventWaypoints() {
      long now = System.currentTimeMillis();
      boolean changed = false;
      Iterator<Map.Entry<String, Entry>> iterator = this.waypoints.entrySet().iterator();
      while (iterator.hasNext()) {
         Map.Entry<String, Entry> entry = iterator.next();
         if (now - entry.getValue().createdAt > EVENT_TTL_MS) {
            iterator.remove();
            changed = true;
         }
      }
      // changed зарезервировано под будущую персистентность; ивент-метки in-memory.
   }

   @EventInit
   public void onRender(EventScreenPre event) {
      if (mc.player == null || mc.level == null || this.waypoints.isEmpty()) {
         return;
      }

      Renderer2D renderer = event.renderer();
      FontObject titleFont = FontRegistry.SF_MEDIUM;
      FontObject smallFont = FontRegistry.SF_MEDIUM;
      if (titleFont == null || smallFont == null) {
         return;
      }

      float scale = (float) Mathf.getScaleFactor();
      Camera camera = mc.gameRenderer.getCamera();
      Vec3 camPos = camera.getCameraPos();

      double yawRad = Math.toRadians(camera.getYRot());
      double pitchRad = Math.toRadians(camera.getXRot());
      double cosPitch = Math.cos(pitchRad);
      double fx = -Math.sin(yawRad) * cosPitch;
      double fy = -Math.sin(pitchRad);
      double fz = Math.cos(yawRad) * cosPitch;

      int themeColor = Renderer2D.ColorUtil.getClientColor();

      for (Map.Entry<String, Entry> mapEntry : this.waypoints.entrySet()) {
         Entry waypoint = mapEntry.getValue();
         Vec3 worldPos = new Vec3(waypoint.x + 0.5, waypoint.y + 1.5, waypoint.z + 0.5);

         double dx = worldPos.x - camPos.x;
         double dy = worldPos.y - camPos.y;
         double dz = worldPos.z - camPos.z;
         double dot = dx * fx + dy * fy + dz * fz;
         if (dot <= 0.05) {
            continue;
         }

         Vec3 screen = Mathf.worldSpaceToScreenSpace(worldPos);
         if (screen == null || !Float.isFinite((float) screen.x) || !Float.isFinite((float) screen.y)) {
            continue;
         }

         int distance = (int) camPos.distanceTo(new Vec3(waypoint.x, waypoint.y, waypoint.z));
         float cx = (float) screen.x * scale;
         float cy = (float) screen.y * scale;

         this.renderEventWaypoint(renderer, titleFont, smallFont, cx, cy,
               mapEntry.getKey(), waypoint.rarity, distance + "m", themeColor);
      }
   }

   private void renderEventWaypoint(Renderer2D renderer, FontObject titleFont, FontObject smallFont,
                                    float cx, float cy, String name, String rarity, String distText, int themeColor) {
      float titleSize = 22.0F;
      float smallSize = 16.0F;
      float iconSize = 34.0F;
      float bgH = 24.0F;
      float textPad = 6.0F;
      float distPad = 10.0F;
      float rightPad = 10.0F;
      float lineGap = 2.0F;

      boolean hasRarity = rarity != null && !rarity.isEmpty();

      Font.TextMetrics nameM = renderer.measureText(titleFont, name, titleSize);
      Font.TextMetrics rarM = hasRarity ? renderer.measureText(smallFont, rarity, smallSize) : null;
      Font.TextMetrics distM = renderer.measureText(smallFont, distText, smallSize);

      float nameW = nameM.width;
      float rarW = hasRarity ? rarM.width : 0.0F;
      float distW = distM.width;
      float textBlockW = Math.max(nameW, rarW);

      float bgW = iconSize + textPad + textBlockW + distPad + distW + rightPad;
      float bgX = cx - bgW / 2.0F;
      float bgY = cy - bgH / 2.0F;

      // Фон + тонкая обводка градиентом цвета темы (как в .way, но тоньше)
      renderer.rect(bgX, bgY, bgW, bgH, 5.0F, ColorUtil.getColor(12, 12, 12, 190));
      renderer.gradientOutline(bgX, bgY, bgW, bgH, 5.0F,
            ColorUtil.multAlpha(ColorUtil.multDark(themeColor, 1.0F), 0.85F),
            ColorUtil.multAlpha(ColorUtil.multDark(themeColor, 1.0F), 0.85F),
            ColorUtil.multAlpha(ColorUtil.BLACK, 0.0F),
            ColorUtil.multAlpha(ColorUtil.BLACK, 0.0F), 1.0F, true);

      float iconOverhang = (iconSize - bgH) / 2.0F;
      renderer.drawImage(this.getEventIcon(name), bgX, bgY - iconOverhang, iconSize, iconSize, themeColor);

      float textX = bgX + iconSize + textPad;
      int white = ColorUtil.getColor(255, 255, 255, 255);

      if (hasRarity) {
         float blockHeight = nameM.height + lineGap + rarM.height;
         float blockTop = bgY + (bgH - blockHeight) / 2.0F;
         renderer.text(titleFont, textX, blockTop + nameM.height, titleSize, name, white);
         renderer.text(smallFont, textX, blockTop + nameM.height + lineGap + rarM.height, smallSize,
               rarity, this.getRarityColor(rarity, themeColor));
      } else {
         renderer.text(titleFont, textX, bgY + (bgH + nameM.height) / 2.0F, titleSize, name, white);
      }

      // Дистанция — цвет темы, прижата вправо
      renderer.text(smallFont, bgX + bgW - rightPad - distW, bgY + (bgH + distM.height) / 2.0F, smallSize, distText, themeColor);
   }

   private ResourceLocation getEventIcon(String name) {
      String lower = name.toLowerCase();
      if (lower.contains("маяк")) {
         return ICON_MAYAK;
      } else if (lower.contains("вулкан")) {
         return ICON_VULKAN;
      } else if (lower.contains("метеор")) {
         return ICON_METEOR;
      } else {
         return lower.contains("гейзер") ? ICON_GEYSER : WAYPOINT_ICON;
      }
   }

   private int getRarityColor(String rarity, int fallback) {
      String lower = rarity.toLowerCase();
      if (lower.contains("легенд")) {
         return ColorUtil.getColor(255, 165, 0, 255);
      } else if (lower.contains("элит")) {
         return ColorUtil.getColor(255, 100, 0, 255);
      } else if (lower.contains("эпич")) {
         return ColorUtil.getColor(163, 53, 238, 255);
      } else if (lower.contains("редк")) {
         return ColorUtil.getColor(0, 112, 221, 255);
      } else if (lower.contains("необыч") || lower.contains("солид")) {
         return ColorUtil.getColor(30, 200, 30, 255);
      } else {
         return lower.contains("обычн") ? ColorUtil.getColor(160, 160, 160, 255) : fallback;
      }
   }

   public static final class Entry {
      private final int x;
      private final int y;
      private final int z;
      private final String rarity;
      private final long createdAt;

      public Entry(int x, int y, int z, String rarity, long createdAt) {
         this.x = x;
         this.y = y;
         this.z = z;
         this.rarity = rarity;
         this.createdAt = createdAt;
      }

      public int getX() {
         return this.x;
      }

      public int getY() {
         return this.y;
      }

      public int getZ() {
         return this.z;
      }

      public String getRarity() {
         return this.rarity;
      }

      public long getCreatedAt() {
         return this.createdAt;
      }
   }
}
