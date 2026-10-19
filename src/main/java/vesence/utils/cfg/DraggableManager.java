package vesence.utils.cfg;

import com.google.gson.JsonObject;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.impl.visuals.Hud;
import vesence.module.impl.visuals.HudElement;
import vesence.Vesence;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class DraggableManager {
    private static DraggableManager instance;

    public static DraggableManager getInstance() {
        if (instance == null) {
            instance = new DraggableManager();
        }
        return instance;
    }

    public Map<String, NormalizedPosition> snapshotNormalizedPositions() {
        Map<String, NormalizedPosition> positions = new HashMap<>();

        if (Vesence.get == null || Vesence.get.manager == null) {
            return positions;
        }

        Hud hudModule = (Hud) Vesence.get.manager.module.stream()
                .filter(m -> m instanceof Hud)
                .findFirst()
                .orElse(null);

        if (hudModule != null) {
            for (HudElement element : hudModule.getHudElements()) {
                float normalizedX = element.x / 1920f;
                float normalizedY = element.y / 1080f;
                positions.put(element.name, new NormalizedPosition(normalizedX, normalizedY));
            }
        }

        return positions;
    }

    public void loadNormalizedPositions(Map<String, NormalizedPosition> positions) {
        if (Vesence.get == null || Vesence.get.manager == null) {
            return;
        }

        Hud hudModule = (Hud) Vesence.get.manager.module.stream()
                .filter(m -> m instanceof Hud)
                .findFirst()
                .orElse(null);

        if (hudModule != null) {
            for (HudElement element : hudModule.getAllHudElements()) {
                NormalizedPosition pos = positions.get(element.name);
                if (pos != null) {
                    element.x = pos.x() * 1920f;
                    element.y = pos.y() * 1080f;
                }
            }
        }
    }

    public Map<String, JsonObject> snapshotElementStates() {
        Map<String, JsonObject> states = new HashMap<>();

        if (Vesence.get == null || Vesence.get.manager == null) {
            return states;
        }

        Hud hudModule = (Hud) Vesence.get.manager.module.stream()
                .filter(m -> m instanceof Hud)
                .findFirst()
                .orElse(null);

        if (hudModule != null) {
            for (HudElement element : hudModule.getHudElements()) {
                JsonObject obj = new JsonObject();
                obj.addProperty("x", element.x / 1920f);
                obj.addProperty("y", element.y / 1080f);
                if (!element.enabled) {
                    obj.addProperty("enabled", false);
                }

                obj.addProperty("scale", element.scaleSetting.current);

                JsonObject settingsObj = new JsonObject();
                for (vesence.module.api.setting.impl.BooleanSetting bs : element.settings) {
                    settingsObj.addProperty(bs.name, bs.get());
                }
                for (vesence.module.api.setting.impl.BooleanSetting bs : element.bottomSettings) {
                    settingsObj.addProperty(bs.name, bs.get());
                }
                for (vesence.module.api.setting.impl.ModeSetting ms : element.modeSettings) {
                    settingsObj.addProperty("mode$" + ms.name, ms.currentMode);
                }
                for (vesence.module.api.setting.impl.ModeSetting ms : element.bottomModeSettings) {
                    settingsObj.addProperty("mode$" + ms.name, ms.currentMode);
                }
                obj.add("settings", settingsObj);
                states.put(element.name, obj);
            }
        }

        return states;
    }

    public void loadElementStates(Map<String, JsonObject> states) {
        if (Vesence.get == null || Vesence.get.manager == null) {
            return;
        }

        Hud hudModule = (Hud) Vesence.get.manager.module.stream()
                .filter(m -> m instanceof Hud)
                .findFirst()
                .orElse(null);

        if (hudModule != null) {
            for (HudElement element : hudModule.getAllHudElements()) {
                JsonObject obj = states.get(element.name);
                if (obj != null) {
                    if (obj.has("x")) element.x = obj.get("x").getAsFloat() * 1920f;
                    if (obj.has("y")) element.y = obj.get("y").getAsFloat() * 1080f;
                    if (obj.has("enabled")) element.enabled = obj.get("enabled").getAsBoolean();
                    if (obj.has("scale")) {
                        double sc = obj.get("scale").getAsDouble();
                        element.scaleSetting.setValue(sc);
                        element.scaleAnim.set(sc);
                    }
                    if (obj.has("settings") && obj.get("settings").isJsonObject()) {
                        JsonObject settingsObj = obj.getAsJsonObject("settings");
                        for (vesence.module.api.setting.impl.BooleanSetting bs : element.settings) {
                            if (settingsObj.has(bs.name)) bs.set(settingsObj.get(bs.name).getAsBoolean());
                        }
                        for (vesence.module.api.setting.impl.BooleanSetting bs : element.bottomSettings) {
                            if (settingsObj.has(bs.name)) bs.set(settingsObj.get(bs.name).getAsBoolean());
                        }
                        for (vesence.module.api.setting.impl.ModeSetting ms : element.modeSettings) {
                            String k = "mode$" + ms.name;
                            if (settingsObj.has(k)) ms.currentMode = settingsObj.get(k).getAsString();
                        }
                        for (vesence.module.api.setting.impl.ModeSetting ms : element.bottomModeSettings) {
                            String k = "mode$" + ms.name;
                            if (settingsObj.has(k)) ms.currentMode = settingsObj.get(k).getAsString();
                        }
                    }
                    element.targetX = element.x;
                    element.targetY = element.y;
                    element.targetInitialized = true;
                }
            }
        }
    }

    public record NormalizedPosition(float x, float y) {}

    public void resetHudElements() {
        if (Vesence.get == null || Vesence.get.manager == null) {
            return;
        }
        Hud hudModule = (Hud) Vesence.get.manager.module.stream()
                .filter(m -> m instanceof Hud)
                .findFirst()
                .orElse(null);
        if (hudModule == null) {
            return;
        }
        for (HudElement element : hudModule.getAllHudElements()) {
            element.enabled = true;
            element.x = element.defaultX;
            element.y = element.defaultY;
            element.targetX = element.defaultX;
            element.targetY = element.defaultY;
            element.scaleSetting.setValue(element.scaleSetting.getDefault());
            element.scaleAnim.set(element.scaleSetting.getDefault());
            element.targetInitialized = false;
            for (vesence.module.api.setting.impl.BooleanSetting bs : element.settings) {
                bs.set(bs.getDefault());
            }
            for (vesence.module.api.setting.impl.BooleanSetting bs : element.bottomSettings) {
                bs.set(bs.getDefault());
            }
            for (vesence.module.api.setting.impl.ModeSetting ms : element.modeSettings) {
                ms.currentMode = ms.getDefault();
            }
            for (vesence.module.api.setting.impl.ModeSetting ms : element.bottomModeSettings) {
                ms.currentMode = ms.getDefault();
            }
        }
    }
}
