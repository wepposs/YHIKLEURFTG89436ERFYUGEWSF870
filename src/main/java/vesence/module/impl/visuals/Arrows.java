package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import vesence.event.EventInit;
import vesence.event.render.EventScreen;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;
import vesence.utils.render.text.FontObject;

/**
 * Полный порт функции "Pointers" (Arrows) из клиента divine recode
 * (su.divine.modules.impl.visuals.Arrows), адаптированный под API vesence 1.21.
 *
 * Рисует треугольные указатели по кольцу вокруг центра экрана, направленные
 * на позиции игроков / друзей / голых / предметов.
 */
@IModule(name = "Pointers", description = "Рисует указатели на позиции разных существ", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Arrows extends Module {

   private static final ResourceLocation TRIANGLE = ResourceLocation.of("vesence", "textures/arrows/triangle.png");

   // Дистанция (блоки), с которой начинается затухание, и на которой достигается минимум.
   private static final float FADE_NEAR = 4.0F;
   private static final float FADE_FAR = 48.0F;
   private static final int MIN_ALPHA = 70;

   private final MultiBooleanSetting rendering = new MultiBooleanSetting(
         "Отображать",
         new BooleanSetting("Игроков", true),
         new BooleanSetting("Друзей", true),
         new BooleanSetting("Голых", false),
         new BooleanSetting("Предметы", false));
   private final HueSetting playerColor = new HueSetting("Цвет игроков", 0.0F, 0.0F, 1.0F)
         .hidden(() -> !(rendering.get("Игроков") || rendering.get("Голых")));
   private final HueSetting friendColor = new HueSetting("Цвет друзей", 35.333F, 1.0F, 1.0F)
         .hidden(() -> !rendering.get("Друзей"));
   private final HueSetting itemColor = new HueSetting("Цвет предметов", 0.0F, 1.0F, 1.0F)
         .hidden(() -> !rendering.get("Предметы"));
   private final BooleanSetting onlyIfNotVisible = new BooleanSetting("Показывать только если не видишь", false);
   private final SliderSetting radius = new SliderSetting("Радиус", 40.0, 30.0, 80.0, 5.0);
   private final SliderSetting size = new SliderSetting("Размер", 16.0, 8.0, 34.0, 1.0);
   private final BooleanSetting glow = new BooleanSetting("Свечение", true);
   private final BooleanSetting distanceFade = new BooleanSetting("Прозрачность по дистанции", true);
   private final BooleanSetting showDistance = new BooleanSetting("Показывать дистанцию", false);

   public Arrows() {
      this.addSettings(new Setting[] { rendering, playerColor, friendColor, itemColor,
            onlyIfNotVisible, radius, size, glow, distanceFade, showDistance });
   }

   @EventInit
   public void onRender(EventScreen event) {
      if (mc.player == null || mc.level == null) {
         return;
      }
      // divine recode: не рисуем указатели при открытом отладочном экране (F3)
      if (mc.getDebugHud().shouldShowDebugHud()) {
         return;
      }

      Renderer2D render = event.renderer();
      FontObject font = event.defaultFont();
      float scale = (float) mc.getWindow().getScaleFactor();
      float centerX = event.viewportWidth() / 2.0F;
      float centerY = event.viewportHeight() / 2.0F;

      double yawRad = Math.toRadians(mc.gameRenderer.getCamera().getYRot());
      double cos = Math.cos(yawRad);
      double sin = Math.sin(yawRad);
      double partialTicks = mc.gameRenderer.getCamera().getLastTickProgress();
      Vec3 camPos = mc.gameRenderer.getCamera().getCameraPos();

      double baseRadius = radius.get();

      for (Entity entity : mc.level.getEntities()) {
         if (!isValidEntity(entity)) {
            continue;
         }

         double adjustedRadius = (entity instanceof ItemEntity) ? Math.max(baseRadius - 20.0, 0.0) : baseRadius;

         double entityX = entity.lastX + (entity.getX() - entity.lastX) * partialTicks;
         double entityZ = entity.lastZ + (entity.getZ() - entity.lastZ) * partialTicks;
         double xWay = (entityX - camPos.x) * 0.01;
         double zWay = (entityZ - camPos.z) * 0.01;
         double rotationY = -(zWay * cos - xWay * sin);
         double rotationX = -(xWay * cos + zWay * sin);
         double angle = Math.toDegrees(Math.atan2(rotationY, rotationX));

         if (!isValidRotation(rotationX, rotationY, adjustedRadius)) {
            continue;
         }

         float ringRadius = (float) adjustedRadius * scale;
         float x = centerX + ringRadius * (float) Math.cos(Math.toRadians(angle));
         float y = centerY + ringRadius * (float) Math.sin(Math.toRadians(angle));
         float sizePx = (float) (double) size.get() * scale;

         int baseColor = determineEntityColor(entity);
         float distance = mc.player.distanceTo(entity);
         int alpha = computeAlpha(distance);
         int arrowColor = ColorUtil.replAlpha(baseColor, alpha);

         render.pushTranslation(x, y);
         render.pushRotation((float) angle);

         // Лёгкое свечение: пара увеличенных полупрозрачных копий под стрелкой.
         if (glow.get()) {
            int glowOuter = ColorUtil.replAlpha(baseColor, Math.round(alpha * 0.28F));
            render.drawImage(TRIANGLE, -sizePx * 0.85F, -sizePx * 0.85F, sizePx * 1.7F, sizePx * 1.7F, glowOuter);
            int glowInner = ColorUtil.replAlpha(baseColor, Math.round(alpha * 0.42F));
            render.drawImage(TRIANGLE, -sizePx * 0.68F, -sizePx * 0.68F, sizePx * 1.36F, sizePx * 1.36F, glowInner);
         }

         render.drawImage(TRIANGLE, -sizePx / 2.0F, -sizePx / 2.0F, sizePx, sizePx, arrowColor);

         // Дистанция — пишется под стрелкой и вращается вместе с ней (в её плоскости).
         if (showDistance.get()) {
            String label = Math.round(distance) + "m";
            float textSize = 11.0F * scale;
            float textY = sizePx / 2.0F + 2.5F * scale;
            int textColor = ColorUtil.replAlpha(0xFFFFFFFF, Math.max(alpha, 130));
            render.textCenter(font, 0.0F, textY, textSize, label, textColor);
         }

         render.popTransform();
         render.popTransform();
      }
   }

   /** Прозрачность в зависимости от дистанции: близко — плотно, далеко — прозрачно. */
   private int computeAlpha(float distance) {
      if (!distanceFade.get()) {
         return 255;
      }
      float t = (distance - FADE_NEAR) / (FADE_FAR - FADE_NEAR);
      if (t < 0.0F) t = 0.0F;
      if (t > 1.0F) t = 1.0F;
      return Math.round(255 - t * (255 - MIN_ALPHA));
   }

   private boolean isValidEntity(Entity entity) {
      boolean isPlayerOrItem = isPlayerTarget(entity) || isItemTarget(entity);
      if (isPlayerOrItem && entity.isAlive() && entity != mc.player) {
         return !onlyIfNotVisible.get() || !isEntityVisible(entity);
      }
      return false;
   }

   private boolean isPlayerTarget(Entity entity) {
      if (!(entity instanceof Player player) || entity == mc.player) {
         return false;
      }
      boolean isFriend = FriendStorage.isFriend(player.getNameForScoreboard());
      boolean isNaked = player.getArmor() == 0;
      if (isFriend) {
         return rendering.get("Друзей");
      }
      if (isNaked) {
         return rendering.get("Голых");
      }
      return rendering.get("Игроков");
   }

   private boolean isItemTarget(Entity entity) {
      return entity instanceof ItemEntity && rendering.get("Предметы");
   }

   private boolean isEntityVisible(Entity entity) {
      Vec3 start = mc.player.getEyePosition();
      Vec3 end = entity.getBoundingBox().getCenter();
      ClipContext context = new ClipContext(
            start, end, ClipContext.ShapeType.COLLIDER, ClipContext.FluidHandling.NONE, mc.player);
      return mc.level.raycast(context).getType() == HitResult.Type.MISS;
   }

   private int determineEntityColor(Entity entity) {
      if (entity instanceof Player player) {
         if (FriendStorage.isFriend(player.getNameForScoreboard())) {
            return friendColor.getRGB();
         }
         return playerColor.getRGB();
      } else if (entity instanceof ItemEntity) {
         return itemColor.getRGB();
      }
      return playerColor.getRGB();
   }

   private boolean isValidRotation(double rotationX, double rotationY, double radius) {
      double mrotY = -rotationY;
      double mrotX = -rotationX;
      return Math.sqrt(mrotX * mrotX + mrotY * mrotY) < radius;
   }
}
