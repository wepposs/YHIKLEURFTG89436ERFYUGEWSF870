package vesence.utils.friends;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import vesence.utils.commands.suggestions.AbstractSubCommandSuggestions;
import vesence.utils.commands.suggestions.CommandSuggestions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

@Environment(EnvType.CLIENT)
public final class FriendCommandSuggestions extends AbstractSubCommandSuggestions {
   private static final FriendCommandSuggestions INSTANCE = new FriendCommandSuggestions();
   private static final Map<String, SubCommand> SUB_COMMANDS;

   private FriendCommandSuggestions() {
   }

   public static FriendCommandSuggestions getInstance() {
      return INSTANCE;
   }

   @Override
   public List<String> commandAliases() {
      return FriendCommand.getInstance().aliases();
   }

   @Override
   protected Map<String, SubCommand> subCommands() {
      return SUB_COMMANDS;
   }

   @Override
   protected String aliasDescription() {
      return "Friend command alias";
   }

   @Override
   protected List<CommandSuggestions.SuggestionEntry> argumentEntries(String subCommand, int argIndex, String partialArgument) {
      if (argIndex != 0) {
         return List.of();
      }
      if ("remove".equals(subCommand)) {
         return buildFriendEntries(partialArgument);
      }
      if ("add".equals(subCommand)) {
         return buildOnlinePlayerEntries(partialArgument);
      }
      return List.of();
   }

   private static List<CommandSuggestions.SuggestionEntry> buildFriendEntries(String partialArgument) {
      List<String> friends = new ArrayList<>(FriendStorage.getFriends());
      if (friends.isEmpty()) {
         return List.of();
      }
      Collections.sort(friends);
      String normalized = partialArgument.toLowerCase(Locale.ROOT);
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (String friend : friends) {
         if (normalized.isEmpty() || friend.toLowerCase(Locale.ROOT).startsWith(normalized)) {
            entries.add(argumentEntry(friend, "Friend", partialArgument));
         }
      }
      return entries;
   }

   private static List<CommandSuggestions.SuggestionEntry> buildOnlinePlayerEntries(String partialArgument) {
      MinecraftClient mc = MinecraftClient.getInstance();
      ClientPlayNetworkHandler nh = mc.getNetworkHandler();
      if (nh == null) {
         return List.of();
      }
      TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      for (PlayerListEntry entry : nh.getPlayerList()) {
         try {
            String name = entry.getProfile().name();
            if (name != null && !name.isBlank() && !FriendStorage.isFriend(name)) {
               names.add(name);
            }
         } catch (Throwable ignored) {
         }
      }
      if (names.isEmpty()) {
         return List.of();
      }
      String normalized = partialArgument.toLowerCase(Locale.ROOT);
      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (String name : names) {
         if (normalized.isEmpty() || name.toLowerCase(Locale.ROOT).startsWith(normalized)) {
            entries.add(argumentEntry(name, "Player", partialArgument));
         }
      }
      return entries;
   }

   static {
      Map<String, SubCommand> commands = new LinkedHashMap<>();
      commands.put("add", new SubCommand("add", "add <nick>", "Add a player to friends", true));
      commands.put("remove", new SubCommand("remove", "remove <nick>", "Remove a player from friends", true));
      commands.put("list", new SubCommand("list", "list", "Show all friends", false));
      commands.put("clear", new SubCommand("clear", "clear", "Clear the friends list", false));
      SUB_COMMANDS = Collections.unmodifiableMap(commands);
   }
}
