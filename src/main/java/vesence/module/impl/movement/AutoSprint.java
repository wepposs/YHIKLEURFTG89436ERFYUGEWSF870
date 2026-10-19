package vesence.module.impl.movement;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import vesence.Vesence;
import vesence.event.EventInit;
import vesence.event.impl.EventChangeWorld;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.event.player.EventInput;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.impl.combat.AttackAura;

/**
 * Полный порт функции AutoSprint из клиента "wave 1.5 by grimsnap".
 * Логика оригинала сохранена 1:1 — переведены только маппинги Forge/MCP -> Fabric/Yarn.
 *
 * Режимы (галочка "Мод"):
 *  - "Обычный"  : держим клавишу спринта; когда AttackAura активна, есть цель и
 *                 включены "Только криты" — синхронный сброс спринта (легитный крит).
 *  - "Пакетный" : сброс спринта отправкой пакета STOP_SPRINTING синхронно с ударом
 *                 AttackAura (см. вызовы AutoSprint.onPacketAttackReset()/
 *                 onPacketSprintKeyTick() внутри AttackAura).
 */
@IModule(
        name = "Auto Sprint",
        description = "\u0410\u0432\u0442\u043e\u043c\u0430\u0442\u0438\u0447\u0435\u0441\u043a\u0438 \u0430\u043a\u0442\u0438\u0432\u0438\u0440\u0443\u0435\u0442 \u0440\u0435\u0436\u0438\u043c \u0431\u0435\u0433\u0430, \u043a\u043e\u0433\u0434\u0430 \u0438\u0433\u0440\u043e\u043a \u043d\u0430\u0447\u0438\u043d\u0430\u0435\u0442 \u0434\u0432\u0438\u0433\u0430\u0442\u044c\u0441\u044f",
        category = Category.MOVEMENT
)
@Environment(EnvType.CLIENT)
public class AutoSprint extends Module {

    // "Мод" — режим работы: "Обычный" / "Пакетный" (по умолчанию "Обычный").
    public final ModeSetting mode = new ModeSetting(
            "\u041c\u043e\u0434", "\u041e\u0431\u044b\u0447\u043d\u044b\u0439",
            "\u041e\u0431\u044b\u0447\u043d\u044b\u0439", "\u041f\u0430\u043a\u0435\u0442\u043d\u044b\u0439");

    // Аналог ванильного поля serverSprintState (MCP) — server-side состояние спринта.
    // Отслеживаем по исходящим пакетам START/STOP_SPRINTING (как SprintHandler в wave).
    public static boolean serverSprintState = false;

    // Кол-во тиков, в течение которых после пакетного сброса гасим ввод движения
    // (реальный W-tap для легитного сброса). Порт wave Aura.legitSprintResetTicks.
    public static int legitSprintResetTicks = 0;

    public AutoSprint() {
        this.addSettings(mode);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        // Инициализируем трекер актуальным состоянием, чтобы пакетный режим не считал
        // спринт выключенным до первого исходящего пакета.
        serverSprintState = mc.player != null && mc.player.isSprinting();
        legitSprintResetTicks = 0;
    }

    @EventInit
    public void onUpdateganodn(EventUpdate eventUpdate) {
        if (mc.player == null) {
            return;
        }
        if (mode.is("\u041e\u0431\u044b\u0447\u043d\u044b\u0439")) { // "Обычный"
            AttackAura aura = Vesence.get.manager.get(AttackAura.class);
            boolean reset = aura != null && aura.enable && AttackAura.target != null
                    && AttackAura.extraSettings.get("\u0422\u043e\u043b\u044c\u043a\u043e \u043a\u0440\u0438\u0442\u044b"); // "Только криты"
            if (reset) {
                if (sprint()) {
                    mc.options.sprintKey.setPressed(true);
                } else {
                    mc.options.sprintKey.setPressed(false);
                    mc.player.setSprinting(false);
                }
            } else {
                mc.options.sprintKey.setPressed(true);
            }
        }
    }

    // Гасим ввод движения на время окна пакетного сброса (порт wave onInput).
    @EventInit
    public void onInput(EventInput e) {
        if (legitSprintResetTicks > 0) {
            e.setForward(0.0F);
            e.setStrafe(0.0F);
            --legitSprintResetTicks;
        }
    }

    // Трекер server-side спринта по исходящим пакетам (порт wave SprintHandler).
    @EventInit
    public void onPacket(EventPacket e) {
        if (!e.isSend()) {
            return;
        }
        if (e.getPacket() instanceof ClientCommandC2SPacket dcp) {
            if (dcp.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                serverSprintState = true;
            } else if (dcp.getMode() == ClientCommandC2SPacket.Mode.STOP_SPRINTING) {
                serverSprintState = false;
            }
        }
    }

    @EventInit
    public void onWorldChange(EventChangeWorld e) {
        legitSprintResetTicks = 0;
        serverSprintState = false;
    }

    public boolean sprint() {
        return canSprint();
    }

    public boolean RAGE() {
        return !mc.player.isSneaking()
                && !mc.player.horizontalCollision
                && mc.player.input.playerInput.forward()
                && !mc.player.isInSneakingPose()
                && !mc.player.hasStatusEffect(StatusEffects.SLOWNESS)
                && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS)
                && !mc.player.isInSwimmingPose()
                && !mc.player.isUsingItem();
    }

    public boolean canSprint() {
        return mc.world.getBlockState(mc.player.getBlockPos()).isOf(Blocks.COBWEB)
                || mc.player.getAbilities().flying
                || mc.player.isTouchingWater()
                || mc.player.isInLava()
                || mc.player.hasVehicle()
                || (mc.player.fallDistance <= 0.0F && mc.player.isOnGround());
    }

    @Override
    public void onDisable() {
        if (mc.options != null && mc.options.sprintKey != null) {
            mc.options.sprintKey.setPressed(false);
        }
        legitSprintResetTicks = 0;
        super.onDisable();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ПАКЕТНЫЙ РЕЖИМ (sync to AttackAura). Вызывается из AttackAura, чтобы сброс
    //  спринта был синхронен с ударом ауры — как в оригинале (Aura.attack()/
    //  Aura.bifcriticals()).
    // ─────────────────────────────────────────────────────────────────────────

    // Пакетный сброс спринта прямо перед ударом ауры (порт wave Aura.attack()).
    public static void onPacketAttackReset() {
        AutoSprint as = Vesence.get.manager.get(AutoSprint.class);
        if (as == null || !as.enable || !as.mode.is("\u041f\u0430\u043a\u0435\u0442\u043d\u044b\u0439")) { // "Пакетный"
            return;
        }
        if (mc.player == null || mc.player.networkHandler == null) {
            return;
        }
        boolean sprint = serverSprintState && !mc.player.isInLava();
        if (sprint) {
            legitSprintResetTicks = 1;
            if (serverSprintState) {
                serverSprintState = false;
                mc.player.setSprinting(false);
                mc.player.networkHandler.sendPacket(
                        new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
            }
        }
    }

    // Управление клавишей спринта после удара ауры (порт wave Aura.bifcriticals()).
    public static void onPacketSprintKeyTick() {
        AutoSprint as = Vesence.get.manager.get(AutoSprint.class);
        if (as == null || !as.enable || !as.mode.is("\u041f\u0430\u043a\u0435\u0442\u043d\u044b\u0439")) { // "Пакетный"
            return;
        }
        if (mc.player == null) {
            return;
        }
        if (as.canSprint()) {
            mc.options.sprintKey.setPressed(true);
        } else {
            mc.options.sprintKey.setPressed(false);
            mc.player.setSprinting(false);
        }
    }
}
