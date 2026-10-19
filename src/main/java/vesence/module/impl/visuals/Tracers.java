package vesence.module.impl.visuals;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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
    private static final MinecraftClient MC = MinecraftClient.getInstance();

    private final SliderSetting height = new SliderSetting("Высота", 0.0, 0.0, 2.0, 0.05, false);
    private final SliderSetting thickness = new SliderSetting("Толщина линий", 1.0, 0.3, 5.0, 0.1, false);
    private final BooleanSetting ignoreNaked = new BooleanSetting("Игнорировать голых", false);
    // Выбор цвета — как в других функциях (Тема клиента / свой цвет).
    private final ModeSetting colorMode = new ModeSetting("Цвет", "Тема", "Тема", "Свой");
    private final HueSetting customColor = (HueSetting) new HueSetting("Свой цвет", 40.0F)
            .hidden(() -> !colorMode.is("Свой"));

    // Гладкие (не пиксельные) линии: обращённые к камере квады, additive.
    private static final RenderPipeline LINE_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new Snippet[]{RenderPipelines.POSITION_COLOR_SNIPPET})
                    .withLocation(Identifier.of("vesence", "tracers_quad_lines"))
                    .withVertexFormat(VertexFormats.POSITION_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.TRANSLUCENT)
                    .build()
    );
    private static final RenderLayer LINE_LAYER = RenderLayer.of(
            "tracers_quad_lines", RenderSetup.builder(LINE_PIPELINE).expectedBufferSize(8192).translucent().build()
    );

    public Tracers() {
        this.addSettings(new Setting[]{height, thickness, ignoreNaked, colorMode, customColor});
    }

    @EventInit
    public void onRender3D(EventRender3D event) {
        if (MC.player == null || MC.world == null || MC.gameRenderer == null) {
            return;
        }
        if (MC.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            return;
        }

        float partialTicks = event.getTickDelta();
        Camera camera = MC.gameRenderer.getCamera();
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d start = this.getStartPoint(camera);
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

        MatrixStack matrices = event.getMatrixStack();
        Immediate immediate = MC.getBufferBuilders().getEntityVertexConsumers();
        VertexConsumer buffer = immediate.getBuffer(LINE_LAYER);

        matrices.push();
        matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
        Matrix4f matrix = matrices.peek().getPositionMatrix();

        for (PlayerEntity player : MC.world.getPlayers()) {
            if (player == null || player == MC.player || player.isRemoved()) {
                continue;
            }
            if (this.ignoreNaked.get() && this.isNaked(player)) {
                continue;
            }
            Vec3d target = this.interpolate(player, partialTicks).add(0.0, height.current, 0.0);
            if (!this.isSafe(target)) {
                continue;
            }

            this.drawQuadLine(buffer, matrix, cameraPos, start, target, baseHalf, cr, cg, cb, 255);
        }

        immediate.draw();
        matrices.pop();
    }

    /**
     * Рисует тонкую гладкую линию от {@code start} до {@code end} как
     * обращённый к камере квад. Ширина у каждого конца масштабируется по его
     * расстоянию до камеры, поэтому толщина в пикселях постоянна вдоль всей
     * линии (не раздувается у ближнего конца).
     */
    private void drawQuadLine(VertexConsumer buffer, Matrix4f matrix, Vec3d cameraPos,
                              Vec3d start, Vec3d end, float baseHalf, int r, int g, int b, int a) {
        Vec3d dir = end.subtract(start);
        double len = dir.length();
        if (len < 1.0E-4) {
            return;
        }
        Vec3d dirN = dir.multiply(1.0 / len);

        // Перпендикуляр «в сторону» для КАЖДОГО конца отдельно — считается от
        // вектора взгляда именно в этой точке, ширина ∝ расстоянию до камеры.
        Vec3d startSide = this.sideVector(dirN, start.subtract(cameraPos), baseHalf);
        Vec3d endSide = this.sideVector(dirN, end.subtract(cameraPos), baseHalf);

        Vec3d s0 = start.subtract(startSide);
        Vec3d s1 = start.add(startSide);
        Vec3d e1 = end.add(endSide);
        Vec3d e0 = end.subtract(endSide);

        buffer.vertex(matrix, (float) s0.x, (float) s0.y, (float) s0.z).color(r, g, b, a);
        buffer.vertex(matrix, (float) s1.x, (float) s1.y, (float) s1.z).color(r, g, b, a);
        buffer.vertex(matrix, (float) e1.x, (float) e1.y, (float) e1.z).color(r, g, b, a);
        buffer.vertex(matrix, (float) e0.x, (float) e0.y, (float) e0.z).color(r, g, b, a);
    }

    /**
     * Боковой перпендикуляр для одного конца линии. Длина = baseHalf ×
     * расстояние_до_камеры → на экране всегда одинаковая тонкая толщина.
     */
    private Vec3d sideVector(Vec3d dirN, Vec3d toPointFromCam, float baseHalf) {
        double dist = toPointFromCam.length();
        float half = (float) (baseHalf * Math.max(0.05, dist));

        Vec3d side = dirN.crossProduct(toPointFromCam);
        double sideLen = side.length();
        if (sideLen < 1.0E-6) {
            side = dirN.crossProduct(new Vec3d(0, 1, 0));
            sideLen = side.length();
            if (sideLen < 1.0E-6) {
                side = new Vec3d(1, 0, 0);
                sideLen = 1.0;
            }
        }
        return side.multiply(half / sideLen);
    }

    /**
     * Стартовая точка привязана к прицелу (центру экрана): origin камеры +
     * полблока вперёд по camera-forward. Так линии не «ходят» по экрану.
     */
    private Vec3d getStartPoint(Camera camera) {
        Vec3d cameraPos = camera.getCameraPos();
        Vec3d forward = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        return cameraPos.add(forward.multiply(0.5));
    }

    private Vec3d interpolate(PlayerEntity player, float partialTicks) {
        return new Vec3d(
                MathHelper.lerp(partialTicks, player.lastRenderX, player.getX()),
                MathHelper.lerp(partialTicks, player.lastRenderY, player.getY()),
                MathHelper.lerp(partialTicks, player.lastRenderZ, player.getZ())
        );
    }

    private boolean isNaked(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = player.getEquippedStack(slot);
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    private boolean isSafe(Vec3d vec) {
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
