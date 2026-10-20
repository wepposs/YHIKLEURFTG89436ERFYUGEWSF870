package vesence.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.impl.visuals.attributes.AttributesChinaHat;
import vesence.module.impl.visuals.attributes.AttributesCrystal;
import vesence.module.impl.visuals.attributes.AttributesEars;
import vesence.module.impl.visuals.attributes.AttributesNeck;
import vesence.module.impl.visuals.attributes.AttributesNimbus;
import vesence.module.impl.visuals.attributes.AttributesTail;
import vesence.module.impl.visuals.attributes.AttributesWings;
import vesence.utils.render.ColorUtil;

/**
 * ╨Ь╨╛╨┤╤Г╨╗╤М Attributes тАФ ╨║╨╛╤Б╨╝╨╡╤В╨╕╤З╨╡╤Б╨║╨╕╨╡ ╨░╤В╤А╨╕╨▒╤Г╤В╤Л ╨┤╨╗╤П ╨╗╨╛╨║╨░╨╗╤М╨╜╨╛╨│╨╛ ╨╕╨│╤А╨╛╨║╨░.
 * ╨Я╨╛╤А╤В ╨║╨╛╤Б╨╝╨╡╤В╨╕╨║ ╨╕╨╖ ╨║╨╗╨╕╨╡╨╜╤В╨░ RelevantPremium (╤Д╤Г╨╜╨║╤Ж╨╕╤П Cosmetic): ┬л╨е╨▓╨╛╤Б╤В┬╗,
 * ┬л╨г╤И╨║╨╕┬╗, ┬л╨и╨╡╤П┬╗, ┬л╨Ъ╤А╨╕╤Б╤В╨░╨╗╨╗ ╨┤╤А╤Г╨│╨░┬╗ ╨╕ ┬л╨Э╨░╤А╤Г╤В╨╛ ╨▒╨╡╨│┬╗. ╨Т╨╕╨╖╤Г╨░╨╗╤М╨╜╤Л╨╡ ╤Н╨╗╨╡╨╝╨╡╨╜╤В╤Л ╤А╨╕╤Б╤Г╤О╤В╤Б╤П
 * ╨▓ ╤Ж╨▓╨╡╤В╨╡ ╤В╨╡╨╝╤Л ╨║╨╗╨╕╨╡╨╜╤В╨░; ┬л╨Э╨░╤А╤Г╤В╨╛ ╨▒╨╡╨│┬╗ ╨╝╨╡╨╜╤П╨╡╤В ╨┐╨╛╨╖╤Г ╤А╤Г╨║ ╤З╨╡╤А╨╡╨╖ PlayerEntityModelMixin.
 */
@Environment(EnvType.CLIENT)
@IModule(name = "Attributes", description = "\u0410\u0442\u0440\u0438\u0431\u0443\u0442\u044b: \u0445\u0432\u043e\u0441\u0442, \u0443\u0448\u043a\u0438, \u0448\u0435\u044f, \u043a\u0440\u0438\u0441\u0442\u0430\u043b\u043b, \u043d\u0430\u0440\u0443\u0442\u043e \u0431\u0435\u0433", category = Category.VISUALS)
public class Attributes extends Module {

    public final BooleanSetting tail = new BooleanSetting("\u0425\u0432\u043e\u0441\u0442", true);
    public final BooleanSetting ears = new BooleanSetting("\u0423\u0448\u043a\u0438", true);
    public final BooleanSetting neck = new BooleanSetting("\u0428\u0435\u044f", false);
    public final BooleanSetting crystal = new BooleanSetting("\u041a\u0440\u0438\u0441\u0442\u0430\u043b\u043b \u0434\u0440\u0443\u0433\u0430", false);
    // ╨Э╨╕╨╝╨▒
    public final BooleanSetting nimbus = new BooleanSetting("\u041d\u0438\u043c\u0431", false);
    // ╨Ъ╤А╤Л╨╗╤М╤П
    public final BooleanSetting wings = new BooleanSetting("\u041a\u0440\u044b\u043b\u044c\u044f", false);
    // ╨Ъ╨╕╤В╨░╨╣╤Б╨║╨░╤П ╤И╨╗╤П╨┐╨░
    public final BooleanSetting chinaHat = new BooleanSetting("\u041a\u0438\u0442\u0430\u0439\u0441\u043a\u0430\u044f \u0448\u043b\u044f\u043f\u0430", false);
    public final BooleanSetting chinaHatFriends = new BooleanSetting("\u041e\u0442\u043e\u0431\u0440\u0430\u0436\u0430\u0442\u044c \u043d\u0430 \u0434\u0440\u0443\u0437\u044c\u044f\u0445", true);
    public final BooleanSetting naruto = new BooleanSetting("\u041d\u0430\u0440\u0443\u0442\u043e \u0431\u0435\u0433", false);

    private final AttributesTail tailRenderer = new AttributesTail(this);
    private final AttributesEars earsRenderer = new AttributesEars(this);
    private final AttributesNeck neckRenderer = new AttributesNeck(this);
    private final AttributesCrystal crystalRenderer = new AttributesCrystal(this);
    private final AttributesNimbus nimbusRenderer = new AttributesNimbus(this);
    private final AttributesWings wingsRenderer = new AttributesWings(this);
    private final AttributesChinaHat chinaHatRenderer = new AttributesChinaHat(this);

    /** ╨Х╨┤╨╕╨╜╤Л╨╣ ╨▒╤Г╤Д╨╡╤А-╨░╨╗╨╗╨╛╨║╨░╤В╨╛╤А ╨╜╨░ ╨▓╤Б╨╡ ╨░╤В╤А╨╕╨▒╤Г╤В╤Л тАФ ╤Н╨║╨╛╨╜╨╛╨╝╨╕╤В ╨┐╨░╨╝╤П╤В╤М. */
    private ByteBufferBuilder sharedAllocator;
    /** ╨Ь╨░╨║╤Б╨╕╨╝╨░╨╗╤М╨╜╨░╤П ╨┤╨╕╤Б╤В╨░╨╜╤Ж╨╕╤П ╨╛╤В╤А╨╕╤Б╨╛╨▓╨║╨╕ ╨░╤В╤А╨╕╨▒╤Г╤В╨╛╨▓, ╨▒╨╗╨╛╨║╨╛╨▓. */
    public static final double MAX_RENDER_DISTANCE = 64.0;
    private static final double NEAR_ALWAYS_SQ = 36.0;   // ╨▒╨╗╨╕╨╢╨╡ 6 ╨▒╨╗╨╛╨║╨╛╨▓ тАФ ╤А╨╕╤Б╤Г╨╡╨╝ ╨▓╤Б╨╡╨│╨┤╨░
    private static final double MAX_RENDER_DISTANCE_SQ = MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE;

    public Attributes() {
        this.addSettings(new Setting[]{this.tail, this.ears, this.neck, this.crystal, this.nimbus, this.wings, this.chinaHat, this.chinaHatFriends, this.naruto});
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (this.tail.get()) {
            this.tailRenderer.onRender3D(event);
        }
        if (this.ears.get()) {
            this.earsRenderer.onRender3D(event);
        }
        if (this.neck.get()) {
            this.neckRenderer.onRender3D(event);
        }
        if (this.crystal.get()) {
            this.crystalRenderer.onRender3D(event);
        }
        if (this.nimbus.get()) {
            this.nimbusRenderer.onRender3D(event);
        }
        if (this.wings.get()) {
            this.wingsRenderer.onRender3D(event);
        }
        // ┬л╨Э╨░╤А╤Г╤В╨╛ ╨▒╨╡╨│┬╗ ╨┐╤А╨╕╨╝╨╡╨╜╤П╨╡╤В╤Б╤П ╨▓ PlayerEntityModelMixin (╨┐╨╛╨╖╨░ ╤А╤Г╨║).
    }

    @Override
    public void onDisable() {
        this.tailRenderer.deactivate();
        this.earsRenderer.deactivate();
        this.neckRenderer.deactivate();
        this.crystalRenderer.deactivate();
        this.nimbusRenderer.deactivate();
        this.wingsRenderer.deactivate();
        this.chinaHatRenderer.deactivate();
        if (this.sharedAllocator != null) {
            try {
                this.sharedAllocator.close();
            } catch (Exception ignored) {
            }
            this.sharedAllocator = null;
        }
        super.onDisable();
    }

    /** ╨Ю╨▒╤Й╨╕╨╣ {@link ByteBufferBuilder} ╨┤╨╗╤П ╨▓╤Б╨╡╤Е ╤А╨╡╨╜╨┤╨╡╤А╨╡╤А╨╛╨▓ ╨░╤В╤А╨╕╨▒╤Г╤В╨╛╨▓ (╨╗╨╡╨╜╨╕╨▓╨░╤П ╨╕╨╜╨╕╤Ж╨╕╨░╨╗╨╕╨╖╨░╤Ж╨╕╤П). */
    public ByteBufferBuilder allocator() {
        if (this.sharedAllocator == null) {
            this.sharedAllocator = new ByteBufferBuilder(262144);
        }
        return this.sharedAllocator;
    }

    /**
     * ╨Ю╤В╤Б╨╡╤З╨╡╨╜╨╕╨╡ ╨┐╨╛ ╨┤╨╕╤Б╤В╨░╨╜╤Ж╨╕╨╕ + ╨│╤А╤Г╨▒╨░╤П ╨┐╤А╨╛╨▓╨╡╤А╨║╨░ ┬л╨▓ ╨┐╨╛╨╗╨╡ ╨╖╤А╨╡╨╜╨╕╤П┬╗.
     * ╨Ш╨│╤А╨╛╨║ ╨▒╨╗╨╕╨╢╨╡ 6 ╨▒╨╗╨╛╨║╨╛╨▓ ╤А╨╕╤Б╤Г╨╡╤В╤Б╤П ╨▓╤Б╨╡╨│╨┤╨░ (╤З╤В╨╛╨▒╤Л ╨╜╨╡ ╨╝╨╕╨│╨░╨╗ ╤Г ╨║╤А╨░╤С╨▓ ╤Н╨║╤А╨░╨╜╨░ ╨▓ 3-╨╝ ╨╗╨╕╤Ж╨╡),
     * ╨┤╨░╨╗╤М╤И╨╡ тАФ ╤В╨╛╨╗╤М╨║╨╛ ╨╡╤Б╨╗╨╕ ╨┐╨╛╨┐╨░╨┤╨░╨╡╤В ╨▓ ╨┐╨╡╤А╨╡╨┤╨╜╨╕╨╣ ╨║╨╛╨╜╤Г╤Б ╨║╨░╨╝╨╡╤А╤Л, ╨╕ ╨╜╨╡ ╨┤╨░╨╗╤М╤И╨╡ {@link #MAX_RENDER_DISTANCE}.
     */
    public boolean shouldRenderFor(Player player, Vec3 cameraPos) {
        Vec3 anchor = new Vec3(player.getX(), player.getY() + player.getBbHeight() + 0.3, player.getZ());
        double distSq = cameraPos.distanceToSqr(anchor);
        if (distSq > MAX_RENDER_DISTANCE_SQ) {
            return false;
        }
        if (distSq < NEAR_ALWAYS_SQ) {
            return true;
        }
        Camera cam = mc.gameRenderer.mainCamera();
        double ry = Math.toRadians(cam.yRot());
        double rp = Math.toRadians(cam.xRot());
        Vec3 forward = new Vec3(
                -Math.sin(ry) * Math.cos(rp),
                -Math.sin(rp),
                Math.cos(ry) * Math.cos(rp));
        Vec3 dir = anchor.subtract(cameraPos).normalize();
        return forward.dot(dir) > 0.15;
    }

    /** ╨Р╨║╤В╨╕╨▓╨╡╨╜ ╨╗╨╕ ┬л╨Э╨░╤А╤Г╤В╨╛ ╨▒╨╡╨│┬╗ тАФ ╨┤╨╗╤П PlayerEntityModelMixin. */
    public boolean isNarutoActive() {
        return this.enable && this.naruto.get();
    }

    /**
     * ╨ж╨▓╨╡╤В ╤В╨╡╨╝╤Л ╨║╨╗╨╕╨╡╨╜╤В╨░ ╨▓ ╤Д╨╛╤А╨╝╨░╤В╨╡ 0xRRGGBB тАФ ╨╕╤Б╨┐╨╛╨╗╤М╨╖╤Г╨╡╤В╤Б╤П ╤Е╨▓╨╛╤Б╤В╨╛╨╝, ╤Г╤И╨║╨░╨╝╨╕ ╨╕ ╤И╨╡╨╡╨╣.
     */
    /** Proxy for the LivingEntityRendererMixin: draws the hat in model space (third person). */
    public void renderChinaHatOnHead(Player player,
                                      com.mojang.blaze3d.vertex.PoseStack matrices,
                                      net.minecraft.client.model.Model<?> model,
                                      net.minecraft.client.renderer.SubmitNodeCollector queue) {
        if (model instanceof net.minecraft.client.model.HumanoidModel<?> biped) {
            this.chinaHatRenderer.renderOnHead(player, matrices, biped, queue);
        }
    }

    public boolean isChinaHatActive() {
        return this.enable && this.chinaHat.get();
    }

    public boolean showChinaHatOnFriends() {
        return this.chinaHatFriends.get();
    }

    public int resolveThemeColor() {
        return ColorUtil.theme(255);
    }
}
