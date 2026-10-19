package vesence.utils.friends;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Environment(EnvType.CLIENT)
public class FriendStorage {
    private static final Set<String> friends = new CopyOnWriteArraySet<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FRIENDS_FILE = new File("vesence/friends.json");
    private static Runnable onChangeCallback;

    static {
        load();
    }

    public static void addFriend(String name) {
        if (name != null && !name.isEmpty()) {
            friends.add(name.toLowerCase());
            save();
            notifyChange();
        }
    }

    public static void removeFriend(String name) {
        if (name != null) {
            friends.remove(name.toLowerCase());
            save();
            notifyChange();
        }
    }

    public static boolean isFriend(String name) {
        return name != null && friends.contains(name.toLowerCase());
    }

    public static Set<String> getFriends() {
        return new HashSet<>(friends);
    }

    public static void clearFriends() {
        friends.clear();
        save();
        notifyChange();
    }

    private static void save() {
        try {
            FRIENDS_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(FRIENDS_FILE)) {
                GSON.toJson(friends, writer);
            }
        } catch (Exception e) {
            System.err.println("Failed to save friends: " + e.getMessage());
        }
    }

    public static void setOnChangeCallback(Runnable callback) {
        onChangeCallback = callback;
    }

    private static void notifyChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    private static void load() {
        if (!FRIENDS_FILE.exists()) {
            return;
        }

        try (FileReader reader = new FileReader(FRIENDS_FILE)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                friends.addAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("Failed to load friends: " + e.getMessage());
        }
    }
}
