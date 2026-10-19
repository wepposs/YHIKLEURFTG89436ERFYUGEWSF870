package vesence.hmi.resource_controller;

import vesence.hmi.LuaTestHMI;
import vesence.hmi.resource_controller.LuaScriptCache;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;

public class LuaAnimationResourceLoader
implements SimpleSynchronousResourceReloadListener {
    public Identifier getFabricId() {
        return Identifier.of((String)"holdmyitems", (String)"lua_animation_loader");
    }

    private String preprocessScript(String script) {
        if (script == null || script.isEmpty()) {
            return script;
        }
        Pattern globalPattern = Pattern.compile("global\\.(\\w+)\\s*=\\s*([^;]+)\\s*;");
        Pattern placeholderPattern = Pattern.compile("\\$\\{(\\w+)\\}");
        Pattern luajavaPattern = Pattern.compile("luajava\\s*\\.\\s*bindClass\\s*\\(\\s*\"[^\"]*\"\\s*\\)\\s*;?");
        HashSet<String> persistVars = new HashSet<String>();
        StringBuilder processed = new StringBuilder();
        boolean respackoptsLoaded = FabricLoader.getInstance().isModLoaded("respackopts");
        for (String line : script.split("\\r?\\n")) {
            Matcher luajavaMatcher = luajavaPattern.matcher(line);
            line = luajavaMatcher.replaceAll("");
            if (!respackoptsLoaded) {
                // Сопоставляем плейсхолдеры HMI с глобальными переменными Lua, значения
                // которых задаются каждый кадр из модуля Living Hands (живые настройки).
                Matcher placeholderMatcher = placeholderPattern.matcher(line);
                StringBuffer placeholderBuffer = new StringBuffer();
                while (placeholderMatcher.find()) {
                    String key = placeholderMatcher.group(1);
                    String replacement = switch (key) {
                        case "xOffset" -> "hmiHandX";
                        case "yOffset" -> "hmiHandY";
                        case "zOffset" -> "hmiHandZ";
                        case "inspectKeybind" -> "hmiInspectKey";
                        default -> "0";
                    };
                    placeholderMatcher.appendReplacement(placeholderBuffer, replacement);
                }
                placeholderMatcher.appendTail(placeholderBuffer);
                line = placeholderBuffer.toString();
            }
            Matcher globalMatcher = globalPattern.matcher(line);
            StringBuffer lineBuffer = new StringBuffer();
            boolean found = false;
            while (globalMatcher.find()) {
                String varName = globalMatcher.group(1);
                String value = globalMatcher.group(2).trim();
                persistVars.add(varName);
                globalMatcher.appendReplacement(lineBuffer, "local " + varName + " = registry:getOrDefault('" + varName + "', " + value + ")");
                found = true;
            }
            globalMatcher.appendTail(lineBuffer);
            if (found) {
                processed.append(lineBuffer.toString());
            } else {
                processed.append(line);
            }
            processed.append("\n");
        }
        if (!persistVars.isEmpty()) {
            processed.append("\n-- PERSIST VARIABLES\n");
            for (String varName : persistVars) {
                processed.append("registry:put('").append(varName).append("', ").append(varName).append(")\n");
            }
        }
        return processed.toString();
    }

    public void reload(ResourceManager manager) {
        LuaTestHMI.renderAsBlock.clear();
        this.loadSingle(manager, "holdmyitems/hand_pose.lua", script -> {
            try {
                LuaTestHMI.handScriptCache = new LuaScriptCache(script);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.loadSingle(manager, "holdmyitems/item_pose.lua", script -> {
            try {
                LuaTestHMI.itemScriptCache = new LuaScriptCache(script);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        this.loadSingle(manager, "holdmyitems/hand_relative_pose.lua", script -> {
            try {
                LuaTestHMI.handRelativeScriptCache = new LuaScriptCache(script);
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        LuaTestHMI.handAddonsCache = this.loadMultiple(manager, "holdmyitems/hand_addon.lua");
        LuaTestHMI.handRelativeAddonsCache = this.loadMultiple(manager, "holdmyitems/hand_relative_addon.lua");
        LuaTestHMI.itemAddonsCache = this.loadMultiple(manager, "holdmyitems/item_addon.lua");
    }

    private void loadSingle(ResourceManager manager, String path, Consumer<String> consumer) {
        Identifier id = Identifier.of((String)"minecraft", (String)path);
        try {
            Resource resource = manager.getResource(id).orElse(null);
            if (resource == null) {
                consumer.accept("");
                return;
            }
            try (InputStream stream = resource.getInputStream();){
                String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                content = this.preprocessScript(content);
                consumer.accept(content);
            }
        }
        catch (Exception e) {
            consumer.accept("");
            e.printStackTrace();
        }
    }

    private ArrayList<LuaScriptCache> loadMultiple(ResourceManager manager, String path) {
        Identifier id = Identifier.of((String)"minecraft", (String)path);
        ArrayList<LuaScriptCache> caches = new ArrayList<LuaScriptCache>();
        try {
            List<Resource> resources = manager.getAllResources(id);
            for (Resource resource : resources) {
                InputStream stream = resource.getInputStream();
                try {
                    String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                    content = this.preprocessScript(content);
                    caches.add(new LuaScriptCache(content));
                }
                finally {
                    if (stream == null) continue;
                    stream.close();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return caches;
    }
}

