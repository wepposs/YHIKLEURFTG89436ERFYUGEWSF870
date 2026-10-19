package vesence.hmi.patricles;

import vesence.hmi.LuaTestHMI;
import vesence.hmi.layers.ParticleRenderLayers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.util.Hand;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public class Particle {
    private int prevAge = 0;
    private Consumer<Particle> ticker = null;
    public String lifecycleType;
    public String space;
    public boolean dead = false;
    private boolean isBirth = true;
    private final String particleRenderType;
    private double yawPrev = 0.0;
    private double pitchPrev = 0.0;
    private double particleLifetime = 0.0;
    public double x;
    public double y;
    public double z;
    public double dx;
    public double dy;
    public double dz;
    private double alpha = 255.0;
    private double scale = 0.0;
    public double maxScale = 1.0;
    public double rx;
    public double ry;
    public double rz;
    public double drx;
    public double dry;
    public double drz;
    private boolean gravity;
    public Hand hand;
    private double age = 0.0;
    private Identifier texture;
    private List<Identifier> keyframes = new ArrayList();
    public MatrixStack p_matrices = null;
    Iterator<Identifier> iterator;

    public Particle(boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker) {
        this.particleLifetime = particleLifetime;
        this.gravity = gravity;
        this.lifecycleType = lifecycleType;
        this.particleRenderType = particleRenderType;
        this.space = space;
        this.hand = hand;
        this.ticker = ticker;
        this.alpha = alpha;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.drx = drx;
        this.dry = dry;
        this.drz = drz;
        this.maxScale = maxScale;
        this.texture = texture;
        this.iterator = this.keyframes.iterator();
    }

    public Particle(boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha) {
        this.particleLifetime = particleLifetime;
        this.gravity = gravity;
        this.lifecycleType = lifecycleType;
        this.particleRenderType = particleRenderType;
        this.space = space;
        this.hand = hand;
        this.alpha = alpha;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.drx = drx;
        this.dry = dry;
        this.drz = drz;
        this.maxScale = maxScale;
        this.texture = texture;
        this.iterator = this.keyframes.iterator();
    }

    public Particle(boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, List<Identifier> keyframes, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker) {
        this.particleLifetime = particleLifetime;
        this.gravity = gravity;
        this.lifecycleType = lifecycleType;
        this.particleRenderType = particleRenderType;
        this.space = space;
        this.hand = hand;
        this.ticker = ticker;
        this.x = x;
        this.y = y;
        this.z = z;
        this.alpha = alpha;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.drx = drx;
        this.dry = dry;
        this.drz = drz;
        this.maxScale = maxScale;
        this.texture = texture;
        this.keyframes = keyframes;
        this.iterator = keyframes.iterator();
    }

    public Particle(boolean gravity, double x, double y, double z, double dx, double dy, double dz, double rx, double ry, double rz, double drx, double dry, double drz, double maxScale, Identifier texture, String space, Hand hand, String lifecycleType, String particleRenderType, double particleLifetime, double alpha, Consumer<Particle> ticker, MatrixStack p_matrices) {
        this.particleLifetime = particleLifetime;
        this.gravity = gravity;
        this.lifecycleType = lifecycleType;
        this.particleRenderType = particleRenderType;
        this.space = space;
        this.hand = hand;
        this.ticker = ticker;
        this.x = x;
        this.y = y;
        this.z = z;
        this.alpha = alpha;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.rx = rx;
        this.ry = ry;
        this.rz = rz;
        this.drx = drx;
        this.dry = dry;
        this.drz = drz;
        this.maxScale = maxScale;
        this.texture = texture;
        this.iterator = this.keyframes.iterator();
        this.p_matrices = p_matrices;
    }

    public void tick() {
        if (!this.dead) {
            this.particleLifetime -= (double)(0.1f * LuaTestHMI.deltaTime * 30.0f);
            this.particleLifetime = Math.clamp(this.particleLifetime, 0.0, 9999.0);
            this.age += (double)(0.1f * LuaTestHMI.deltaTime * 30.0f);
            this.x += this.dx * (double)LuaTestHMI.deltaTime * 30.0;
            this.y += this.dy * (double)LuaTestHMI.deltaTime * 30.0;
            this.z += this.dz * (double)LuaTestHMI.deltaTime * 30.0;
            this.dx *= Math.pow(0.9, LuaTestHMI.deltaTime * 30.0f);
            this.dy *= Math.pow(0.9, LuaTestHMI.deltaTime * 30.0f);
            this.dz *= Math.pow(0.9, LuaTestHMI.deltaTime * 30.0f);
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            double yaw = player.getYaw();
            double radians = Math.toRadians(yaw);
            double forwardX = -Math.sin(radians);
            double forwardZ = Math.cos(radians);
            Vec3d horizontalVelocity = player.getVelocity();
            double dotProduct = horizontalVelocity.x * forwardX + horizontalVelocity.z * forwardZ;
            double crossProduct = player.getVelocity().getHorizontal().x * forwardZ - horizontalVelocity.z * forwardX;
            if (this.gravity) {
                this.dy -= 0.01 * (double)LuaTestHMI.deltaTime * 30.0;
            }
            this.rx += this.drx * (double)LuaTestHMI.deltaTime * 30.0;
            this.ry += this.dry * (double)LuaTestHMI.deltaTime * 30.0;
            this.rz += this.drz * (double)LuaTestHMI.deltaTime * 30.0;
            if (!this.keyframes.isEmpty() && this.iterator.hasNext() && MinecraftClient.getInstance().player.age % 4 == 0 && player.age != this.prevAge) {
                this.texture = (Identifier)this.iterator.next();
            }
            if (this.lifecycleType == "SCALE") {
                if (!this.isBirth && this.particleLifetime == 0.0) {
                    this.scale -= 0.03 * (double)LuaTestHMI.deltaTime * 30.0;
                    if (this.scale <= 0.0) {
                        this.dead = true;
                    }
                } else {
                    this.scale += 0.07 * (double)LuaTestHMI.deltaTime * 30.0;
                    if (this.scale >= this.maxScale) {
                        this.scale = this.maxScale;
                        this.isBirth = false;
                    }
                }
            } else if (this.lifecycleType == "OPACITY") {
                if (!this.isBirth && this.particleLifetime == 0.0) {
                    this.alpha -= (double)(10.0f * LuaTestHMI.deltaTime * 30.0f);
                    if (this.alpha <= 0.0) {
                        this.dead = true;
                        this.alpha = 0.0;
                    }
                } else {
                    this.scale = this.maxScale;
                    this.isBirth = false;
                    this.alpha = 255.0;
                }
            } else if (this.lifecycleType == "SPAWN") {
                if (!this.isBirth && this.particleLifetime == 0.0) {
                    this.dead = true;
                } else {
                    this.scale = this.maxScale;
                    this.isBirth = false;
                }
            } else if (this.lifecycleType == "KEYFRAME") {
                if (!this.isBirth && !this.iterator.hasNext()) {
                    this.dead = true;
                } else {
                    this.scale = this.maxScale;
                    this.isBirth = false;
                }
            }
            if (this.ticker != null) {
                this.ticker.accept(this);
            }
            this.prevAge = player.age;
        }
    }

    @SuppressWarnings("StringEquality")
    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, AbstractClientPlayerEntity player) {
        if (this.dead) {
            return;
        }
        matrices.push();
        matrices.translate(this.x, this.y, this.z);
        matrices.scale((float)this.scale, (float)this.scale, (float)this.scale);

        // Выбор слоя соответствует оригиналу HMI, адаптировано под фабрики 1.21.11.
        RenderLayer layer;
        if (this.particleRenderType == "ADDITIVE") {
            layer = ParticleRenderLayers.getAdditiveRenderLayer(this.texture);
        } else if (this.particleRenderType == "TRANSLUCENT") {
            layer = ParticleRenderLayers.getSolidRenderLayer(this.texture);
        } else if (this.particleRenderType == "CUTOUT_L") {
            layer = RenderLayers.entitySmoothCutout(this.texture);
        } else { // TRANSLUCENT_L и значение по умолчанию
            layer = RenderLayers.entityTranslucent(this.texture);
        }

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Vector3f translation = new Vector3f();
        Vector3f scale = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        matrix.getTranslation(translation);
        matrix.getUnnormalizedRotation(rotation);
        matrix.getScale(scale);
        matrix.identity().translate((Vector3fc)translation).scale((Vector3fc)scale).rotate((Quaternionfc)MinecraftClient.getInstance().gameRenderer.getCamera().getRotation());
        matrix.rotate((Quaternionfc)new Quaternionf().rotateX((float)(this.rx * 0.01745329238474369)));
        matrix.rotate((Quaternionfc)new Quaternionf().rotateY((float)(this.ry * 0.01745329238474369)));
        matrix.rotate((Quaternionfc)new Quaternionf().rotateZ((float)(this.rz * 0.01745329238474369)));
        Matrix3f normalMatrix = matrices.peek().getNormalMatrix();
        int renderLight = light;
        if (this.particleRenderType == "CUTOUT_L") {
            renderLight = LightmapTextureManager.pack(15, 15);
        }
        double size = 0.5;
        double halfSize = size / 2.0;
        Vector3f normal = new Vector3f(0.0f, 0.0f, 1.0f);
        normal.mul((Matrix3fc)normalMatrix);

        int alpha = (int) Math.clamp((long)((int)this.alpha), 0, 255);
        int finalLight = renderLight;
        final Matrix4f finalMatrix = new Matrix4f(matrix);
        // В 1.21.11 рендер идёт через очередь команд: submitCustom выдаёт VertexConsumer.
        queue.submitCustom(matrices, layer, (entry, vertexConsumer) -> {
            vertexConsumer.vertex(finalMatrix, (float)halfSize, (float)halfSize, 0.0f).color(255, 255, 255, alpha).texture(1.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(finalLight).normal(normal.x, normal.y, normal.z);
            vertexConsumer.vertex(finalMatrix, (float)(-halfSize), (float)halfSize, 0.0f).color(255, 255, 255, alpha).texture(0.0f, 0.0f).overlay(OverlayTexture.DEFAULT_UV).light(finalLight).normal(normal.x, normal.y, normal.z);
            vertexConsumer.vertex(finalMatrix, (float)(-halfSize), (float)(-halfSize), 0.0f).color(255, 255, 255, alpha).texture(0.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(finalLight).normal(normal.x, normal.y, normal.z);
            vertexConsumer.vertex(finalMatrix, (float)halfSize, (float)(-halfSize), 0.0f).color(255, 255, 255, alpha).texture(1.0f, 1.0f).overlay(OverlayTexture.DEFAULT_UV).light(finalLight).normal(normal.x, normal.y, normal.z);
        });
        matrices.pop();
    }
}

