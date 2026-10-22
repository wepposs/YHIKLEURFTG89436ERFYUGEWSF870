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

    // Замер шага ротации по тикам: античит смотрит на разгон шага, поэтому
    // тест обязан мерить не только «попал или нет», но и как менялся шаг.
    private static float prevTickYaw;
    private static boolean hasTickYaw;
    private static float prevStep;
    private static boolean hasStep;
    private static float maxStep;
    private static float maxAccel;
    private static int reversals;
    private static int stepTicks;

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
        if (hasTickYaw) {
            float step = net.minecraft.util.Mth.wrapDegrees(yaw - prevTickYaw);
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
            prevStep = step;
            hasStep = true;
            stepTicks++;
        }
        prevTickYaw = yaw;
        hasTickYaw = true;
    }

    private static void finish(Minecraft mc) {
        System.out.println("[auratest] камера: maxFrameYaw="
                + String.format("%.1f", maxFrameYaw) + " deg, кадров=" + frames);
        System.out.println("[auratest] шаг ротации: maxStep="
                + String.format("%.2f", maxStep) + " maxAccel="
                + String.format("%.2f", maxAccel) + " разворотов=" + reversals
                + " тиков=" + stepTicks);
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
