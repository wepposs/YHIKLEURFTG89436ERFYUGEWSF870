package vesence.utils.waypoint;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.render.EventScreenPre;
import vesence.module.impl.misc.ClickGui;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.other.Mathf;
import vesence.utils.render.BorderRadius;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.AnimatedText;
import vesence.utils.render.text.FontObject;
import vesence.utils.render.text.FontRegistry;
import vesence.utils.render.text.Font;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Environment(EnvType.CLIENT)
public final class WaypointManager {

   private static final WaypointManager INSTANCE = new WaypointManager();

   public static WaypointManager getInstance() {
      return INSTANCE;
   }

   private static final float FONT_SIZE = 26;
   private static final float INFO_FONT_SIZE = 21;

   private static final double BASE_WALK_SPEED_PER_SEC = 4.317;
   private static final double DEFAULT_SPEED_ATTR = 0.1;

   private final Minecraft mc = Minecraft.getInstance();
   private final CopyOnWriteArrayList<Waypoint> waypoints = new CopyOnWriteArrayList<>();
   private boolean loaded = false;

   private WaypointManager() {
   }

   public List<Waypoint> getWaypoints() {
      return waypoints;
   }

   public Waypoint find(String name) {
      for (Waypoint w : waypoints) {
         if (w.name.equalsIgnoreCase(name)) return w;
      }
      return null;
   }

   public boolean add(String name, double x, double y, double z) {
      Waypoint existing = find(name);
      if (existing != null) {
         existing.x = x;
         existing.y = y;
         existing.z = z;
      } else {
         waypoints.add(new Waypoint(name, x, y, z));
      }
      save();
      return true;
   }

   public boolean remove(String name) {
      Waypoint w = find(name);
      if (w == null) return false;
      waypoints.remove(w);
      save();
      return true;
   }

   public int clear() {
      int count = waypoints.size();
      waypoints.clear();
      save();
      return count;
   }

   private File getFile() {
      File root = Vesence.get != null ? Vesence.get.root : new File("vesence");
      return new File(root, "waypoints.json");
   }

   public synchronized void load() {
      if (loaded) return;
      loaded = true;
      waypoints.clear();
      File file = getFile();
      if (!file.exists()) return;
      try (FileReader reader = new FileReader(file)) {
         JsonElement parsed = JsonParser.parseReader(reader);
         if (!parsed.isJsonArray()) return;
         JsonArray array = parsed.getAsJsonArray();
         for (JsonElement element : array) {
            try {
               JsonObject obj = element.getAsJsonObject();
               String name = obj.get("name").getAsString();
               double x = obj.get("x").getAsDouble();
               double y = obj.get("y").getAsDouble();
               double z = obj.get("z").getAsDouble();
               waypoints.add(new Waypoint(name, x, y, z));
            } catch (Exception ignored) {
            }
         }
      } catch (Exception ignored) {
      }
   }

   public synchronized void save() {
      File file = getFile();
      try {
         if (file.getParentFile() != null) file.getParentFile().mkdirs();
      } catch (Exception ignored) {
      }
      JsonArray array = new JsonArray();
      for (Waypoint w : waypoints) {
         JsonObject obj = new JsonObject();
         obj.addProperty("name", w.name);
         obj.addProperty("x", w.x);
         obj.addProperty("y", w.y);
         obj.addProperty("z", w.z);
         array.add(obj);
      }
      try (FileWriter writer = new FileWriter(file)) {
         new GsonBuilder().setPrettyPrinting().create().toJson(array, writer);
      } catch (Exception ignored) {
      }
   }

   private static String pluralRu(long n, String one, String few, String many) {
      long mod100 = Math.abs(n) % 100;
      long mod10 = Math.abs(n) % 10;
      if (mod100 >= 11 && mod100 <= 14) return many;
      if (mod10 == 1) return one;
      if (mod10 >= 2 && mod10 <= 4) return few;
      return many;
   }

   private static String formatEta(int totalSeconds) {
      if (totalSeconds < 0) totalSeconds = 0;
      int hours = totalSeconds / 3600;
      int minutes = (totalSeconds % 3600) / 60;
      int seconds = totalSeconds % 60;

      StringBuilder sb = new StringBuilder();
      if (hours > 0) {
         sb.append(hours).append(' ').append(pluralRu(hours, "час", "часа", "часов")).append(' ');
      }
      if (minutes > 0) {
         sb.append(minutes).append(' ').append(pluralRu(minutes, "минута", "минуты", "минут")).append(' ');
      }
      sb.append(seconds).append(' ').append(pluralRu(seconds, "секунда", "секунды", "секунд"));
      return sb.toString();
   }

   @EventInit
   private void onRender(EventScreenPre event) {
      if (mc.level == null || mc.player == null) return;
      if (waypoints.isEmpty()) return;

      Renderer2D renderer = event.renderer();
      FontObject font = FontRegistry.MONTSERRAT;
      float scale = (float) Mathf.getScaleFactor();

      Camera camera = mc.gameRenderer.mainCamera();
      Vec3 camPos = camera.position();

      double yawRad = Math.toRadians(camera.yRot());
      double pitchRad = Math.toRadians(camera.xRot());
      double cosPitch = Math.cos(pitchRad);
      double fx = -Math.sin(yawRad) * cosPitch;
      double fy = -Math.sin(pitchRad);
      double fz = Math.cos(yawRad) * cosPitch;

      double speedPerSec = BASE_WALK_SPEED_PER_SEC;
      try {
         double attr = mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
         if (attr > 1.0E-4) {
            speedPerSec = BASE_WALK_SPEED_PER_SEC * (attr / DEFAULT_SPEED_ATTR);
         }
      } catch (Exception ignored) {
      }
      if (speedPerSec < 0.5) speedPerSec = 0.5;

      Vec3 playerPos = mc.player.position();

      for (Waypoint w : waypoints) {
         Vec3 worldPos = new Vec3(w.x + 0.5, w.y + 0.5, w.z + 0.5);

         double dx = worldPos.x - camPos.x;
         double dy = worldPos.y - camPos.y;
         double dz = worldPos.z - camPos.z;
         double dot = dx * fx + dy * fy + dz * fz;
         if (dot <= 0.05) continue;

         Vec3 screen = Mathf.worldSpaceToScreenSpace(worldPos);
         if (!Float.isFinite((float) screen.x) || !Float.isFinite((float) screen.y)) continue;

         double pdx = worldPos.x - playerPos.x;
         double pdy = worldPos.y - playerPos.y;
         double pdz = worldPos.z - playerPos.z;
         double distance = Math.sqrt(pdx * pdx + pdy * pdy + pdz * pdz);
         double horizDist = Math.sqrt(pdx * pdx + pdz * pdz);
         int etaSeconds = (int) Math.round(horizDist / speedPerSec);

         String name = w.name;
         long distBlocks = Math.round(distance);
         String info = distBlocks + " " + pluralRu(distBlocks, "блок", "блока", "блоков")
               + " | " + formatEta(etaSeconds);

         float sx = (float) screen.x * scale;
         float sy = (float) screen.y * scale;

         Font.TextMetrics nameM = renderer.measureText(font, name, FONT_SIZE);
         Font.TextMetrics infoM = renderer.measureText(font, info, INFO_FONT_SIZE);

         float padH = 10;
         float padV = 8;
         float lineGap = 2f;
         float contentW = Math.max(nameM.width, infoM.width) + 20;
         float contentH = nameM.height + lineGap + infoM.height;
         float tagW = contentW + padH * 2f;
         float tagH = contentH + padV * 2f;
         float tagX = sx - tagW / 2f;
         float tagY = sy - tagH / 2f;

         renderer.blur(tagX, tagY, tagW, tagH, 5, 8, 1);
         renderer.gradient(tagX, tagY, tagW, tagH, 8,
                 ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.10f), 0.7f),
                 ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.10f), 0.7f),
                 ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.05f), 0.7f),
                 ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 0.05f), 0.7f), true);
         renderer.gradientOutline(tagX, tagY, tagW, tagH, 8,
                 ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 1), 0.8f),
                 ColorUtil.multAlpha(ColorUtil.multDark(Renderer2D.ColorUtil.getClientColor(), 1), 0.8f),
                 ColorUtil.multAlpha(ColorUtil.BLACK, 0),
                 ColorUtil.multAlpha(ColorUtil.BLACK, 0), 1.5f, true);

         float nameBaseline = tagY + padV + nameM.height - 2f;
         renderer.textCenter(font, tagX + tagW / 2f - 10, nameBaseline,
               FONT_SIZE, name, ColorUtil.getColor(255, 255, 255, 235));
         renderer.textCenter(FontRegistry.VESENCE, tagX + tagW / 2f + (nameM.width / 2f), nameBaseline - 0.5f,
                 30, "M", ColorUtil.replAlpha(Renderer2D.ColorUtil.getClientColor(), 255));
         float infoBaseline = tagY + padV + nameM.height + lineGap + infoM.height - 2f;
         AnimatedText.draw(renderer, font, "wpinfo_" + w.name, info,
               sx, infoBaseline, INFO_FONT_SIZE,
               ColorUtil.getColor(255, 255, 255, 95), AnimatedText.ALIGN_CENTER,
               AnimatedText.WAYPOINT_MIN_ALPHA);
      }
   }
}
