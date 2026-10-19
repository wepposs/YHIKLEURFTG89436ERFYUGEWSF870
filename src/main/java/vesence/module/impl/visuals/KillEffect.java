package vesence.module.impl.visuals;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat.DrawMode;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.render.MultiBufferSource;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.event.player.AttackEvent;
import vesence.event.render.EventRender3D;
import vesence.event.render.EventScreen;
import vesence.module.impl.combat.AttackAura;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.api.setting.impl.SliderSetting;
import vesence.utils.friends.FriendStorage;
import vesence.utils.render.ColorUtil;

/**
 * KillEffect — эффекты при исчезновении цели. Несколько режимов:
 *   • Матрица — расширяющаяся сканирующая волна + виньетка + частицы + звуки
 *     (порт rich.modules FragEffect, GPU depth-scan через WorldRendererMixin);
 *   • Default — столб света;
 *   • Молния — зигзаг-болт;
 *   • Частицы — взрыв частиц (с гравитацией/без).
 * Default/Молния/Частицы перенесены из RelevantPremiumpp4 (рендер адаптирован
 * под пайплайн-систему 1.21.11: общий GLOW_LAYER + VertexConsumer).
 *
 * Детект смерти общий и надёжный (порт из RelevantPremiumpp4): каждую цель,
 * которую атаковали/держит аура, запоминаем и обновляем её позицию, эффект
 * запускается при удалении/смерти сущности — поэтому работает и на мобах.
 */
@IModule(name = "KillEffect", description = "Эффект при исчезновении цели", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class KillEffect extends Module {

    public static KillEffect INSTANCE;

    private static final ResourceLocation VIGNETTE_TEXTURE = ResourceLocation.of("vesence", "images/world/lightarroundscreen.png");
    private static final long BASE_EFFECT_DURATION_MS = 5200L;
    private static final double SPAWN_MAX_DISTANCE = 64.0;
    private static final Random random = new Random();

    // ── Charm sounds (Матрица) ──
    private static final SoundEvent FRAG_EFFECT_ECHO_MAIN = SoundEvent.of(ResourceLocation.of("vesence", "frag_effect_echo_main"));
    private static final SoundEvent FRAG_EFFECT_KNOCK_MAIN = SoundEvent.of(ResourceLocation.of("vesence", "frag_effect_knock_main"));
    private static final SoundEvent FRAG_EFFECT_PULSE = SoundEvent.of(ResourceLocation.of("vesence", "frag_effect_pulse"));
    private static final SoundEvent FRAG_EFFECT_SPARKS_COLLISION = SoundEvent.of(ResourceLocation.of("vesence", "frag_effect_sparks_collision"));

    // ── Settings ──
    public final ModeSetting mode = new ModeSetting("Режим", "Матрица", "Матрица", "Default", "Молния", "Частицы");
    public final MultiBooleanSetting effectTargets = new MultiBooleanSetting("Цели эффекта",
            new BooleanSetting("Игроки", true),
            new BooleanSetting("Друзья", true),
            new BooleanSetting("Мобы", true),
            new BooleanSetting("Животные", true));
    public final BooleanSetting gravity = new BooleanSetting("Гравитация", true).hidden(() -> !this.mode.is("Частицы"));
    public final SliderSetting speed = new SliderSetting("Скорость", 100.0, 25.0, 200.0, 1.0).hidden(() -> !this.mode.is("Матрица"));
    public final BooleanSetting screenOverlay = new BooleanSetting("Экранная картинка", true).hidden(() -> !this.mode.is("Матрица"));
    public final BooleanSetting scanlines = new BooleanSetting("Сканлайны", true).hidden(() -> !this.mode.is("Матрица"));

    // ── State (Матрица) ──
    private boolean animate;
    private float activeEffectSpeedMultiplier = 1.0f;
    private long effectStartTime;
    private final List<ScanWave> waves = new CopyOnWriteArrayList<>();
    private final List<FragParticle> fragParticles = new CopyOnWriteArrayList<>();
    private final List<PendingSound> pendingSounds = new ArrayList<>();
    private vesence.renderengine.render.pipeline.KillEffectScanPipeline scanPipeline;

    // ── State (Default / Молния / Частицы) ──
    private final List<DeathEffect> lightningEffects = new CopyOnWriteArrayList<>();
    private final List<Particle> particles = new CopyOnWriteArrayList<>();
    private final List<ActiveEffect> defaultEffects = new CopyOnWriteArrayList<>();

    // ── Общий детект смерти ──
    private final Map<Entity, Tracked> tracked = new IdentityHashMap<>();

    private ByteBufferBuilder killEffectAlloc = null;

    // ── Render pipeline (glow-биллборды, аддитивный blend) ──
    private static final RenderPipeline GLOW_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.POSITION_TEX_COLOR_SNIPPET})
                    .withLocation(ResourceLocation.of("vesence", "pipeline/world/killeffect_glow"))
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEXTURE_COLOR, DrawMode.QUADS)
                    .withCull(false)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withBlend(BlendFunction.LIGHTNING)
                    .build());
    private static final ResourceLocation GLOW_TEX = ResourceLocation.of("vesence", "textures/targetesp/bloom.png");
    private static final RenderType GLOW_LAYER = RenderType.of(
            "vesence_killeffect_frag_glow",
            RenderSetup.builder(GLOW_PIPELINE).expectedBufferSize(524288).translucent()
                    .texture("Sampler0", GLOW_TEX).build());

    public KillEffect() {
        INSTANCE = this;
        this.addSettings(new Setting[]{this.mode, this.effectTargets, this.gravity, this.speed, this.screenOverlay, this.scanlines});
    }

    @Override
    public void onDisable() {
        resetState();
        if (this.scanPipeline != null) {
            try { this.scanPipeline.close(); } catch (Exception ignored) {}
            this.scanPipeline = null;
        }
        if (killEffectAlloc != null) { killEffectAlloc.close(); killEffectAlloc = null; }
        super.onDisable();
    }

    // ═══ Event: атака — запоминаем цель ══════════════════════════════════════
    @EventInit
    public void onAttack(AttackEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        if (!target.isAlive() || target.getHealth() <= 0.0f || !shouldTriggerFor(target)) return;
        this.tracked.put(target, new Tracked(target.getEntityPos(), target.getEyePosition()));
    }

    // ═══ Event: тик — трекинг цели Aura + детект смерти + частицы/звуки ══════
    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.level == null) {
            resetState();
            return;
        }

        LivingEntity auraTarget = AttackAura.target;
        if (auraTarget != null && auraTarget.isAlive() && auraTarget.getHealth() > 0.0f && shouldTriggerFor(auraTarget)) {
            this.tracked.put(auraTarget, new Tracked(auraTarget.getEntityPos(), auraTarget.getEyePosition()));
        }

        detectDeaths();
        tickParticles();
        tickSounds();
    }

    /** Обновляем позицию живых целей, при удалении/смерти — запускаем эффект. */
    private void detectDeaths() {
        long now = System.currentTimeMillis();
        Vec3 playerPos = mc.player.getEntityPos();
        Iterator<Map.Entry<Entity, Tracked>> it = this.tracked.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Entity, Tracked> entry = it.next();
            Entity entity = entry.getKey();
            Tracked t = entry.getValue();
            if (entity != mc.player && entity.isAlive() && !entity.isRemoved()) {
                t.feet = entity.getEntityPos();
                t.eye = entity.getEyePosition();
            } else {
                if (t.feet.distanceTo(playerPos) <= SPAWN_MAX_DISTANCE) {
                    spawnEffect(entity, t.feet, t.eye, now);
                }
                it.remove();
            }
        }
    }

    // ═══ Event: 3D рендер ════════════════════════════════════════════════════
    @EventInit
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;

        boolean hasGlow = !this.fragParticles.isEmpty() || !this.lightningEffects.isEmpty()
                || !this.particles.isEmpty() || !this.defaultEffects.isEmpty();
        if (!hasGlow) return;

        PoseStack ms = event.getMatrixStack();
        Vec3 cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        float partialTicks = event.getTickDelta();
        long now = System.currentTimeMillis();
        int accent = accentColor();

        if (killEffectAlloc == null) { killEffectAlloc = new ByteBufferBuilder(524288); }
        MultiBufferSource.Immediate immediate = MultiBufferSource.immediate(killEffectAlloc);
        VertexConsumer buffer = immediate.getBuffer(GLOW_LAYER);

        // Матрица — белые искры взрыва.
        for (FragParticle p : this.fragParticles) {
            p.render(buffer, ms, cameraPos, partialTicks);
        }

        // Молния.
        for (DeathEffect effect : this.lightningEffects) {
            effect.render(buffer, ms, cameraPos);
        }
        this.lightningEffects.removeIf(DeathEffect::isFinished);

        // Частицы.
        for (Particle particle : this.particles) {
            if (!particle.isDead()) {
                particle.update();
                particle.render(buffer, ms, cameraPos);
            }
        }
        this.particles.removeIf(Particle::isDead);

        // Default — столб света.
        Iterator<ActiveEffect> defIt = this.defaultEffects.iterator();
        while (defIt.hasNext()) {
            ActiveEffect effect = defIt.next();
            float progress = (float) (now - effect.startTime) / 1500.0f;
            if (progress >= 1.0f) {
                defIt.remove();
            } else {
                renderDefault(buffer, ms, effect, cameraPos, progress, accent);
            }
        }

        immediate.draw();
    }

    /**
     * GPU-рендер сканирующих волн (Матрица) поверх мира. Вызывается из
     * {@code WorldRendererMixin}. В других режимах {@link #waves} пуст — no-op.
     */
    public void renderScanWaves(org.joml.Matrix4f projection, org.joml.Matrix4f view, Vec3 cameraPos) {
        if (!this.enable || this.waves.isEmpty()) return;
        if (mc.player == null || mc.level == null) return;

        if (this.scanPipeline == null) {
            this.scanPipeline = new vesence.renderengine.render.pipeline.KillEffectScanPipeline();
        }

        int accent = accentColor();
        try {
            for (ScanWave wave : this.waves) {
                wave.renderGpu(this.scanPipeline, projection, view, cameraPos, accent, this.scanlines.get());
            }
            this.waves.removeIf(ScanWave::isFinished);
        } catch (Exception ignored) {
        }
    }

    // ═══ Event: 2D рендер — виньетка (Матрица) ═══════════════════════════════
    @EventInit
    public void onRender2D(EventScreen event) {
        if (!this.mode.is("Матрица") || !this.screenOverlay.get()) return;
        float effectProgress = effectProgress();
        if (effectProgress <= 0.0f) return;
        float alphaProgress = effectEnvelope(380L, 1100L);
        if (alphaProgress <= 0.0f) return;

        float scaleAnimation = quintOut(effectProgress);
        float scaleProgress = Math.min((scaleAnimation > 0.5f ? 1.0f - scaleAnimation : scaleAnimation) * 4.25f, 1.0f);
        float width = event.viewportWidth();
        float height = event.viewportHeight();
        float extendX = width * 0.5f * (1.0f - scaleProgress);
        float extendY = height * 0.5f * (1.0f - scaleProgress);

        int baseColor = lighten(accentColor(), 0.22f);
        int color = ColorUtil.interpolate(baseColor, 0xFFFFFFFF, effectProgress);
        int alpha = Math.round(80.0f * alphaProgress);
        int tint = (alpha << 24) | (color & 0x00FFFFFF);

        event.renderer().drawImage(VIGNETTE_TEXTURE, -extendX, -extendY, width + extendX * 2.0f, height + extendY * 2.0f, tint);
    }

    // ═══ Kill dispatch ═══════════════════════════════════════════════════════
    private void spawnEffect(Entity entity, Vec3 feet, Vec3 eye, long now) {
        if (!(entity instanceof LivingEntity living) || !matchesEffectTarget(living)) return;
        int accent = accentColor();
        switch (this.mode.get()) {
            case "Молния" -> this.lightningEffects.add(new DeathEffect(feet, accent));
            case "Частицы" -> this.spawnParticles(living, feet, accent);
            case "Default" -> this.defaultEffects.add(new ActiveEffect(feet, now));
            default -> this.spawnMatrix(eye); // "Матрица"
        }
    }

    // ═══ Матрица (FragEffect) ════════════════════════════════════════════════
    private void spawnMatrix(Vec3 killPosition) {
        float effectSpeed = speedMultiplier();
        restartEffect(effectSpeed);
        this.waves.add(new ScanWave(killPosition, effectSpeed));
        spawnBurst(killPosition, 480, 900, 1.9f, 96, 0.012f);
        scheduleCharmSounds();
    }

    private void scheduleCharmSounds() {
        long offset = scaleDuration(150L);
        scheduleSound(FRAG_EFFECT_PULSE, 1.0f, 0L);
        scheduleSound(FRAG_EFFECT_KNOCK_MAIN, 1.0f, offset);
        scheduleSound(FRAG_EFFECT_SPARKS_COLLISION, 0.2f, offset * 2L);
        scheduleSound(FRAG_EFFECT_ECHO_MAIN, 0.6f, offset * 3L);
    }

    private void restartEffect(float effectSpeedMultiplier) {
        this.animate = true;
        this.activeEffectSpeedMultiplier = Mth.clamp(effectSpeedMultiplier, 0.25f, 2.0f);
        this.effectStartTime = System.currentTimeMillis();
    }

    // ═══ Target filtering ════════════════════════════════════════════════════
    private boolean shouldTriggerFor(LivingEntity entity) {
        return entity != null && entity != mc.player && entity.isAlive()
                && entity.getHealth() > 0.0f && matchesEffectTarget(entity);
    }

    private boolean matchesEffectTarget(LivingEntity entity) {
        if (entity == null || entity == mc.player) return false;
        if (entity instanceof Player player) {
            if (FriendStorage.isFriend(player.getName().getString())) {
                return this.effectTargets.get("Друзья");
            }
            return this.effectTargets.get("Игроки");
        }
        if (entity instanceof AnimalEntity) {
            return this.effectTargets.get("Животные");
        }
        if (entity instanceof MobEntity) {
            return this.effectTargets.get("Мобы");
        }
        return false;
    }

    // ═══ Effect envelope math (Матрица) ══════════════════════════════════════
    private float effectProgress() {
        long duration = effectDurationMs();
        float progress = this.animate ? Math.min((float) (System.currentTimeMillis() - effectStartTime) / (float) duration, 1.0f) : 0.0f;
        if (progress >= 1.0f) this.animate = false;
        return progress;
    }

    private float effectEnvelope(long attackMs, long releaseMs) {
        if (!this.animate) return 0.0f;
        long duration = effectDurationMs();
        long elapsed = Math.min(System.currentTimeMillis() - effectStartTime, duration);
        if (elapsed <= 0L) return 0.0f;
        long attack = Mth.clamp(scaleDuration(attackMs), 1L, duration);
        long release = Mth.clamp(scaleDuration(releaseMs), 1L, duration);
        long releaseStart = Math.max(duration - release, attack);
        if (elapsed < attack) return sineOut((float) elapsed / (float) attack);
        if (elapsed >= releaseStart) {
            float rp = Mth.clamp((float) (elapsed - releaseStart) / (float) release, 0.0f, 1.0f);
            return 1.0f - sineInOut(rp);
        }
        return 1.0f;
    }

    private void resetState() {
        this.animate = false;
        this.activeEffectSpeedMultiplier = speedMultiplier();
        this.pendingSounds.clear();
        this.tracked.clear();
        this.fragParticles.clear();
        this.waves.clear();
        this.lightningEffects.clear();
        this.particles.clear();
        this.defaultEffects.clear();
    }

    private int accentColor() {
        int c = ColorUtil.fade();
        if ((c >> 24 & 0xFF) == 0) c |= 0xFF000000;
        return c;
    }

    private float speedMultiplier() {
        return Mth.clamp((float) (double) this.speed.get() / 100.0f, 0.25f, 2.0f);
    }

    private long effectDurationMs() {
        return scaleDuration(BASE_EFFECT_DURATION_MS);
    }

    private long scaleDuration(long durationMs) {
        if (durationMs <= 0L) return 0L;
        return Math.max(1L, Math.round(durationMs / Mth.clamp(this.activeEffectSpeedMultiplier, 0.25f, 2.0f)));
    }

    // ═══ Sounds ══════════════════════════════════════════════════════════════
    private void scheduleSound(SoundEvent sound, float volume, long delayMs) {
        if (sound == null) return;
        if (delayMs <= 0L) {
            playSound(sound, volume);
            return;
        }
        this.pendingSounds.add(new PendingSound(sound, volume, System.currentTimeMillis() + delayMs));
    }

    private void tickSounds() {
        long now = System.currentTimeMillis();
        Iterator<PendingSound> it = this.pendingSounds.iterator();
        while (it.hasNext()) {
            PendingSound s = it.next();
            if (now < s.playAt) continue;
            playSound(s.sound, s.volume);
            it.remove();
        }
    }

    private void playSound(SoundEvent sound, float volume) {
        if (mc.getSoundManager() == null) return;
        SoundInstance instance = PositionedSoundInstance.ui(sound, 1.0f, volume);
        mc.getSoundManager().play(instance);
    }

    // ═══ FragParticles (Матрица) ═════════════════════════════════════════════
    private void spawnBurst(Vec3 center, int lifetimeMin, int lifetimeMax, float maxFlight, int count, float gravity) {
        float maxXz = maxFlight / (lifetimeMin / 50.0f) * 1.12f;
        float maxY = maxFlight / (lifetimeMin / 50.0f) * 0.31f;
        for (int i = 0; i < count; i++) {
            this.fragParticles.add(new FragParticle(center, lifetimeMin + ThreadLocalRandom.current().nextInt(lifetimeMax - lifetimeMin), maxXz, maxY, gravity));
        }
    }

    private void tickParticles() {
        Iterator<FragParticle> it = this.fragParticles.iterator();
        while (it.hasNext()) {
            FragParticle p = it.next();
            p.tick();
            if (p.isExpired()) it.remove();
        }
    }

    // ═══ Default — столб света (порт RelevantPremiumpp4) ═════════════════════
    private void renderDefault(VertexConsumer buffer, PoseStack matrices, ActiveEffect effect, Vec3 cameraPos, float progress, int color) {
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        float globalAlpha = progress < 0.15f ? progress / 0.15f : (progress > 0.75f ? (1.0f - progress) / 0.25f : 1.0f);

        for (int i = 0; i < 40; i++) {
            float t = i / 40.0f;
            float y = t * 4.0f;
            float radius = Mth.sin((float) (Math.PI * t));
            float sliceAlpha = (1.0f - Math.abs(2.0f * t - 1.0f) * 0.25f) * globalAlpha;
            Vec3 pos = effect.position.add(0.0, y, 0.0);
            drawGlow(buffer, matrices, cameraPos, pos, radius * 2.1f, r, g, b, sliceAlpha * 0.22f);
            drawGlow(buffer, matrices, cameraPos, pos, radius * 1.15f, r, g, b, sliceAlpha * 0.48f);
            drawGlow(buffer, matrices, cameraPos, pos, radius * 0.55f, r, g, b, sliceAlpha * 0.85f);
        }

        for (int i = 0; i < 10; i++) {
            float t = i / 10.0f;
            float spread = 1.0f - t;
            float bottomRadius = 3.6f * spread;
            float bottomAlpha = spread * spread * globalAlpha * 0.38f;
            Vec3 bPos = effect.position.add(0.0, t * 0.45f, 0.0);
            drawGlow(buffer, matrices, cameraPos, bPos, bottomRadius, r, g, b, bottomAlpha);
            drawGlow(buffer, matrices, cameraPos, bPos, bottomRadius * 0.35f, r, g, b, bottomAlpha * 1.7f);
        }
    }

    private void drawGlow(VertexConsumer buffer, PoseStack matrices, Vec3 cameraPos, Vec3 position, float size, int r, int g, int b, float alphaMul) {
        if (alphaMul <= 0.01f) return;
        matrices.pushPose();
        matrices.translate(position.x - cameraPos.x, position.y - cameraPos.y, position.z - cameraPos.z);
        matrices.multiply(mc.gameRenderer.getCamera().getRotation());
        appendQuadRGBA(buffer, matrices.last().getPositionMatrix(), size, r, g, b, alphaMul);
        matrices.popPose();
    }

    // ═══ Частицы — взрыв (порт RelevantPremiumpp4/zenith) ════════════════════
    private void spawnParticles(LivingEntity entity, Vec3 pos, int color) {
        float width = entity != null ? entity.getWidth() : 0.6f;
        float height = entity != null ? entity.getHeight() : 1.8f;
        float yaw = entity != null ? (float) Math.toRadians(-entity.getYRot() + 90.0f) : 0.0f;
        boolean hasGravity = this.gravity.get();
        int count = 194;
        float hOffset = height - 0.2f;
        float spread = width * 0.4f;

        spawnSphere(pos.add(0.0, hOffset, 0.0), spread, count / 10, color, hasGravity);
        spawnBody(pos, width * 0.4f, height * 0.85f, width * 0.2f, yaw, count / 4, color, hasGravity);

        for (int i = -1; i <= 1; i += 2) {
            Vec3 armPos = new Vec3(Math.sin(yaw) * width * 0.5 * i, height * 0.75f, Math.cos(yaw) * width * 0.5 * i);
            spawnLimb(pos.add(armPos), width * 0.4f, width * 0.15f, count / 8, color, hasGravity);
            Vec3 legPos = new Vec3(Math.sin(yaw) * width * 0.15 * i, height * 0.4f, Math.cos(yaw) * width * 0.15 * i);
            spawnLimb(pos.add(legPos), height * 0.45f, width * 0.15f, count / 6, color, hasGravity);
        }
    }

    private void spawnSphere(Vec3 pos, float radius, int count, int color, boolean hasGravity) {
        for (int i = 0; i < count; ++i) {
            float angle1 = random.nextFloat() * (float) Math.PI * 2.0f;
            float angle2 = (float) Math.acos(2.0f * random.nextFloat() - 1.0f);
            float r = radius * (float) Math.cbrt(random.nextFloat());
            float px = r * (float) (Math.sin(angle2) * Math.cos(angle1));
            float py = r * (float) (Math.sin(angle2) * Math.sin(angle1));
            float pz = r * (float) Math.cos(angle2);
            this.particles.add(new Particle(pos, px, py, pz, color, hasGravity));
        }
    }

    private void spawnBody(Vec3 pos, float radiusX, float height, float spread, float yaw, int count, int color, boolean hasGravity) {
        for (int i = 0; i < count; ++i) {
            float ox = (random.nextFloat() - 0.5f) * radiusX * 2.0f;
            float oy = random.nextFloat() * height;
            float oz = (random.nextFloat() - 0.5f) * spread * 2.0f;
            float px = (float) ((double) ox * Math.cos(yaw) - (double) oz * Math.sin(yaw));
            float pz = (float) ((double) ox * Math.sin(yaw) + (double) oz * Math.cos(yaw));
            this.particles.add(new Particle(pos, px, oy, pz, color, hasGravity));
        }
    }

    private void spawnLimb(Vec3 pos, float height, float radius, int count, int color, boolean hasGravity) {
        for (int i = 0; i < count; ++i) {
            float oy = -random.nextFloat() * height;
            float ox = (random.nextFloat() - 0.5f) * radius * 2.0f;
            float oz = (random.nextFloat() - 0.5f) * radius * 2.0f;
            this.particles.add(new Particle(pos, ox, oy, oz, color, hasGravity));
        }
    }

    // ═══ Easings ═════════════════════════════════════════════════════════════
    private static float quintOut(float v) {
        float t = Mth.clamp(v, 0.0f, 1.0f);
        return 1.0f - (float) Math.pow(1.0f - t, 5.0);
    }

    private static float expoInOut(float v) {
        float t = Mth.clamp(v, 0.0f, 1.0f);
        if (t == 0.0f) return 0.0f;
        if (t == 1.0f) return 1.0f;
        return t < 0.5f ? (float) Math.pow(2.0, 20.0 * t - 10.0) / 2.0f
                : (2.0f - (float) Math.pow(2.0, -20.0 * t + 10.0)) / 2.0f;
    }

    private static float sineOut(float v) {
        float t = Mth.clamp(v, 0.0f, 1.0f);
        return (float) Math.sin(t * Math.PI / 2.0);
    }

    private static float sineInOut(float v) {
        float t = Mth.clamp(v, 0.0f, 1.0f);
        return (float) (-(Math.cos(Math.PI * t) - 1.0) / 2.0);
    }

    private static float bounceOut(float v) {
        float t = Mth.clamp(v, 0.0f, 1.0f);
        float n1 = 7.5625f, d1 = 2.75f;
        if (t < 1.0f / d1) return n1 * t * t;
        if (t < 2.0f / d1) { t -= 1.5f / d1; return n1 * t * t + 0.75f; }
        if (t < 2.5f / d1) { t -= 2.25f / d1; return n1 * t * t + 0.9375f; }
        t -= 2.625f / d1;
        return n1 * t * t + 0.984375f;
    }

    private static float bounceIn(float v) {
        return 1.0f - bounceOut(1.0f - Mth.clamp(v, 0.0f, 1.0f));
    }

    private static int lighten(int color, float amount) {
        float c = Mth.clamp(amount, 0.0f, 1.0f);
        int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF, a = color >> 24 & 0xFF;
        r = (int) Mth.clamp(r + (255 - r) * c, 0, 255);
        g = (int) Mth.clamp(g + (255 - g) * c, 0, 255);
        b = (int) Mth.clamp(b + (255 - b) * c, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // ═══ Общие glow-квады ════════════════════════════════════════════════════
    private static void appendQuadRGBA(VertexConsumer buffer, Matrix4f matrix, float size, int r, int g, int b, float alphaMul) {
        float half = size * 0.5f;
        int a = (int) Mth.clamp(255.0f * alphaMul, 0, 255);
        if (a <= 1) return;
        buffer.vertex(matrix, -half, -half, 0.0f).texture(0.0f, 1.0f).color(r, g, b, a);
        buffer.vertex(matrix, -half, half, 0.0f).texture(0.0f, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, half, half, 0.0f).texture(1.0f, 0.0f).color(r, g, b, a);
        buffer.vertex(matrix, half, -half, 0.0f).texture(1.0f, 1.0f).color(r, g, b, a);
    }

    // ═══ ScanWave (Матрица) ══════════════════════════════════════════════════
    private static final class ScanWave {
        private final Vec3 center;
        private final long startedAt = System.currentTimeMillis();
        private final float growthDuration;

        ScanWave(Vec3 center, float speedMultiplier) {
            this.center = center;
            this.growthDuration = Math.max(280.0f, 5600.0f / Mth.clamp(speedMultiplier, 0.25f, 2.0f));
        }

        boolean isFinished() {
            return (System.currentTimeMillis() - startedAt) >= growthDuration;
        }

        private float timeProgress(float duration) {
            return Mth.clamp((float) (System.currentTimeMillis() - startedAt) / duration, 0.0f, 1.0f);
        }

        private static float waveFn(float v) {
            v = Mth.clamp(v, 0.0f, 1.0f);
            return v > 0.5f ? (1.0f - v) * 2.0f : v * 2.0f;
        }

        private float viewDistance() {
            int rd = mc.options.getViewDistance().getValue();
            return Math.max(96.0f, (rd + 1) * 16.0f);
        }

        void renderGpu(vesence.renderengine.render.pipeline.KillEffectScanPipeline pipeline,
                       org.joml.Matrix4f projection, org.joml.Matrix4f view, Vec3 cameraPos,
                       int accent, boolean scanlines) {
            float vd = viewDistance();

            int outer = lighten(accent, 0.45f);
            int mid = lighten(accent, 0.18f);
            int inner = darken(accent, 0.18f);
            int scanCol = scanlines ? lighten(accent, 0.6f) : 0;
            int flickOuter = lighten(accent, 0.7f);
            int flickMid = ColorUtil.interpolate(accent, 0xFFFFFFFF, 0.28f);
            int flickInner = darken(accent, 0.08f);
            int flickScan = scanlines ? darken(accent, 0.35f) : 0;

            float baseRadius = 1.0f + (vd - 1.0f) * quintOut(timeProgress(growthDuration));
            float baseWave = Math.min(waveFn(1.0f - timeProgress(growthDuration)) * 1.75f, 1.0f);
            pipeline.renderWave(mc, projection, view, cameraPos, center, baseRadius, baseRadius, 30.0f,
                    scaleColor(outer, baseWave), scaleColor(mid, baseWave), scaleColor(inner, baseWave), scaleColor(scanCol, baseWave));

            float flickRadius = 1.0f + (vd - 1.0f) * expoInOut(timeProgress(growthDuration * 0.5f));
            float flickAlphaProg = 1.0f - timeProgress(growthDuration * 0.5f);
            float flickWave = Math.min(waveFn(flickAlphaProg) * 2.0f, 1.0f);
            pipeline.renderWave(mc, projection, view, cameraPos, center, flickRadius, flickRadius / 1.5f, 40.0f,
                    scaleColor(flickOuter, flickAlphaProg), scaleColor(flickMid, flickWave), scaleColor(flickInner, flickWave), scaleColor(flickScan, flickWave));
        }

        private static int scaleColor(int color, float factor) {
            float c = Mth.clamp(factor, 0.0f, 1.0f);
            int r = (int) ((color >> 16 & 0xFF) * c);
            int g = (int) ((color >> 8 & 0xFF) * c);
            int b = (int) ((color & 0xFF) * c);
            int a = (int) (255.0f * c);
            return (a << 24) | (r << 16) | (g << 8) | b;
        }

        private static int darken(int color, float amount) {
            float f = 1.0f - Mth.clamp(amount, 0.0f, 1.0f);
            int r = (int) ((color >> 16 & 0xFF) * f);
            int g = (int) ((color >> 8 & 0xFF) * f);
            int b = (int) ((color & 0xFF) * f);
            int a = color >> 24 & 0xFF;
            return (a << 24) | (r << 16) | (g << 8) | b;
        }
    }

    // ═══ FragParticle (Матрица) ══════════════════════════════════════════════
    private static final class FragParticle {
        private Vec3 pos;
        private Vec3 prevPos;
        private Vec3 motion;
        private final int maxTime;
        private final float gravity;
        private final long startTime = System.currentTimeMillis();
        private final float baseSize;
        private final float baseBright;

        FragParticle(Vec3 spawn, int maxTime, float xzSpeedMax, float ySpeedMax, float gravity) {
            this.pos = spawn;
            this.prevPos = spawn;
            ThreadLocalRandom r = ThreadLocalRandom.current();
            this.motion = new Vec3(
                    (r.nextFloat() - 0.5f) * 2f * xzSpeedMax,
                    (r.nextFloat() - 0.5f) * 2f * ySpeedMax - ySpeedMax / 4.0f + ySpeedMax / 4.0f,
                    (r.nextFloat() - 0.5f) * 2f * xzSpeedMax);
            this.maxTime = maxTime;
            this.gravity = gravity;
            this.baseSize = 0.035f + r.nextFloat() * 0.045f;
            this.baseBright = expoInOut(r.nextFloat());
        }

        void tick() {
            this.prevPos = this.pos;
            this.pos = this.pos.add(this.motion);
            this.motion = new Vec3(this.motion.x * 0.99, (this.motion.y - gravity) * 0.994, this.motion.z * 0.99);
        }

        boolean isExpired() {
            return System.currentTimeMillis() - startTime > maxTime;
        }

        private float alpha() {
            return 1.0f - Math.min((float) (System.currentTimeMillis() - startTime) / maxTime, 1.0f);
        }

        void render(VertexConsumer buffer, PoseStack ms, Vec3 cameraPos, float partialTicks) {
            float a = alpha();
            if (a <= 0.001f) return;
            Vec3 rp = new Vec3(
                    Mth.lerp(partialTicks, prevPos.x, pos.x),
                    Mth.lerp(partialTicks, prevPos.y, pos.y),
                    Mth.lerp(partialTicks, prevPos.z, pos.z));
            int bright = (int) (baseBright * 255) & 0xFF;

            ms.pushPose();
            ms.translate(rp.x - cameraPos.x, rp.y - cameraPos.y, rp.z - cameraPos.z);
            ms.multiply(mc.gameRenderer.getCamera().getRotation());
            Matrix4f matrix = ms.last().getPositionMatrix();
            appendQuadRGBA(buffer, matrix, baseSize, bright, bright, bright, a * 0.38f);
            appendQuadRGBA(buffer, matrix, baseSize * 0.22f, bright, bright, bright, a * 0.9f);
            ms.popPose();
        }
    }

    // ═══ DeathEffect — молния (порт RelevantPremiumpp4/zenith) ═══════════════
    private static final class DeathEffect {
        private static final long APPEAR_MS = 700L;
        private static final long FADE_MS = 1400L;
        final Vec3 pos;
        final int color;
        final long startedAt = System.currentTimeMillis();
        final List<Vec3> poses = new ArrayList<>();
        long fadeStartedAt = -1L;

        DeathEffect(Vec3 pos, int color) {
            this.pos = pos;
            this.color = color;
            Vec3 lastPos = pos;
            for (int i = 0; i < 200; i++) {
                lastPos = lastPos.add(
                        (random.nextFloat() - 0.5f) * 0.5f,
                        0.25,
                        (random.nextFloat() - 0.5f) * 0.5f);
                this.poses.add(lastPos);
            }
        }

        boolean isFinished() {
            return this.fadeStartedAt >= 0L && (System.currentTimeMillis() - this.fadeStartedAt) >= FADE_MS;
        }

        void render(VertexConsumer buffer, PoseStack ms, Vec3 cameraPos) {
            long now = System.currentTimeMillis();
            float appear = bounceIn(Mth.clamp((float) (now - startedAt) / (float) APPEAR_MS, 0.0f, 1.0f));
            if (appear >= 0.99f && this.fadeStartedAt < 0L) {
                this.fadeStartedAt = now;
            }
            float fade = this.fadeStartedAt < 0L ? 0.0f
                    : sineInOut(Mth.clamp((float) (now - this.fadeStartedAt) / (float) FADE_MS, 0.0f, 1.0f));
            float finalAlpha = appear * (1.0f - fade);
            if (finalAlpha <= 0.001f) return;

            int r = this.color >> 16 & 0xFF;
            int g = this.color >> 8 & 0xFF;
            int b = this.color & 0xFF;
            int wr = (r + 255 * 2) / 3;
            int wg = (g + 255 * 2) / 3;
            int wb = (b + 255 * 2) / 3;

            for (Vec3 p : this.poses) {
                float size = (float) (1.0 + 3.0 * (p.y - this.pos.y) / 50.0);
                ms.pushPose();
                ms.translate(p.x - cameraPos.x, p.y - cameraPos.y, p.z - cameraPos.z);
                ms.multiply(mc.gameRenderer.getCamera().getRotation());
                Matrix4f matrix = ms.last().getPositionMatrix();
                appendQuadRGBA(buffer, matrix, size * 2.2f, r, g, b, finalAlpha * 0.4f);
                appendQuadRGBA(buffer, matrix, size * 1.1f, r, g, b, finalAlpha * 0.8f);
                appendQuadRGBA(buffer, matrix, size * 0.5f, wr, wg, wb, finalAlpha);
                ms.popPose();
            }
        }
    }

    // ═══ Particle — частица взрыва (порт RelevantPremiumpp4/zenith) ══════════
    private static final class Particle {
        double x, y, z;
        float vx, vy, vz;
        long startTime, lastUpdate;
        float gravityFactor;
        long lifeTime;
        boolean hasGravity;
        float friction;
        int color;

        Particle(Vec3 origin, float ox, float oy, float oz, int color, boolean hasGravity) {
            this.x = origin.x + ox;
            this.y = origin.y + oy;
            this.z = origin.z + oz;
            this.color = color;
            this.startTime = this.lastUpdate = System.currentTimeMillis();
            this.hasGravity = hasGravity;
            this.gravityFactor = 0.005f + random.nextFloat() * 0.005f;
            this.lifeTime = 1508 + random.nextInt(1508);
            this.friction = hasGravity ? 0.999f : 0.995f;
            float spread = hasGravity ? 0.04f : 0.03f;
            this.vx = (random.nextFloat() - 0.5f) * spread;
            this.vy = hasGravity ? 0.025f + random.nextFloat() * 0.035f : (random.nextFloat() - 0.5f) * 0.03f;
            this.vz = (random.nextFloat() - 0.5f) * spread;
        }

        void update() {
            float bounceFriction = 0.5f;
            long current = System.currentTimeMillis();
            float delta = (float) (current - this.lastUpdate) / 16.67f;
            this.lastUpdate = current;
            if (delta > 5.0f) delta = 5.0f;

            if (this.hasGravity) {
                this.vy -= this.gravityFactor * delta;
            }
            float drag = (float) Math.pow(this.friction, delta);
            this.vx *= drag;
            this.vy *= drag;
            this.vz *= drag;

            double nextX = this.x + this.vx * delta;
            double nextY = this.y + this.vy * delta;
            double nextZ = this.z + this.vz * delta;

            if (this.hasGravity && mc.level != null) {
                if (!mc.level.getBlockState(BlockPos.ofFloored(this.x, nextY - 0.05, this.z)).isAir()) {
                    this.vy = -this.vy * bounceFriction;
                    nextY = this.y;
                }
                if (!mc.level.getBlockState(BlockPos.ofFloored(nextX, this.y, this.z)).isAir()) {
                    this.vx = -this.vx * bounceFriction;
                    nextX = this.x;
                }
                if (!mc.level.getBlockState(BlockPos.ofFloored(this.x, this.y, nextZ)).isAir()) {
                    this.vz = -this.vz * bounceFriction;
                    nextZ = this.z;
                }
            }

            if (Math.abs(this.vy) <= 1.0E-4f) {
                this.vx = 0.0f;
                this.vz = 0.0f;
            }

            this.x = nextX;
            this.y = nextY;
            this.z = nextZ;
        }

        float getAlpha() {
            float progress = Mth.clamp((float) (System.currentTimeMillis() - this.startTime) / this.lifeTime, 0.0f, 1.0f);
            return 1.0f - progress;
        }

        boolean isDead() {
            return System.currentTimeMillis() - this.startTime > this.lifeTime;
        }

        void render(VertexConsumer buffer, PoseStack ms, Vec3 cameraPos) {
            float innerSize = 0.1f;
            float alpha = this.getAlpha();
            int r = this.color >> 16 & 0xFF;
            int g = this.color >> 8 & 0xFF;
            int b = this.color & 0xFF;
            ms.pushPose();
            ms.translate(this.x - cameraPos.x, this.y - cameraPos.y, this.z - cameraPos.z);
            ms.multiply(mc.gameRenderer.getCamera().getRotation());
            appendQuadRGBA(buffer, ms.last().getPositionMatrix(), innerSize, r, g, b, 0.9f * alpha);
            ms.popPose();
        }
    }

    private static final class ActiveEffect {
        final Vec3 position;
        final long startTime;

        ActiveEffect(Vec3 position, long startTime) {
            this.position = position;
            this.startTime = startTime;
        }
    }

    private static final class Tracked {
        Vec3 feet;
        Vec3 eye;

        Tracked(Vec3 feet, Vec3 eye) {
            this.feet = feet;
            this.eye = eye;
        }
    }

    private record PendingSound(SoundEvent sound, float volume, long playAt) {
    }
}
