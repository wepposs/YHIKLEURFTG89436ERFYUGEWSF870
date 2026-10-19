package vesence.utils.staff;

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
public class StaffStorage {
    private static final Set<String> staff = new CopyOnWriteArraySet<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File STAFF_FILE = new File("vesence/staff.json");
    private static Runnable onChangeCallback;

    static {
        load();
    }

    public static void addStaff(String name) {
        if (name != null && !name.isEmpty()) {
            staff.add(name.toLowerCase());
            save();
            notifyChange();
        }
    }

    public static void removeStaff(String name) {
        if (name != null) {
            staff.remove(name.toLowerCase());
            save();
            notifyChange();
        }
    }

    public static boolean isStaff(String name) {
        return name != null && staff.contains(name.toLowerCase());
    }

    public static Set<String> getStaff() {
        return new HashSet<>(staff);
    }

    public static void clearStaff() {
        staff.clear();
        save();
        notifyChange();
    }

    public static void setOnChangeCallback(Runnable callback) {
        onChangeCallback = callback;
    }

    private static void notifyChange() {
        if (onChangeCallback != null) {
            onChangeCallback.run();
        }
    }

    private static void save() {
        try {
            STAFF_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(STAFF_FILE)) {
                GSON.toJson(staff, writer);
            }
        } catch (Exception e) {
            System.err.println("Failed to save staff: " + e.getMessage());
        }
    }

    private static void load() {
        if (!STAFF_FILE.exists()) return;

        try (FileReader reader = new FileReader(STAFF_FILE)) {
            Type type = new TypeToken<Set<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                staff.addAll(loaded);
            }
        } catch (Exception e) {
            System.err.println("Failed to load staff: " + e.getMessage());
        }
    }
}
