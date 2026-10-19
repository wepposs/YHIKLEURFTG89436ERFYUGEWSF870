package vesence.renderengine.render;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.RenderPipelines;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.Camera;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * SkyShaderRenderer — полный порт Relevant ShaderSky на core-пайплайн 1.21.11.
 *
 * <p>Точный перенос логики Relevant: небо рисуется НА СТАДИИ НЕБА (из миксина
 * {@code SkyRenderingMixin} на {@code renderTopSky} с отменой ванильного неба).
 * Т.к. это происходит ДО отрисовки геометрии мира, земля/деревья потом
 * перекрывают небо естественным образом — не нужна ни маска глубины, ни
 * композит, и НЕТ бага с чернением при взгляде вверх.
 *
 * <p>Рисуем полноэкранный квад выбранного шейдера прямо в цветовой буфер кадра
 * без теста/записи глубины.
 */
@Environment(EnvType.CLIENT)
public final class SkyShaderRenderer {

    public static final String[] SHADER_NAMES = {
            "sky_cosmos", "sky_summer", "sky_clouds", "sky_sunset", "sky_aurora",
            "sky_galaxy", "sky_dawn", "sky_storm", "sky_nebula", "sky_neon",
            "sky_meteor", "sky_inferno", "sky_psychedelic", "sky_rainbow", "sky_moon",
            "sky_eclipse", "sky_twilight", "sky_crystal", "sky_ocean", "sky_volcano",
            "sky_winter", "sky_lava", "sky_prism", "sky_matrix", "sky_starfall",
            "sky_pastel", "sky_glacier", "sky_plasma", "sky_sakura", "sky_void",
            "sky_spectrum", "sky_bloodmoon", "sky_emerald"
    };

    public static final String[] MODE_NAMES = {
            "Космос", "Лето", "Облака", "Закат", "Сияние",
            "Галактика", "Рассвет", "Гроза", "Туманность", "Неон",
            "Метеоры", "Инферно", "Психоделика", "Радуга", "Луна",
            "Затмение", "Сумерки", "Кристалл", "Океан", "Вулкан",
            "Зима", "Лава", "Призма", "Матрица", "Звездопад",
            "Пастель", "Ледник", "Плазма", "Сакура", "Бездна",
            "Спектр", "Кровавая луна", "Изумруд"
    };

    private static final ResourceLocation SKY_VSH = ResourceLocation.of("vesence", "core/sky/sky_fullscreen");

    // Небо рисуется поверх содержимого буфера с учётом собственной альфы, чтобы
    // туман (см. fsh) мог плавно смешиваться. Обычно альфа=1 → полная замена.
    private static final BlendFunction REPLACE_BLEND =
            new BlendFunction(SourceFactor.ONE, DestFactor.ZERO, SourceFactor.ONE, DestFactor.ZERO);

    private static final Vector4f COLOR_MODULATOR = new Vector4f(1f, 1f, 1f, 1f);
    private static final Vector3f MODEL_OFFSET = new Vector3f(0, 0, 0);
    private static final Matrix4f TEXTURE_MATRIX = new Matrix4f();
    private static final int SKY_UBO_SIZE = 48; // 3 x vec4

    private final Map<String, RenderPipeline> skyPipelines = new HashMap<>();
    private GpuBuffer skyUbo;
    private GpuBuffer dummyVertexBuffer;
    private ByteBuffer skyData;
    private boolean initialized = false;
    // Поколение пайплайнов: при перезагрузке ресурсов (F3+T) инкрементируется,
    // новые локации заставляют GPU скомпилировать шейдеры из свежих исходников.
    private int pipelineGeneration = 0;
    private final long startTime = System.currentTimeMillis();

    private static SkyShaderRenderer instance;

    public static SkyShaderRenderer getInstance() {
        if (instance == null) instance = new SkyShaderRenderer();
        return instance;
    }

    private void ensureInitialized() {
        if (initialized) return;
        this.skyData = MemoryUtil.memAlloc(SKY_UBO_SIZE);
        ByteBuffer dummy = MemoryUtil.memAlloc(4);
        dummy.putInt(0);
        dummy.flip();
        this.dummyVertexBuffer = RenderSystem.getDevice().createBuffer(
                () -> "vesence:sky_dummy_vertex", GpuBuffer.USAGE_VERTEX, dummy);
        MemoryUtil.memFree(dummy);
        this.skyUbo = RenderSystem.getDevice().createBuffer(
                () -> "vesence:sky_ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SKY_UBO_SIZE);
        initialized = true;
    }

    private RenderPipeline getSkyPipeline(String shaderName) {
        if (skyPipelines.containsKey(shaderName)) return skyPipelines.get(shaderName);
        try {
            ResourceLocation fsh = ResourceLocation.of("vesence", "core/sky/" + shaderName);
            RenderPipeline p = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                            .withLocation(ResourceLocation.of("vesence", "pipeline/" + shaderName + "_g" + pipelineGeneration))
                            .withVertexShader(SKY_VSH)
                            .withFragmentShader(fsh)
                            .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.DrawMode.TRIANGLES)
                            .withUniform("SkyData", UniformType.UNIFORM_BUFFER)
                            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                            .withDepthWrite(false)
                            .withBlend(REPLACE_BLEND)
                            .withCull(false)
                            .build()
            );
            skyPipelines.put(shaderName, p);
            return p;
        } catch (Exception e) {
            System.err.println("[weppos] Sky pipeline register failed (" + shaderName + "): " + e.getMessage());
            skyPipelines.put(shaderName, null);
            return null;
        }
    }

    /**
     * Вызывается при перезагрузке ресурсов (F3+T): сбрасывает кэш пайплайнов,
     * чтобы шейдеры неба скомпилировались заново из свежих файлов.
     */
    public void reloadPipelines() {
        pipelineGeneration++;
        skyPipelines.clear();
    }

    public String modeToShaderName(String mode) {
        for (int i = 0; i < MODE_NAMES.length; i++) {
            if (MODE_NAMES[i].equals(mode)) return SHADER_NAMES[i];
        }
        return SHADER_NAMES[0];
    }

    /**
     * Рисует кастомное небо прямо в цветовой буфер кадра (стадия неба).
     *
     * @param fogEnabled слить низ неба с цветом тумана
     * @param fogColorRgb 0xRRGGBB
     * @param fogBlend    сила слияния 0..1
     */
    public boolean render(String mode, Camera camera, float speed, float intensity,
                          boolean fogEnabled, int fogColorRgb, float fogBlend) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || camera == null) return false;
        RenderTarget mainFb = mc.getMainRenderTarget();
        if (mainFb == null || mainFb.getColorAttachment() == null) return false;

        int w = mainFb.textureWidth;
        int h = mainFb.textureHeight;
        if (w <= 0 || h <= 0) return false;

        ensureInitialized();
        RenderPipeline skyPipeline = getSkyPipeline(modeToShaderName(mode));
        if (skyPipeline == null) return false;

        float time = (float) (System.currentTimeMillis() - startTime) / 1000.0F * speed;
        float camYaw = camera.getYRot();
        float camPitch = camera.getXRot();
        float fov = mc.options.getFov().getValue().floatValue();

        float fr = ((fogColorRgb >> 16) & 0xFF) / 255.0f;
        float fg = ((fogColorRgb >> 8) & 0xFF) / 255.0f;
        float fb = (fogColorRgb & 0xFF) / 255.0f;

        skyData.clear();
        skyData.putFloat(w).putFloat(h).putFloat(time).putFloat(intensity);
        skyData.putFloat(camYaw).putFloat(camPitch).putFloat(fov).putFloat(0f);
        skyData.putFloat(fr).putFloat(fg).putFloat(fb).putFloat(fogEnabled ? fogBlend : 0f);
        skyData.flip();

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        encoder.writeToBuffer(skyUbo.slice(), skyData);

        GpuBufferSlice dyn = RenderSystem.getDynamicUniforms().write(
                RenderSystem.getModelViewMatrix(), COLOR_MODULATOR, MODEL_OFFSET, TEXTURE_MATRIX);

        try (RenderPass pass = encoder.createRenderPass(() -> "vesence:sky_render",
                mainFb.getColorAttachmentView(), OptionalInt.empty())) {
            pass.setPipeline(skyPipeline);
            pass.setVertexBuffer(0, dummyVertexBuffer);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", dyn);
            pass.setUniform("SkyData", skyUbo);
            pass.draw(0, 6);
        }
        return true;
    }

    public void close() {
        if (skyUbo != null) { skyUbo.close(); skyUbo = null; }
        if (dummyVertexBuffer != null) { dummyVertexBuffer.close(); dummyVertexBuffer = null; }
        if (skyData != null) { MemoryUtil.memFree(skyData); skyData = null; }
        skyPipelines.clear();
        initialized = false;
    }
}
