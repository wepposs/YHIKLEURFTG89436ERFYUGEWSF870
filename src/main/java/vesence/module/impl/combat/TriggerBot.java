package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.MultiBooleanSetting;
import vesence.module.impl.combat.auraComponent.Attack;

@IModule(
    name = "TriggerBot",
    description = "Автоматически бьет сущность, на которую наведен прицел",
    category = Category.COMBAT,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class TriggerBot extends Module {

    public static MultiBooleanSetting targets = new MultiBooleanSetting(
        "Цели",
        new BooleanSetting("Игроки", true),
        new BooleanSetting("Голые", true),
        new BooleanSetting("Мобы", true)
    );

    public static BooleanSetting onlyCrits = new BooleanSetting("Только Криты", true);
    public static BooleanSetting smartCrits = new BooleanSetting("Умные Криты", false);

    public static LivingEntity target;

    public TriggerBot() {
        this.addSettings(new Setting[]{targets, onlyCrits, smartCrits});
    }

    @Override
    public void onEnable() {
        target = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        target = null;
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        target = null;

        if (mc.player == null || mc.level == null) {
            return;
        }
        if (mc.screen != null) {
            return;
        }

        HitResult hitResult = mc.crosshairTarget;
        if (hitResult == null || hitResult.getType() != HitResult.Type.ENTITY) {
            return;
        }

        EntityHitResult entityHitResult = (EntityHitResult) hitResult;
        if (!(entityHitResult.getEntity() instanceof LivingEntity livingTarget)) {
            return;
        }

        if (!isValidTarget(livingTarget)) {
            return;
        }

        target = livingTarget;

        if (mc.player.getAttackCooldownProgress(0.0f) < 1.0f) {
            return;
        }

        boolean shouldAttack = true;

        boolean isVanillaCrit = mc.player.fallDistance > 0.0F
                             && mc.player.getDeltaMovement().y < 0.0
                             && !mc.player.onGround()
                             && !mc.player.isClimbing()
                             && !mc.player.isTouchingWater()
                             && !mc.player.isInLava()
                             && !mc.player.hasVehicle()
                             && !mc.player.hasStatusEffect(MobEffects.BLINDNESS);

        if (smartCrits.get()) {
            boolean isJumpingUp = !mc.player.onGround() && mc.player.getDeltaMovement().y >= 0.0;
            boolean canCritEnvironment = !mc.player.isTouchingWater() && !mc.player.isInLava() && !mc.player.isClimbing() && !mc.player.hasVehicle();

            if (isJumpingUp && canCritEnvironment) {
                shouldAttack = false;
            } else {
                shouldAttack = true;
            }
        } else if (onlyCrits.get()) {
            shouldAttack = isVanillaCrit;
        }

        if (shouldAttack) {
            Attack.useEntity(target, () -> {}, () -> {}, InteractionHand.MAIN_HAND, true);
        }
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        if (entity == mc.player) return false;

        boolean isPlayer = entity instanceof Player;
        if (isPlayer && !targets.get("Игроки")) return false;
        if (!isPlayer && !targets.get("Мобы")) return false;

        if (isPlayer && !targets.get("Голые")) {
            Player player = (Player) entity;
            boolean hasArmor = false;
            net.minecraft.entity.EquipmentSlot[] armorSlots = new net.minecraft.entity.EquipmentSlot[]{
                net.minecraft.entity.EquipmentSlot.HEAD,
                net.minecraft.entity.EquipmentSlot.CHEST,
                net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.FEET
            };
            for (net.minecraft.entity.EquipmentSlot slot : armorSlots) {
                if (!player.getEquippedStack(slot).isEmpty()) {
                    hasArmor = true;
                    break;
                }
            }
            if (!hasArmor) return false;
        }

        return true;
    }
}
