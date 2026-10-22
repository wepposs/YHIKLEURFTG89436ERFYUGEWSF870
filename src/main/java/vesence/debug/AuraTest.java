package vesence.debug;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import vesence.module.impl.combat.AttackAura;
import vesence.module.impl.combat.rotation.UShardRotation;

/**
 * Автотест киллауры. Включается {@code VESENCE_AURATEST=1}.
 *
 * <p>Ставит зомби прямо перед игроком, включает ауру и следит, уходит ли удар.
 * Печатает не только результат, но и причину: цель найдена или нет, сколько
 * удалось нанести, как ведёт себя камера.
 *
 * <p>Камера проверяется отдельно: жалоба «камера дёргается» означает, что
 * ротация ставит углы скачками. Здесь измеряется максимальное изменение
 * рысканья за кадр — при плавном ведении это единицы градусов.
 */
@Environment(EnvType.CLIENT)
public final class AuraTest {

    private static final boolean ACTIVE = System.getenv("VESENCE_AURATEST") != null;

    private static final int WARMUP_TICKS = 80;
    private static final int RUN_TICKS = 200;

    private static int ticks;
    private static int phase;
    private static boolean done;

    private static Zombie dummy;
    private static float startHealth;
    private static float lastYaw;
    private static boolean hasLast;
    private static float maxFrameYaw;
    private static int frames;

    // Замер шага ротации по тикам: ML-античит снимает с потока углов набор
    // признаков, поэтому тест обязан мерить не «попал или нет», а то, как
    // распределён шаг: гистограмму, разгон, связь осей, остаток.
    private static float prevTickYaw;
    private static float prevTickPitch;
    private static boolean hasTickYaw;
    private static float prevStep;
    private static boolean hasStep;
    private static float maxStep;
    private static float maxAccel;
    private static int reversals;
    private static int stepTicks;

    /** Выборка шагов для статистики распределения. */
    private static final float[] yawSteps = new float[4096];
    private static final float[] pitchSteps = new float[4096];
    private static int sampleCount;

    /** Остаток прицела на каждом тике — у бота он сходится в ноль. */
    private static final float[] residuals = new float[4096];
    private static int residualCount;

    private AuraTest() {
    }

    public static void init() {
        if (!ACTIVE) {
            return;
        }
        ClientTickEvents.END_CLIENT_TICK.register(AuraTest::tick);
        vesence.event.EventManager.register(new CameraSampler());
        System.out.println("[auratest] harness active");
    }

    /** Замер скачков камеры: событие приходит каждый кадр. */
    public static final class CameraSampler {
        @vesence.event.EventInit
        public void onRotation(vesence.event.player.EventRotation event) {
            if (!ACTIVE || dummy == null || done) {
                return;
            }
            float yaw = event.getYaw();
            if (hasLast) {
                float delta = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw));
                if (delta > maxFrameYaw) {
                    maxFrameYaw = delta;
                }
                frames++;
            }
            lastYaw = yaw;
            hasLast = true;
        }
    }

    private static void tick(Minecraft mc) {
        if (done || mc.player == null || mc.level == null) {
            return;
        }
        if (++ticks < WARMUP_TICKS) {
            return;
        }

        AttackAura aura = vesence.Vesence.get.getManager().get(AttackAura.class);
        if (aura == null) {
            return;
        }

        if (phase == 0) {
            Vec3 pos = mc.player.position()
                    .add(mc.player.getLookAngle().scale(2.5));
            // В 26.3 константы EntityType.ZOMBIE больше нет: типы берутся из
            // реестра по имени.
            var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getValue(net.minecraft.resources.Identifier.withDefaultNamespace("zombie"));
            if (type == null) {
                System.out.println("[auratest] тип zombie не найден в реестре");
                done = true;
                return;
            }
            dummy = (Zombie) type.create(mc.level,
                    net.minecraft.world.entity.EntitySpawnReason.COMMAND);
            if (dummy == null) {
                System.out.println("[auratest] не смог создать зомби");
                done = true;
                return;
            }
            dummy.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            dummy.setNoAi(true);
            dummy.setPermanentlyInvulnerable(false);
            // Клиентский уровень требует назначенный id: без него addEntity
            // падает на попытке прочитать getId().
            dummy.setId(900000 + (int) (System.nanoTime() % 100000));
            mc.level.addEntity(dummy);
            startHealth = dummy.getHealth();

            // Оружие в руку обязательно: аура по умолчанию бьёт только оружием,
            // и с пустой рукой тест мерил бы настройку, а не работоспособность.
            if (mc.player != null) {
                mc.player.getInventory().setSelectedSlot(0);
                mc.player.getInventory().setItem(0,
                        new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
                vesence.module.impl.combat.auraComponent.Attack.setTicksSinceHit(20);
            }

            if (!aura.enable) {
                aura.toggle();
            }
            // Режим ротации можно задать снаружи: так тест меряет именно тот
            // профиль, который проверяют, а не тот, что остался в конфиге.
            String mode = System.getenv("VESENCE_AURAMODE");
            if (mode != null && !mode.isBlank()) {
                AttackAura.rotationType.currentMode = mode;
            }
            System.out.println("[auratest] зомби создан в " + fmt(pos)
                    + " здоровье=" + startHealth + " аура=" + aura.enable
                    + " ротация=" + AttackAura.rotationType.get()
                    + " оружие=" + (mc.player == null ? "?"
                    : mc.player.getMainHandItem().getItem().toString()));
            phase = 1;
            ticks = 0;
            return;
        }

        if (phase == 1) {
            if (dummy == null || !dummy.isAlive()) {
                System.out.println("[auratest] зомби умер или пропал");
                finish(mc);
                return;
            }

            trackStep(mc);

            if (ticks % 20 == 0) {
                System.out.println("[auratest] t=" + ticks
                        + " hp=" + String.format("%.1f", dummy.getHealth())
                        + " dist=" + String.format("%.2f", mc.player.distanceTo(dummy))
                        + " yaw=" + String.format("%.1f", mc.player.getYRot())
                        + " pitch=" + String.format("%.1f", mc.player.getXRot())
                        + " остаток=" + String.format("%.1f", UShardRotation.residual())
                        + " maxStep=" + String.format("%.1f", maxStep)
                        + " maxAccel=" + String.format("%.1f", maxAccel)
                        + " maxFrameYaw=" + String.format("%.1f", maxFrameYaw));
            }
            if (dummy.getHealth() < startHealth) {
                System.out.println("[auratest] УДАР прошёл: hp "
                        + startHealth + " -> " + dummy.getHealth()
                        + " за " + ticks + " тиков");
                finish(mc);
                return;
            }
            if (ticks > RUN_TICKS) {
                System.out.println("[auratest] ИТОГ удара НЕ было за " + RUN_TICKS + " тиков");
                finish(mc);
            }
        }
    }

    /**
     * Замер шага ротации по тикам.
     *
     * <p>Меряется не только величина шага, но и его прирост: античит ловит не
     * размер доворота, а отсутствие разгона. Прирост шага за тик выше
     * {@code ACCEL_YAW} означает мгновенный доворот и провал проверки.
     */
    private static void trackStep(Minecraft mc) {
        float yaw = mc.player.getYRot();
        float pitch = mc.player.getXRot();
        if (hasTickYaw) {
            float step = net.minecraft.util.Mth.wrapDegrees(yaw - prevTickYaw);
            float pitchStep = pitch - prevTickPitch;
            float abs = Math.abs(step);
            if (abs > maxStep) {
                maxStep = abs;
            }
            if (hasStep) {
                float accel = abs - Math.abs(prevStep);
                if (accel > maxAccel) {
                    maxAccel = accel;
                }
                if (step != 0.0F && prevStep != 0.0F
                        && Math.signum(step) != Math.signum(prevStep)) {
                    reversals++;
                }
            }
            if (sampleCount < yawSteps.length) {
                yawSteps[sampleCount] = step;
                pitchSteps[sampleCount] = pitchStep;
                sampleCount++;
            }
            prevStep = step;
            hasStep = true;
            stepTicks++;
        }
        float res = UShardRotation.residual();
        if (residualCount < residuals.length && res < 1.0E4F) {
            residuals[residualCount++] = res;
        }
        prevTickYaw = yaw;
        prevTickPitch = pitch;
        hasTickYaw = true;
    }

    /**
     * Статистика распределения — то, что снимает ML-античит.
     *
     * <p>Печатаются ровно те признаки, по которым классификатор отличает руку от
     * бота:
     * <ul>
     *   <li>среднее, разброс, асимметрия и эксцесс шага — у человека распределение
     *       тяжёлохвостое (положительный эксцесс) и скошенное;</li>
     *   <li>доля нулевых шагов — у clamp почти ноль, у человека заметная;</li>
     *   <li>корреляция рысканья и тангажа — у человека высокая (одно движение
     *       руки), у независимых клампов около нуля;</li>
     *   <li>остаток: минимум не должен быть нулём, иначе сходимость выдаёт бота.</li>
     * </ul>
     */
    private static void reportDistribution() {
        if (sampleCount < 8) {
            System.out.println("[auratest] статистика: мало данных (" + sampleCount + ")");
            return;
        }

        double sumYaw = 0;
        double sumPitch = 0;
        int zeros = 0;
        for (int i = 0; i < sampleCount; i++) {
            sumYaw += yawSteps[i];
            sumPitch += pitchSteps[i];
            if (Math.abs(yawSteps[i]) < 1.0E-4F) {
                zeros++;
            }
        }
        double meanYaw = sumYaw / sampleCount;

        double varYaw = 0;
        double varPitch = 0;
        double cov = 0;
        for (int i = 0; i < sampleCount; i++) {
            double dy = yawSteps[i] - meanYaw;
            double dp = pitchSteps[i] - (sumPitch / sampleCount);
            varYaw += dy * dy;
            varPitch += dp * dp;
            cov += dy * dp;
        }
        varYaw /= sampleCount;
        varPitch /= sampleCount;
        cov /= sampleCount;

        double sdYaw = Math.sqrt(varYaw);
        double sdPitch = Math.sqrt(varPitch);

        // Асимметрия и эксцесс: главные признаки «нечеловеческого» распределения.
        double m3 = 0;
        double m4 = 0;
        for (int i = 0; i < sampleCount; i++) {
            double dy = yawSteps[i] - meanYaw;
            m3 += dy * dy * dy;
            m4 += dy * dy * dy * dy;
        }
        m3 /= sampleCount;
        m4 /= sampleCount;
        double skew = sdYaw > 1.0E-9 ? m3 / (sdYaw * sdYaw * sdYaw) : 0;
        double kurtosis = sdYaw > 1.0E-9 ? m4 / (varYaw * varYaw) - 3.0 : 0;

        double corr = (sdYaw > 1.0E-9 && sdPitch > 1.0E-9)
                ? cov / (sdYaw * sdPitch) : 0;

        double resMin = Double.MAX_VALUE;
        double resSum = 0;
        for (int i = 0; i < residualCount; i++) {
            resMin = Math.min(resMin, residuals[i]);
            resSum += residuals[i];
        }
        double resMean = residualCount > 0 ? resSum / residualCount : 0;

        System.out.println("[auratest] шаг: среднее=" + String.format("%.2f", meanYaw)
                + " sd=" + String.format("%.2f", sdYaw)
                + " асимметрия=" + String.format("%.2f", skew)
                + " эксцесс=" + String.format("%.2f", kurtosis)
                + " нулевых=" + String.format("%.0f%%", 100.0 * zeros / sampleCount));
        System.out.println("[auratest] оси: корреляция yaw/pitch="
                + String.format("%.2f", corr));
        System.out.println("[auratest] остаток: минимум=" + String.format("%.2f", resMin)
                + " средний=" + String.format("%.2f", resMean)
                + " (ноль был бы признаком бота)");
        System.out.println("[auratest] выборка: " + sampleCount + " шагов, "
                + residualCount + " замеров остатка");
    }

    private static void finish(Minecraft mc) {
        System.out.println("[auratest] камера: maxFrameYaw="
                + String.format("%.1f", maxFrameYaw) + " deg, кадров=" + frames);
        System.out.println("[auratest] шаг ротации: maxStep="
                + String.format("%.2f", maxStep) + " maxAccel="
                + String.format("%.2f", maxAccel) + " разворотов=" + reversals
                + " тиков=" + stepTicks);
        reportDistribution();
        if (dummy != null) {
            dummy.discard();
            dummy = null;
        }
        AttackAura aura = vesence.Vesence.get.getManager().get(AttackAura.class);
        if (aura != null && aura.enable) {
            aura.toggle();
        }
        System.out.println("[auratest] === DONE ===");
        done = true;
    }

    private static String fmt(Vec3 v) {
        return String.format("%.1f/%.1f/%.1f", v.x, v.y, v.z);
    }
}
