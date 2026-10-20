package vesence.module.impl.visuals;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline.Snippet;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.CameraType;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.render.EventRender3D;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.HueSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.renderengine.render.Renderer2D;
import vesence.utils.render.ColorUtil;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;

/**
 * Полный порт Relevant {@code Tracers} — линии от прицела до игроков.
 * <p>
 * Отличие от Relevant: линии рисуются НЕ через тонкие {@code DEBUG_LINES}
 * (пиксельные, толщиной 1px), а как обращённые к камере квады (billboard
 * strip) с настраиваемой толщиной и additive-блендингом — линии гладкие,
 * не пиксельные. Выбор цвета сделан через режим «Тема/Свой», как в других
 * функциях vesence.
 */
@IModule(name = "Tracers", description = "Линии до игроков и целей", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class Tracers extends Module {

    private static final double MAX_SAFE_COORD = 3.0E7;
    private static final Minecraft MC = Minecraft.getInstance();

    private final SliderSetting height = new SliderSetting("Высота", 0.0, 0.0, 2.0, 0.05, false);
    private final SliderSetting thickness = new SliderSetting("Толщина линий", 1.0, 0.3, 5.0, 0.1, false);
    private final BooleanSetting ignoreNaked = new BooleanSetting("Игнорировать голых", false);
    // Выбор цвета — как в других функциях (Тема клиента / свой цвет).
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Свой");
    private final HueSetting customColor = (HueSetting) new HueSetting("Свой цвет", 40.0F)
            .hidden(() -> !colorMode.is("Свой"));

    // Гладкие (не пиксельные) линии: обращённые к камере квады, additive.
    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET})
                    .withLocation(Identifier.fromNamespaceAndPath("vesence", "tracers_quad_lines"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)                    .withPrimitiveTopology(PrimitiveTopology.QUADS)
                    .withCull(false)
                    .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                    .build()
    );
    private static final RenderType LINE_LAYER = RenderType.create(
            "tracers_quad_lines", RenderSetup.builder(LINE_PIPELINE).createRenderSetup()
    );

    public Tracers() {
        this.addSettings(new Setting[]{height, thickness, ignoreNaked, colorMode, customColor});
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (MC.player == null || MC.level == null || MC.gameRenderer == null) {
            return;
        }
        if (MC.options.getCameraType() == net.minecraft.client.CameraType.THIRD_PERSON_FRONT) {
            return;
        }

        float partialTicks = event.getTickDelta();
        Camera camera = MC.gameRenderer.mainCamera();
        Vec3 cameraPos = camera.position();
        Vec3 start = this.getStartPoint(camera);
        if (!this.isSafe(start)) {
            return;
        }

        int color = this.resolveColor();
        int cr = ColorUtil.red(color);
        int cg = ColorUtil.green(color);
        int cb = ColorUtil.blue(color);

        // Толщина линии в экранных единицах на расстоянии 1 блок от камеры.
        // Ширина каждой вершины масштабируется по её расстоянию до камеры —
        // это даёт постоянную тонкую толщину на экране (как оригинальная
        // тонкая линия, но сглаженная, не пиксельная).
        float baseHalf = (float) (thickness.current * 0.00065);

        PoseStack matrices = event.getMatrixStack();
        Immediate immediate = new Immediate();
        VertexConsumer buffer = immediate.getBuffer(LINE_LAYER);

        matrices.pushPose();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.last().pose();

        for (Player player : MC.level.players()) {
            if (player == null || player == MC.player || player.isRemoved()) {
                continue;
            }
            if (this.ignoreNaked.get() && this.isNaked(player)) {
                continue;
            }
            Vec3 target = this.interpolate(player, partialTicks).add(0.0, height.current, 0.0);
            if (!this.isSafe(target)) {
                continue;
            }

            this.drawQuadLine(buffer, matrix, cameraPos, start, target, baseHalf, cr, cg, cb, 255);
        }

        immediate.draw();
        matrices.popPose();
    }

    /**
     * Рисует тонкую гладкую линию от {@code start} до {@code end} как
     * обращённый к камере квад. Ширина у каждого конца масштабируется по его
     * расстоянию до камеры, поэтому толщина в пикселях постоянна вдоль всей
     * линии (не раздувается у ближнего конца).
     */
    private void drawQuadLine(VertexConsumer buffer, Matrix4f matrix, Vec3 cameraPos,
                              Vec3 start, Vec3 end, float baseHalf, int r, int g, int b, int a) {
        Vec3 dir = end.subtract(start);
        double len = dir.length();
        if (len < 1.0E-4) {
            return;
        }
        Vec3 dirN = dir.scale(1.0 / len);

        // Перпендикуляр «в сторону» для КАЖДОГО конца отдельно — считается от
        // вектора взгляда именно в этой точке, ширина ∝ расстоянию до камеры.
        Vec3 startSide = this.sideVector(dirN, start.subtract(cameraPos), baseHalf);
        Vec3 endSide = this.sideVector(dirN, end.subtract(cameraPos), baseHalf);

        Vec3 s0 = start.subtract(startSide);
        Vec3 s1 = start.add(startSide);
        Vec3 e1 = end.add(endSide);
        Vec3 e0 = end.subtract(endSide);

        buffer.addVertex(matrix, (float) s0.x, (float) s0.y, (float) s0.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float) e0.x, (float) e0.y, (float) e0.z).setColor(r, g, b, a);
    }

    /**
     * Боковой перпендикуляр для одного конца линии. Длина = baseHalf ×
     * расстояние_до_камеры → на экране всегда одинаковая тонкая толщина.
     */
    private Vec3 sideVector(Vec3 dirN, Vec3 toPointFromCam, float baseHalf) {
        double dist = toPointFromCam.length();
        float half = (float) (baseHalf * Math.max(0.05, dist));

        Vec3 side = dirN.cross(toPointFromCam);
        double sideLen = side.length();
        if (sideLen < 1.0E-6) {
            side = dirN.cross(new Vec3(0, 1, 0));
            sideLen = side.length();
            if (sideLen < 1.0E-6) {
                side = new Vec3(1, 0, 0);
                sideLen = 1.0;
            }
        }
        return side.scale(half / sideLen);
    }

    /**
     * Стартовая точка привязана к прицелу (центру экрана): origin камеры +
     * полблока вперёд по camera-forward. Так линии не «ходят» по экрану.
     */
    private Vec3 getStartPoint(Camera camera) {
        Vec3 cameraPos = camera.position();
        Vec3 forward = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        return cameraPos.add(forward.scale(0.5));
    }

    private Vec3 interpolate(Player player, float partialTicks) {
        return new Vec3(
                Mth.lerp(partialTicks, player.xOld, player.getX()),
                Mth.lerp(partialTicks, player.yOld, player.getY()),
                Mth.lerp(partialTicks, player.zOld, player.getZ())
        );
    }

    private boolean isNaked(Player player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    private boolean isSafe(Vec3 vec) {
        return vec != null
                && Double.isFinite(vec.x) && Double.isFinite(vec.y) && Double.isFinite(vec.z)
                && Math.abs(vec.x) <= MAX_SAFE_COORD && Math.abs(vec.y) <= MAX_SAFE_COORD && Math.abs(vec.z) <= MAX_SAFE_COORD;
    }

    private int resolveColor() {
        if (colorMode.is("Свой")) {
            return customColor.getRGB();
        }
        return Renderer2D.ColorUtil.getClientColor();
    }
}
