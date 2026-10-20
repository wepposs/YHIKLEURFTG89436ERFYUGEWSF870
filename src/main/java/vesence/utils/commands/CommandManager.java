package vesence.utils.commands;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;

import java.util.*;
import java.util.Map.Entry;

@Environment(EnvType.CLIENT)
public final class CommandManager {
   private static final CommandManager INSTANCE = new CommandManager();
   private final Map<String, RegisteredCommand> commandsByName = new LinkedHashMap<>();
   private final Map<String, RegisteredCommand> aliases = new LinkedHashMap<>();

   private static final String PREFIX = "[weppos]";
   private static final int ERROR_START = 16739179;
   private static final int ERROR_END   = 16723555;

   private CommandManager() {
   }

   public static CommandManager getInstance() {
      return INSTANCE;
   }

   public synchronized void register(Command command) {
      Objects.requireNonNull(command, "command");
      String canonicalName = this.normalize(command.name());
      if (this.commandsByName.containsKey(canonicalName)) {
         throw new IllegalStateException("Command '" + command.name() + "' already registered");
      } else {
         List<String> aliasList = command.aliases();
         if (aliasList != null && !aliasList.isEmpty()) {
            List<String> normalizedAliases = new ArrayList<>(aliasList.size());

            for (String alias : aliasList) {
               if (alias == null || alias.isBlank()) {
                  throw new IllegalArgumentException("Command '" + command.name() + "' contains invalid alias");
               }

               String normalizedAlias = this.normalize(alias);
               if (this.aliases.containsKey(normalizedAlias)) {
                  throw new IllegalStateException("Alias '" + alias + "' already bound to command '" + this.aliases.get(normalizedAlias).command().name() + "'");
               }

               normalizedAliases.add(alias);
            }

            RegisteredCommand registration = new RegisteredCommand(command, List.copyOf(normalizedAliases));
            this.commandsByName.put(canonicalName, registration);

            for (String alias : normalizedAliases) {
               this.aliases.put(this.normalize(alias), registration);
            }
         } else {
            throw new IllegalArgumentException("Command '" + command.name() + "' must declare at least one alias");
         }
      }
   }

   public synchronized Optional<RegisteredCommand> findByAlias(String alias) {
      return alias == null ? Optional.empty() : Optional.ofNullable(this.aliases.get(this.normalize(alias)));
   }

   public synchronized RegisteredCommand getByName(String name) {
      return name == null ? null : this.commandsByName.get(this.normalize(name));
   }

   public synchronized List<RegisteredCommand> getRegisteredCommands() {
      return List.copyOf(this.commandsByName.values());
   }

   public boolean handleMessage(String message) {
      if (message == null) {
         return false;
      }
      String trimmed = message.trim();
      if (trimmed.isEmpty() || trimmed.charAt(0) != '.') {
         return false;
      }

      RegisteredCommand match = null;
      String matchedAlias = null;
      synchronized (this) {
         for (Entry<String, RegisteredCommand> entry : this.aliases.entrySet()) {
            String normalizedAlias = entry.getKey();
            RegisteredCommand registration = entry.getValue();
            String rawAlias = registration.resolveAlias(normalizedAlias);
            if (rawAlias != null && this.startsWithAlias(trimmed, rawAlias)) {
               match = registration;
               matchedAlias = rawAlias;
               break;
            }
         }
      }

      if (match != null && matchedAlias != null) {
         String arguments = trimmed.length() > matchedAlias.length() ? trimmed.substring(matchedAlias.length()).trim() : "";
         CommandContext context = new CommandContext(match.command(), matchedAlias, message, arguments);
         try {
            match.command().execute(context, arguments);
         } catch (CommandException ex) {
            context.sendError(ex.getMessage());
         }
      } else {
         sendNotFound(trimmed);
      }

      // Сообщение начинается с '.' — в любом случае не отправляем в чат
      return true;
   }

   // ── helpers ──────────────────────────────────────────────────────────────

   private void sendNotFound(String input) {
      Minecraft client = Minecraft.getInstance();
      if (client == null) return;

      MutableComponent prefix  = buildGradient(PREFIX, ERROR_START, ERROR_END);
      MutableComponent payload = Component.literal(" команда не найдена: " + input)
              .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5555)));
      MutableComponent full = prefix.append(payload);

      if (client.player != null) {
         client.player.sendSystemMessage(full);
      } else if (client.gui != null) {
         client.gui.hud.getChat().addClientSystemMessage(full);
      }
   }

   private MutableComponent buildGradient(String text, int startColor, int endColor) {
      MutableComponent result = Component.empty();
      int[] codePoints = text.codePoints().toArray();
      int length = codePoints.length;
      for (int i = 0; i < length; i++) {
         double ratio = length <= 1 ? 0.0 : (double) i / (length - 1);
         int color = interpolateColor(startColor, endColor, ratio);
         String ch = new String(Character.toChars(codePoints[i]));
         result = result.append(Component.literal(ch).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
      }
      return result;
   }

   private int interpolateColor(int start, int end, double ratio) {
      double r = Math.max(0.0, Math.min(1.0, ratio));
      int sr = (start >> 16) & 0xFF, sg = (start >> 8) & 0xFF, sb = start & 0xFF;
      int er = (end   >> 16) & 0xFF, eg = (end   >> 8) & 0xFF, eb = end   & 0xFF;
      return ((int) Math.round(sr + (er - sr) * r) & 0xFF) << 16
              | ((int) Math.round(sg + (eg - sg) * r) & 0xFF) << 8
              |  (int) Math.round(sb + (eb - sb) * r) & 0xFF;
   }

   private boolean startsWithAlias(String message, String alias) {
      if (message.length() < alias.length()) return false;
      return message.regionMatches(true, 0, alias, 0, alias.length())
              && (message.length() == alias.length() || Character.isWhitespace(message.charAt(alias.length())));
   }

   private String normalize(String value) {
      return value.toLowerCase(Locale.ROOT);
   }

   // ── record ────────────────────────────────────────────────────────────────

   @Environment(EnvType.CLIENT)
   public record RegisteredCommand(Command command, List<String> aliases) {
      public RegisteredCommand(Command command, List<String> aliases) {
         command = Objects.requireNonNull(command, "command");
         aliases = aliases == null ? List.of() : List.copyOf(aliases);
         this.command = command;
         this.aliases = aliases;
      }

      public List<String> aliases() {
         return Collections.unmodifiableList(this.aliases);
      }

      public String resolveAlias(String normalizedAlias) {
         for (String alias : this.aliases) {
            if (alias.equalsIgnoreCase(normalizedAlias)) return alias;
         }
         return null;
      }
   }
}