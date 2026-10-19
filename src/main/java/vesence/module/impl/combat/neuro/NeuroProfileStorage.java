package vesence.module.impl.combat.neuro;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.nio.file.Files;

public class NeuroProfileStorage {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static String PROFILE_PATH;
    private static File profileFile;
    private static Gson gson;
    private static NeuroSampler currentProfile;

    public static void init() {
        PROFILE_PATH = mc.runDirectory.getPath() + "\\vesence\\neuro\\aura_neuro.json";
        gson = new GsonBuilder().setPrettyPrinting().create();
        profileFile = new File(PROFILE_PATH);
        currentProfile = load();
    }

    public static void save() {
        File parent = profileFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        
        getProfile().cleanup();
        
        try {
            Files.writeString(profileFile.toPath(), gson.toJson(getProfile()));
        } catch (Exception e) {
            // Ignore save failures
        }
    }

    private static NeuroSampler load() {
        if (!profileFile.exists()) {
            return new NeuroSampler();
        }
        
        try {
            String json = Files.readString(profileFile.toPath());
            NeuroSampler profile = gson.fromJson(json, NeuroSampler.class);
            if (profile == null) {
                profile = new NeuroSampler();
            }
            profile.cleanup();
            return profile;
        } catch (Exception e) {
            return new NeuroSampler();
        }
    }

    public static NeuroSampler getProfile() {
        if (currentProfile == null) {
            currentProfile = new NeuroSampler();
        }
        currentProfile.validate();
        return currentProfile;
    }

    public static void reset() {
        currentProfile = new NeuroSampler();
        save();
    }
}
