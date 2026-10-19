package vesence.module.impl.combat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
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

        if (mc.player == null || mc.world == null) {
            return;
        }
        if (mc.currentScreen != null) {
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
                             && mc.player.getVelocity().y < 0.0
                             && !mc.player.isOnGround()
                             && !mc.player.isClimbing()
                             && !mc.player.isTouchingWater()
                             && !mc.player.isInLava()
                             && !mc.player.hasVehicle()
                             && !mc.player.hasStatusEffect(StatusEffects.BLINDNESS);

        if (smartCrits.get()) {
            boolean isJumpingUp = !mc.player.isOnGround() && mc.player.getVelocity().y >= 0.0;
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
            Attack.useEntity(target, () -> {}, () -> {}, Hand.MAIN_HAND, true);
        }
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (!entity.isAlive()) return false;
        if (entity == mc.player) return false;

        boolean isPlayer = entity instanceof PlayerEntity;
        if (isPlayer && !targets.get("Игроки")) return false;
        if (!isPlayer && !targets.get("Мобы")) return false;

        if (isPlayer && !targets.get("Голые")) {
            PlayerEntity player = (PlayerEntity) entity;
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
