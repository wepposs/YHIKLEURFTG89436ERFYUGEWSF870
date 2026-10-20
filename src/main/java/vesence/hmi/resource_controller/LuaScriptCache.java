package vesence.hmi.resource_controller;

import vesence.hmi.LuaTestHMI;
import vesence.hmi.classes.KeyBindManager;
import vesence.hmi.patricles.Particle;
import vesence.hmi.patricles.ParticleManager;
import vesence.hmi.patricles.Texture;
import vesence.hmi.resource_controller.LuaScriptManager;
import vesence.hmi.script_wrappers.C;
import vesence.hmi.script_wrappers.Easings;
import vesence.hmi.script_wrappers.I;
import vesence.hmi.script_wrappers.JSItems;
import vesence.hmi.script_wrappers.JSTags;
import vesence.hmi.script_wrappers.M;
import vesence.hmi.script_wrappers.P;
import vesence.hmi.script_wrappers.S;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.jse.CoerceJavaToLua;

public class LuaScriptCache {
    public static int swingSpeed = 9;
    private final Globals globals;
    private final LuaValue chunk;
    private boolean canRun = true;
    private final M mInstance = new M();
    private final I iInstance = new I();
    private final Texture textureInstance = new Texture();
    private final JSItems jsItemsInstance = new JSItems();
    private final JSTags jsTagsInstance = new JSTags();
    private final P pInstance = new P();
    private final Easings easingsInstance = new Easings();
    private final KeyBindManager keyBindManagerInstance = new KeyBindManager();
    private final S sInstance = new S();
    private final C cInstance = new C();
    private final ParticleManager particleManagerInstance = new ParticleManager();

    public LuaScriptCache(String sourceCode) throws IOException {
        this.globals = LuaScriptManager.getInstance().sharedGlobals;
        this.globals.set("M", CoerceJavaToLua.coerce((Object)this.mInstance));
        this.globals.set("I", CoerceJavaToLua.coerce((Object)this.iInstance));
        this.globals.set("Texture", CoerceJavaToLua.coerce((Object)this.textureInstance));
        this.globals.set("Items", CoerceJavaToLua.coerce((Object)this.jsItemsInstance));
        this.globals.set("Tags", CoerceJavaToLua.coerce((Object)this.jsTagsInstance));
        this.globals.set("P", CoerceJavaToLua.coerce((Object)this.pInstance));
        this.globals.set("Easings", CoerceJavaToLua.coerce((Object)this.easingsInstance));
        this.globals.set("KeyBindManager", CoerceJavaToLua.coerce((Object)this.keyBindManagerInstance));
        this.globals.set("S", CoerceJavaToLua.coerce((Object)this.sInstance));
        this.globals.set("C", CoerceJavaToLua.coerce((Object)this.cInstance));
        this.globals.set("particleManager", CoerceJavaToLua.coerce((Object)this.particleManagerInstance));
        this.globals.set("swingSpeed", (LuaValue)LuaValue.valueOf((int)swingSpeed));
        this.chunk = this.globals.load(sourceCode);
    }

    public void execute(PoseStack matrices, boolean bl, HashMap<String, Object> registry, float swingProgress, ItemStack item, AbstractClientPlayer player, InteractionHand hand, boolean mainHand, float deltaTime, float equipProgress, float mainHandSwingProgress, float offHandSwingProgress, boolean mainHandSwitchEvent, boolean offHandSwitchEvent, boolean swingMHand, boolean swingOHand, boolean interact, boolean blockBreaking, List<Particle> particles) {
        if (!this.canRun) {
            return;
        }
        try {
            this.globals.set("matrices", CoerceJavaToLua.coerce((Object)matrices));
            this.globals.set("bl", (LuaValue)LuaValue.valueOf((boolean)bl));
            this.globals.set("registry", CoerceJavaToLua.coerce(registry));
            this.globals.set("swingProgress", (LuaValue)LuaValue.valueOf((double)swingProgress));
            this.globals.set("offHandSwingProgress", (LuaValue)LuaValue.valueOf((double)offHandSwingProgress));
            this.globals.set("mainHandSwingProgress", (LuaValue)LuaValue.valueOf((double)mainHandSwingProgress));
            this.globals.set("item", CoerceJavaToLua.coerce((Object)item));
            this.globals.set("player", CoerceJavaToLua.coerce((Object)player));
            this.globals.set("hand", CoerceJavaToLua.coerce((Object)hand));
            this.globals.set("mainHand", (LuaValue)LuaValue.valueOf((boolean)mainHand));
            this.globals.set("deltaTime", (LuaValue)LuaValue.valueOf((double)deltaTime));
            this.globals.set("equipProgress", (LuaValue)LuaValue.valueOf((double)equipProgress));
            this.globals.set("mainHandSwitchEvent", (LuaValue)LuaValue.valueOf((boolean)mainHandSwitchEvent));
            this.globals.set("offHandSwitchEvent", (LuaValue)LuaValue.valueOf((boolean)offHandSwitchEvent));
            this.globals.set("swingOHand", (LuaValue)LuaValue.valueOf((boolean)swingOHand));
            this.globals.set("swingMHand", (LuaValue)LuaValue.valueOf((boolean)swingMHand));
            this.globals.set("interact", (LuaValue)LuaValue.valueOf((boolean)interact));
            this.globals.set("blockBreaking", (LuaValue)LuaValue.valueOf((boolean)blockBreaking));
            this.globals.set("particles", CoerceJavaToLua.coerce(particles));
            this.globals.set("renderAsBlock", CoerceJavaToLua.coerce((Object)LuaTestHMI.renderAsBlock));
            this.globals.set("hmiMainX", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getMainX()));
            this.globals.set("hmiMainY", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getMainY()));
            this.globals.set("hmiMainZ", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getMainZ()));
            this.globals.set("hmiOffX", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getOffX()));
            this.globals.set("hmiOffY", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getOffY()));
            this.globals.set("hmiOffZ", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getOffZ()));
            this.globals.set("hmiSwingStyle", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getSwingStyle()));
            this.globals.set("hmiMode", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getAnimationMode()));
            this.globals.set("hmiCrit", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getCrit()));
            this.globals.set("hmiInspectKey", LuaValue.valueOf(vesence.module.impl.visuals.LivingHands.getInspectKey()));
            this.chunk.call();
        }
        catch (Exception e) {
            System.err.println("[HoldMyItems] Lua runtime error: " + e.getMessage());
            SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PACK_LOAD_FAILURE, Component.literal("HMI Lua Runtime error!"), Component.literal(String.valueOf(e.getMessage())));
            this.canRun = false;
        }
    }
}

