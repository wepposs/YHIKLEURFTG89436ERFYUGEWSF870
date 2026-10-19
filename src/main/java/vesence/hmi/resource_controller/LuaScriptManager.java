package vesence.hmi.resource_controller;

import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;

public class LuaScriptManager {
    private static final LuaScriptManager INSTANCE = new LuaScriptManager();
    public final Globals sharedGlobals = JsePlatform.standardGlobals();

    private LuaScriptManager() {
    }

    public static LuaScriptManager getInstance() {
        return INSTANCE;
    }
}

