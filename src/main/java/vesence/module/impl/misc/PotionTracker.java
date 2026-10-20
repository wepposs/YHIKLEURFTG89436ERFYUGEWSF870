package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.phys.AABB;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.utils.render.ColorUtil;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@IModule(name = "PotionTracker", description = "Показывает попадание выкинутых зелий по игрокам", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class PotionTracker extends Module {

   private static final double TRACK_RADIUS = 50.0;
   private static final double SPLASH_RADIUS = 4.0;
   private static final double SPLASH_HEIGHT = 2.0;
   private static final int MAX_MESSAGES = 4;
   private static final int GRAY = new Color(200, 200, 200).getRGB();
   private static final int PLAYER = new Color(235, 235, 235).getRGB();

   private final Map<Integer, PotionData> trackedPotions = new HashMap<>();
   private ClientLevel lastWorld;

   public PotionTracker() {
   }

   @Override
   public void onDisable() {
      this.trackedPotions.clear();
      this.lastWorld = null;
      super.onDisable();
   }

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.level == null) {
         this.trackedPotions.clear();
         this.lastWorld = null;
         return;
      }

      if (this.lastWorld != mc.level) {
         this.trackedPotions.clear();
         this.lastWorld = mc.level;
      }

      Set<Integer> currentPotions = new HashSet<>();

      double trackRadiusSq = TRACK_RADIUS * TRACK_RADIUS;
      AABB searchBox = mc.player.getBoundingBox().inflate(TRACK_RADIUS);
      for (AbstractThrownPotion potionEntity : mc.level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(AbstractThrownPotion.class), searchBox, Entity::isAlive)) {
         if (mc.player.distanceToSqr(potionEntity) > trackRadiusSq) {
            continue;
         }

         PotionInfo potionInfo = this.getPotionInfo(potionEntity);
         if (potionInfo == null) {
            continue;
         }

         int entityId = potionEntity.getId();
         currentPotions.add(entityId);

         PotionData data = this.trackedPotions.get(entityId);
         if (data == null) {
            this.trackedPotions.put(entityId, new PotionData(
               potionInfo,
               potionEntity.getX(),
               potionEntity.getY(),
               potionEntity.getZ()
            ));
            continue;
         }

         data.lastX = potionEntity.getX();
         data.lastY = potionEntity.getY();
         data.lastZ = potionEntity.getZ();
         data.potionInfo = potionInfo;
      }

      Set<Integer> removedPotions = new HashSet<>(this.trackedPotions.keySet());
      removedPotions.removeAll(currentPotions);

      for (int entityId : removedPotions) {
         PotionData data = this.trackedPotions.remove(entityId);
         if (data != null) {
            this.printSplash(data);
         }
      }
   }

   private void printSplash(PotionData data) {
      AABB potionBox = new AABB(
         data.lastX - SPLASH_RADIUS,
         data.lastY - SPLASH_HEIGHT,
         data.lastZ - SPLASH_RADIUS,
         data.lastX + SPLASH_RADIUS,
         data.lastY + SPLASH_HEIGHT,
         data.lastZ + SPLASH_RADIUS
      );

      List<PlayerHit> hits = new ArrayList<>();

      for (Player player : mc.level.players()) {
         if (player == null || !player.isAlive()) {
            continue;
         }
         if (!potionBox.contains(player.position())) {
            continue;
         }

         double dx = player.getX() - data.lastX;
         double dz = player.getZ() - data.lastZ;
         double distance = Math.sqrt(dx * dx + dz * dz);
         if (distance > SPLASH_RADIUS) {
            continue;
         }

         double proximity = Math.max(0.0, 1.0 - distance / SPLASH_RADIUS);
         int percent = Math.max(1, Math.min(100, (int) Math.round(proximity * 100.0)));

         hits.add(new PlayerHit(player.getName().getString(), percent, distance));
      }

      hits.sort(Comparator.comparingDouble(PlayerHit::distance));

      for (int i = 0; i < Math.min(MAX_MESSAGES, hits.size()); i++) {
         PlayerHit hit = hits.get(i);
         this.sendPotionMessage(hit.playerName(), data.potionInfo, hit.percent());
      }
   }

   private PotionInfo getPotionInfo(AbstractThrownPotion potionEntity) {
      PotionContents contents = potionEntity.getItem().get(DataComponents.POTION_CONTENTS);

      PotionInfo byEffects = this.getPotionInfo(contents);
      if (byEffects != null) {
         return byEffects;
      }

      return this.getPotionInfo(potionEntity.getItem().getHoverName().getString());
   }

   private PotionInfo getPotionInfo(PotionContents contents) {
      if (contents == null || !contents.hasEffects()) {
         return null;
      }

      boolean regenerationTwo = this.hasEffect(contents, MobEffects.REGENERATION, 1);
      boolean strengthFive = this.hasEffect(contents, MobEffects.STRENGTH, 4);
      boolean healthBoostThree = this.hasEffect(contents, MobEffects.HEALTH_BOOST, 2);
      boolean strengthFour = this.hasEffect(contents, MobEffects.STRENGTH, 3);
      boolean speedThree = this.hasEffect(contents, MobEffects.SPEED, 2);

      if (regenerationTwo) {
         return PotionInfo.HOLY_WATER;
      }
      if (strengthFive) {
         return PotionInfo.WRATH;
      }
      if (healthBoostThree) {
         return PotionInfo.PALADIN;
      }
      if (strengthFour && speedThree) {
         return PotionInfo.ASSASSIN;
      }
      if (strengthFour) {
         return PotionInfo.ASSASSIN;
      }

      return null;
   }

   private boolean hasEffect(PotionContents contents, Holder<MobEffect> effect, int amplifier) {
      for (MobEffectInstance instance : contents.getAllEffects()) {
         if (instance.getEffect().equals(effect) && instance.getAmplifier() == amplifier) {
            return true;
         }
      }
      return false;
   }

   private PotionInfo getPotionInfo(String itemName) {
      String normalizedName = this.normalize(itemName);

      for (PotionInfo potionInfo : PotionInfo.values()) {
         if (normalizedName.contains(this.normalize(potionInfo.plainName()))) {
            return potionInfo;
         }
      }

      return null;
   }

   private String normalize(String text) {
      return text
         .replaceAll("§.", "")
         .replace("[", "")
         .replace("]", "")
         .replace("✦", "")
         .toLowerCase(Locale.ROOT)
         .trim();
   }

   private void sendPotionMessage(String playerName, PotionInfo potionInfo, int percent) {
      if (mc.player == null) {
         return;
      }

      int themeStart = ColorUtil.fade(0);
      int themeEnd = ColorUtil.fade(90);

      MutableComponent text = Component.literal("");
      text.append(this.gradientText("vesence", themeStart, themeEnd, true));
      text.append(Component.literal(" ⇒ ").setStyle(this.grayStyle()));
      text.append(Component.literal(playerName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(PLAYER))));
      text.append(Component.literal(" получил ").setStyle(this.grayStyle()));
      text.append(this.gradientText(potionInfo.displayName, potionInfo.startColor, potionInfo.endColor, true));
      text.append(Component.literal(" " + percent + "%").setStyle(this.grayStyle()));

      mc.player.sendSystemMessage(text);
   }

   private MutableComponent gradientText(String text, int startColor, int endColor, boolean bold) {
      MutableComponent result = Component.literal("");

      for (int i = 0; i < text.length(); i++) {
         float progress = text.length() <= 1 ? 0.0F : (float) i / (text.length() - 1);
         int color = ColorUtil.interpolate(startColor, endColor, progress);
         result.append(Component.literal(String.valueOf(text.charAt(i)))
            .setStyle(Style.EMPTY
               .withBold(bold)
               .withColor(TextColor.fromRgb(color & 0xFFFFFF))));
      }

      return result;
   }

   private Style grayStyle() {
      return Style.EMPTY.withColor(TextColor.fromRgb(GRAY & 0xFFFFFF));
   }

   @Environment(EnvType.CLIENT)
   private enum PotionInfo {
      HOLY_WATER("[✦] Святая вода", 0xFFF56B, 0xB8FF42),
      WRATH("[✦] Зелье Гнева", 0xC41212, 0xFFB13B),
      PALADIN("[✦] Зелье Паладина", 0xB8FF42, 0xFFF0A0),
      ASSASSIN("[✦] Зелье Ассасина", 0x555555, 0xB02A2A);

      private final String displayName;
      private final int startColor;
      private final int endColor;

      PotionInfo(String displayName, int startColor, int endColor) {
         this.displayName = displayName;
         this.startColor = startColor;
         this.endColor = endColor;
      }

      private String plainName() {
         int index = this.displayName.indexOf("] ");
         return index >= 0 ? this.displayName.substring(index + 2) : this.displayName;
      }
   }

   @Environment(EnvType.CLIENT)
   private static class PotionData {
      private PotionInfo potionInfo;
      private double lastX;
      private double lastY;
      private double lastZ;

      private PotionData(PotionInfo potionInfo, double lastX, double lastY, double lastZ) {
         this.potionInfo = potionInfo;
         this.lastX = lastX;
         this.lastY = lastY;
         this.lastZ = lastZ;
      }
   }

   @Environment(EnvType.CLIENT)
   private record PlayerHit(String playerName, int percent, double distance) {
   }
}
