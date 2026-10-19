package vesence.utils.config.friend;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import vesence.Vesence;
import vesence.utils.friends.FriendStorage;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class FriendManager {
   public static MinecraftClient mc = MinecraftClient.getInstance();

   public static void init() {

      syncFromStorage();

      FriendStorage.setOnChangeCallback(FriendManager::syncFromStorage);
   }

   private static void syncFromStorage() {
      Friend.friends.clear();
      for (String name : FriendStorage.getFriends()) {
         Friend.friends.add(new Friend(name));
      }
   }

   public void add(String name) {
      FriendStorage.addFriend(name);
      syncFromStorage();
   }

   public Friend getFriend(String friend) {
      return Friend.friends.stream().filter(isFriend -> isFriend.getName().equalsIgnoreCase(friend)).findFirst().orElse(null);
   }

   public boolean isFriend(String friend) {
      return FriendStorage.isFriend(friend);
   }

   public void remove(String name) {
      FriendStorage.removeFriend(name);
      syncFromStorage();
   }

   public void clearFriend() {
      FriendStorage.clearFriends();
      syncFromStorage();
   }

   public static List<Friend> getFriends() {
      return Friend.friends;
   }

   public static boolean getNearFriends(String name) {
      if (mc.world == null) return false;
      return mc.world.getPlayers().stream().anyMatch(player -> player.getName().getString().equals(name));
   }
}
