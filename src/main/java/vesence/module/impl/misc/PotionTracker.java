package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.Box;
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
   private ClientWorld lastWorld;

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
      if (mc.player == null || mc.world == null) {
         this.trackedPotions.clear();
         this.lastWorld = null;
         return;
      }

      if (this.lastWorld != mc.world) {
         this.trackedPotions.clear();
         this.lastWorld = mc.world;
      }

      Set<Integer> currentPotions = new HashSet<>();

      double trackRadiusSq = TRACK_RADIUS * TRACK_RADIUS;
      Box searchBox = mc.player.getBoundingBox().expand(TRACK_RADIUS);
      for (PotionEntity potionEntity : mc.world.getEntitiesByClass(PotionEntity.class, searchBox, Entity::isAlive)) {
         if (mc.player.squaredDistanceTo(potionEntity) > trackRadiusSq) {
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
      Box potionBox = new Box(
         data.lastX - SPLASH_RADIUS,
         data.lastY - SPLASH_HEIGHT,
         data.lastZ - SPLASH_RADIUS,
         data.lastX + SPLASH_RADIUS,
         data.lastY + SPLASH_HEIGHT,
         data.lastZ + SPLASH_RADIUS
      );

      List<PlayerHit> hits = new ArrayList<>();

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player == null || !player.isAlive()) {
            continue;
         }
         if (!potionBox.contains(player.getEntityPos())) {
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

   private PotionInfo getPotionInfo(PotionEntity potionEntity) {
      PotionContentsComponent contents = potionEntity.getStack().get(DataComponentTypes.POTION_CONTENTS);

      PotionInfo byEffects = this.getPotionInfo(contents);
      if (byEffects != null) {
         return byEffects;
      }

      return this.getPotionInfo(potionEntity.getStack().getName().getString());
   }

   private PotionInfo getPotionInfo(PotionContentsComponent contents) {
      if (contents == null || !contents.hasEffects()) {
         return null;
      }

      boolean regenerationTwo = this.hasEffect(contents, StatusEffects.REGENERATION, 1);
      boolean strengthFive = this.hasEffect(contents, StatusEffects.STRENGTH, 4);
      boolean healthBoostThree = this.hasEffect(contents, StatusEffects.HEALTH_BOOST, 2);
      boolean strengthFour = this.hasEffect(contents, StatusEffects.STRENGTH, 3);
      boolean speedThree = this.hasEffect(contents, StatusEffects.SPEED, 2);

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

   private boolean hasEffect(PotionContentsComponent contents, RegistryEntry<StatusEffect> effect, int amplifier) {
      for (StatusEffectInstance instance : contents.getEffects()) {
         if (instance.getEffectType().equals(effect) && instance.getAmplifier() == amplifier) {
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

      MutableText text = Text.literal("");
      text.append(this.gradientText("vesence", themeStart, themeEnd, true));
      text.append(Text.literal(" ⇒ ").setStyle(this.grayStyle()));
      text.append(Text.literal(playerName).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(PLAYER))));
      text.append(Text.literal(" получил ").setStyle(this.grayStyle()));
      text.append(this.gradientText(potionInfo.displayName, potionInfo.startColor, potionInfo.endColor, true));
      text.append(Text.literal(" " + percent + "%").setStyle(this.grayStyle()));

      mc.player.sendMessage(text, false);
   }

   private MutableText gradientText(String text, int startColor, int endColor, boolean bold) {
      MutableText result = Text.literal("");

      for (int i = 0; i < text.length(); i++) {
         float progress = text.length() <= 1 ? 0.0F : (float) i / (text.length() - 1);
         int color = ColorUtil.interpolate(startColor, endColor, progress);
         result.append(Text.literal(String.valueOf(text.charAt(i)))
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
