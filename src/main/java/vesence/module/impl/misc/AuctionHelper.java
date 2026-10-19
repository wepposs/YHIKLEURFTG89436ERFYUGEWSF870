package vesence.module.impl.misc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Team;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.impl.HandledScreenEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BindSettings;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.utils.render.ColorUtil;

/**
 * Полный порт AuctionHelper из RelevantPremiumpp4: подсвечивает самые дешёвые
 * лоты аукциона (градиент до 8 штук + свечение на самом дешёвом), фильтрует
 * шипы и недоступные по балансу лоты, добавляет цену за штуку в тултип и ищет
 * предмет из руки по биндом. Логика цен/количества/баланса перенесена дословно.
 */
@IModule(name = "AuctionHelper", description = "Подсвечивает самые дешёвые лоты аукциона, считает цену за штуку и ищет предмет из руки", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public final class AuctionHelper extends Module {

   private static final int MAX_HIGHLIGHT = 8;

   private static final int[] SLOT_COLORS = {
      0x00FF44,
      0x66FF00,
      0xAAFF00,
      0xFFFF00,
      0xFFCC00,
      0xFF8800,
      0xFF4400,
      0xFF0000,
   };

   private static AuctionHelper INSTANCE;

   private final BooleanSetting filterThorns = new BooleanSetting("Фильтр шипов", true);
   private final BooleanSetting pricePerItem = new BooleanSetting("Цена за предмет", false);
   private final BooleanSetting unitPriceTip = new BooleanSetting("Цена за штуку", false);
   private final BooleanSetting affordableOnly = new BooleanSetting("Только доступные", false);
   private final BindSettings searchHeldItem = new BindSettings("Поиск предмета в руке", -1);

   private final List<Slot> cheapestSlots = new ArrayList<>(MAX_HIGHLIGHT);
   private int lastSyncId = -1;
   private int lastRevision = -1;
   private boolean searchKeyWasDown = false;

   public AuctionHelper() {
      INSTANCE = this;
      this.addSettings(new Setting[]{filterThorns, pricePerItem, unitPriceTip, affordableOnly, searchHeldItem});
   }

   @Override
   public void onEnable() {
      this.resetState();
      super.onEnable();
   }

   @Override
   public void onDisable() {
      this.resetState();
      this.searchKeyWasDown = false;
      super.onDisable();
   }

   // ── бинд «поиск предмета в руке» ──────────────────────────────────────────

   @EventInit
   public void onUpdate(EventUpdate event) {
      if (mc.player == null || mc.currentScreen instanceof ChatScreen) {
         this.searchKeyWasDown = false;
         return;
      }
      boolean down = this.searchHeldItem.isPressed();
      if (down && !this.searchKeyWasDown && mc.currentScreen == null) {
         this.searchHeldItem();
      }
      this.searchKeyWasDown = down;
   }

   // ── рендер подсветки в GUI аукциона ───────────────────────────────────────

   @EventInit
   public void onHandledScreen(HandledScreenEvent event) {
      if (mc.player == null
            || !(mc.currentScreen instanceof GenericContainerScreen screen)
            || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
         this.resetState();
         return;
      }
      if (!isSupportedAuctionScreen(screen, handler)) {
         this.resetState();
         return;
      }

      this.refreshSlotsIfNeeded(handler);

      DrawContext context = event.getDrawContext();
      int baseX = event.getScreenX();
      int baseY = event.getScreenY();
      float pulse = (float) Math.abs(Math.sin(System.currentTimeMillis() / 800.0 * Math.PI));

      for (int i = 0; i < this.cheapestSlots.size(); i++) {
         Slot slot = this.cheapestSlots.get(i);
         if (slot == null) {
            continue;
         }

         int rgb = SLOT_COLORS[i];
         int r = (rgb >> 16) & 0xFF;
         int g = (rgb >> 8) & 0xFF;
         int b = rgb & 0xFF;
         int sx = baseX + slot.x;
         int sy = baseY + slot.y;

         if (i == 0) {
            // Мягкое свечение вокруг самого дешёвого лота (приближение drawShadow)
            for (int layer = 3; layer >= 1; layer--) {
               int a = (int) ((28 + pulse * 22) * (4 - layer) / 3.0);
               context.fill(sx - layer, sy - layer, sx + 16 + layer, sy + 16 + layer, ColorUtil.getColor(r, g, b, Math.max(0, a)));
            }
            int fillAlpha = (int) (90 + pulse * 80);
            context.fill(sx, sy, sx + 16, sy + 16, ColorUtil.getColor(r, g, b, fillAlpha));
         } else {
            int alpha = Math.max(40, (int) (0x70 * (0.5F + pulse * 0.3F)));
            context.fill(sx, sy, sx + 16, sy + 16, ColorUtil.getColor(r, g, b, alpha));
         }
      }
   }

   // ── тултип «цена за штуку» (вызывается из ScreenTooltipMixin) ──────────────

   public static void appendPerUnitTooltip(ItemStack stack, List<Text> tooltip) {
      AuctionHelper inst = INSTANCE;
      if (inst == null || !inst.enable || !inst.unitPriceTip.get()) {
         return;
      }
      if (stack == null || stack.isEmpty() || tooltip == null) {
         return;
      }
      if (!onSupportedAuctionScreen() || isUnavailableLot(stack)) {
         return;
      }

      int totalPrice = extractPrice(stack);
      if (totalPrice <= 0) {
         totalPrice = extractPriceFallback(stack);
      }
      if (totalPrice <= 0) {
         return;
      }

      int qty = extractQuantity(stack);
      long unitPrice = Math.round((double) totalPrice / Math.max(1, qty));

      String priceStr = "$" + formatMoney(unitPrice);
      String qtyStr = qty > 1 ? " (x" + qty + ")" : "";

      tooltip.add(Text.empty());
      MutableText line = Text.literal("Цена за штуку: ")
            .formatted(Formatting.GRAY)
            .append(Text.literal(priceStr).formatted(Formatting.GREEN))
            .append(Text.literal(qtyStr).formatted(Formatting.DARK_GRAY));
      tooltip.add(line);
   }

   // ── обновление списка дешёвых слотов ──────────────────────────────────────

   private void refreshSlotsIfNeeded(GenericContainerScreenHandler handler) {
      if (handler.syncId == this.lastSyncId && handler.getRevision() == this.lastRevision) {
         return;
      }
      this.updateSlots(handler);
      this.lastSyncId = handler.syncId;
      this.lastRevision = handler.getRevision();
   }

   private void updateSlots(GenericContainerScreenHandler handler) {
      List<SlotData> candidates = new ArrayList<>();
      int topSlots = Math.min(45, handler.getRows() * 9);
      long balance = this.affordableOnly.get() ? this.getPlayerBalance() : Long.MAX_VALUE;

      for (int i = 0; i < topSlots && i < handler.slots.size(); i++) {
         Slot slot = handler.getSlot(i);
         if (slot == null || !slot.hasStack()) {
            continue;
         }

         ItemStack stack = slot.getStack();
         if (isUnavailableLot(stack)) {
            continue;
         }
         if (this.filterThorns.get() && this.hasThorns(stack)) {
            continue;
         }

         int price = extractPrice(stack);
         if (price <= 0) {
            price = extractPriceFallback(stack);
         }
         if (price <= 0) {
            continue;
         }

         // Только доступные: пропускаем лот дороже баланса. Если баланс не
         // прочитался (-1) — фильтр не применяется (показываем всё).
         if (this.affordableOnly.get() && balance >= 0 && price > balance) {
            continue;
         }

         int quantity = Math.max(1, extractQuantity(stack));
         int effectivePrice = this.pricePerItem.get() && quantity > 1 ? price / quantity : price;

         candidates.add(new SlotData(slot, effectivePrice));
      }

      candidates.sort(Comparator.comparingInt(SlotData::price));

      this.cheapestSlots.clear();
      for (int i = 0; i < Math.min(MAX_HIGHLIGHT, candidates.size()); i++) {
         this.cheapestSlots.add(candidates.get(i).slot());
      }
   }

   // ── баланс игрока (для фильтра «только доступные») ────────────────────────

   private long getPlayerBalance() {
      if (mc.world == null || mc.player == null) {
         return -1L;
      }

      Scoreboard sb = mc.world.getScoreboard();
      if (sb == null) {
         return -1L;
      }

      ScoreboardObjective sidebar = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
      if (sidebar != null) {
         long fromSidebar = scanScoreboardObjective(sb, sidebar);
         if (fromSidebar >= 0) {
            return fromSidebar;
         }
      }

      try {
         PlayerListEntry self = mc.getNetworkHandler() != null
               ? mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid())
               : null;
         if (self != null) {
            Text display = self.getDisplayName();
            if (display != null) {
               long money = parseMoneyFromText(display.getString());
               if (money >= 0) {
                  return money;
               }
            }
            long money = parseMoneyFromText(mc.player.getName().getString());
            if (money >= 0) {
               return money;
            }
         }
      } catch (Throwable ignored) {
      }

      return -1L;
   }

   private static long scanScoreboardObjective(Scoreboard sb, ScoreboardObjective objective) {
      try {
         for (ScoreHolder holder : sb.getKnownScoreHolders()) {
            String name = holder.getNameForScoreboard();
            if (name == null) {
               continue;
            }
            long money = parseMoneyFromText(name);
            if (money >= 0) {
               return money;
            }
            Team team = sb.getScoreHolderTeam(name);
            if (team != null) {
               long pref = parseMoneyFromText(team.getPrefix().getString());
               if (pref >= 0) {
                  return pref;
               }
               long suf = parseMoneyFromText(team.getSuffix().getString());
               if (suf >= 0) {
                  return suf;
               }
            }
         }
         long fromTitle = parseMoneyFromText(objective.getDisplayName().getString());
         if (fromTitle >= 0) {
            return fromTitle;
         }
      } catch (Throwable ignored) {
      }
      return -1L;
   }

   private static final Pattern BALANCE_PATTERN =
         Pattern.compile("(?iu)(?:баланс|деньги|money|coins|кошел[её]к|cash)\\s*[:=]?\\s*([\\d\\s.,]+)");
   private static final Pattern CURRENCY_NEAR_NUMBER =
         Pattern.compile("([\\d\\s.,]{2,})\\s*[$₽€¥¢⛃◊]");

   private static long parseMoneyFromText(String raw) {
      if (raw == null || raw.isEmpty()) {
         return -1L;
      }
      String cleaned = raw.replaceAll("§.", " ").trim();
      if (cleaned.isEmpty()) {
         return -1L;
      }

      Matcher m = BALANCE_PATTERN.matcher(cleaned);
      if (m.find()) {
         long parsed = parseDigits(m.group(1));
         if (parsed >= 0) {
            return parsed;
         }
      }

      m = CURRENCY_NEAR_NUMBER.matcher(cleaned);
      if (m.find()) {
         long parsed = parseDigits(m.group(1));
         if (parsed >= 0) {
            return parsed;
         }
      }
      return -1L;
   }

   private static long parseDigits(String chunk) {
      if (chunk == null) {
         return -1L;
      }
      String digits = chunk.replaceAll("[\\s.,]", "");
      if (digits.isEmpty()) {
         return -1L;
      }
      try {
         return Long.parseLong(digits);
      } catch (NumberFormatException e) {
         return -1L;
      }
   }

   // ── определение экрана аукциона / лотов ───────────────────────────────────

   private static boolean onSupportedAuctionScreen() {
      if (mc.player == null || !(mc.currentScreen instanceof GenericContainerScreen screen)
            || !(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler handler)) {
         return false;
      }
      return isSupportedAuctionScreen(screen, handler);
   }

   private static boolean isSupportedAuctionScreen(GenericContainerScreen screen, GenericContainerScreenHandler handler) {
      String title = normalize(screen.getTitle().getString());
      if (title.contains("аукцион") || title.contains("auction")) {
         return true;
      }
      if (handler.getRows() < 6 || handler.slots.size() <= 49) {
         return false;
      }
      ItemStack refresh = handler.getSlot(49).getStack();
      return !refresh.isEmpty() && (refresh.isOf(Items.CLOCK) || refresh.isOf(Items.NETHER_STAR));
   }

   private void searchHeldItem() {
      if (mc.player == null || mc.getNetworkHandler() == null) {
         return;
      }
      ItemStack stack = mc.player.getMainHandStack();
      if (stack.isEmpty()) {
         sendMessage("AuctionHelper: возьмите предмет в основную руку.");
         return;
      }
      String query = sanitizeAuctionSearchName(stack.getName().getString());
      if (query.isEmpty()) {
         sendMessage("AuctionHelper: не удалось построить запрос для поиска.");
         return;
      }
      mc.getNetworkHandler().sendChatCommand("ah search " + query);
      sendMessage("AuctionHelper: поиск " + query + ".");
   }

   private boolean hasThorns(ItemStack stack) {
      ItemEnchantmentsComponent enchantments =
            stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
      for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
         String id = enchantment.getKey().map(key -> key.getValue().getPath()).orElse("");
         if (id.contains("thorns")) {
            return true;
         }
      }
      return false;
   }

   private static boolean isUnavailableLot(ItemStack stack) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }
      String name = normalize(stack.getName().getString());
      return stack.isOf(Items.BARRIER) || name.contains("товар не актуален");
   }

   // ── извлечение цены / количества из лора ──────────────────────────────────

   private static final Pattern PRICE_PATTERN_LORE =
         Pattern.compile("(?iu)цен[аa]\\s*:?\\s*([\\d\\s,]+)");
   private static final Pattern QUANTITY_PATTERN =
         Pattern.compile("(?iu)(?:кол(?:-во|во|ичество)?)\\s*:?\\s*(\\d+)");
   private static final Pattern PRICE_PATTERN_FALLBACK =
         Pattern.compile("(?iu)цен[аa]\\s*:?\\s*([\\d\\s,.]+)");

   private static int extractPrice(ItemStack stack) {
      for (String line : extractRawLore(stack)) {
         Matcher matcher = PRICE_PATTERN_LORE.matcher(normalizeText(line));
         if (!matcher.find()) {
            continue;
         }
         String digits = matcher.group(1).replace(" ", "").replace(",", "");
         try {
            return Integer.parseInt(digits);
         } catch (NumberFormatException ignored) {
            return -1;
         }
      }
      return -1;
   }

   private static int extractQuantity(ItemStack stack) {
      for (String line : extractRawLore(stack)) {
         Matcher matcher = QUANTITY_PATTERN.matcher(normalizeText(line));
         if (!matcher.find()) {
            continue;
         }
         try {
            return Integer.parseInt(matcher.group(1));
         } catch (NumberFormatException ignored) {
            return Math.max(1, stack.getCount());
         }
      }
      return Math.max(1, stack.getCount());
   }

   private static int extractPriceFallback(ItemStack stack) {
      LoreComponent lore = stack.get(DataComponentTypes.LORE);
      if (lore == null) {
         return -1;
      }
      for (Text line : lore.lines()) {
         Matcher m = PRICE_PATTERN_FALLBACK.matcher(normalize(line.getString()));
         if (!m.find()) {
            continue;
         }
         try {
            return Integer.parseInt(m.group(1).replace(" ", "").replace(",", "").replace(".", ""));
         } catch (NumberFormatException ignored) {
         }
      }
      return -1;
   }

   private static List<String> extractRawLore(ItemStack stack) {
      LoreComponent lore = stack.get(DataComponentTypes.LORE);
      if (lore == null || lore.lines().isEmpty()) {
         return List.of();
      }
      List<String> lines = new ArrayList<>();
      for (Text line : lore.lines()) {
         lines.add(line.getString());
      }
      return lines;
   }

   // ── строковые утилиты ─────────────────────────────────────────────────────

   private static String formatMoney(long value) {
      String s = Long.toString(value);
      StringBuilder sb = new StringBuilder();
      int start = s.length() % 3;
      if (start > 0) {
         sb.append(s, 0, start);
      }
      for (int i = start; i < s.length(); i += 3) {
         if (sb.length() > 0) {
            sb.append(' ');
         }
         sb.append(s, i, i + 3);
      }
      return sb.toString();
   }

   private static String sanitizeAuctionSearchName(String value) {
      if (value == null) {
         return "";
      }
      return value.toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replaceAll("§.", " ")
            .replaceAll("[^\\p{L} ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
   }

   private static String normalizeText(String value) {
      if (value == null) {
         return "";
      }
      return value.toLowerCase(Locale.ROOT)
            .replace('ё', 'е')
            .replace('\u2014', '-')
            .replace('\u2013', '-')
            .replaceAll("§.", " ")
            .replaceAll("[^\\p{L}\\p{Nd}_:\\- ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
   }

   private static String normalize(String value) {
      return value == null ? ""
            : value.toLowerCase(Locale.ROOT)
                  .replace('ё', 'е')
                  .replaceAll("§.", " ")
                  .replaceAll("[^\\p{L}_:\\- ]", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
   }

   private static void sendMessage(String text) {
      if (mc.player != null) {
         mc.player.sendMessage(Text.literal(text), false);
      }
   }

   private void resetState() {
      this.cheapestSlots.clear();
      this.lastSyncId = -1;
      this.lastRevision = -1;
   }

   private record SlotData(Slot slot, int price) {}
}
