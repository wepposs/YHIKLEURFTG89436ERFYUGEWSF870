package vesence.module.impl.visuals;

import java.util.UUID;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import vesence.event.EventInit;
import vesence.event.impl.EventPacket;
import vesence.event.impl.EventUpdate;
import vesence.event.player.AttackEvent;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.ModeSetting;
import vesence.module.api.setting.impl.SliderSetting;

/**
 * HitSound — порт из RelevantPremiumpp4.
 * Звук при ударе, убийстве и тотем-попах (свои/вражеские).
 */
@IModule(name = "HitSound", description = "Звук при ударе и тотем-попах", category = Category.VISUALS, bind = -1)
@Environment(EnvType.CLIENT)
public class HitSound extends Module {

    private static final float HIT_SOUND_MULTIPLIER = 6.0f;
    private static final float TOTEM_SOUND_MULTIPLIER = 1.0f;
    private static final float HIT_SOUND_PITCH = 1.08f;
    private static final float TOTEM_SOUND_PITCH = 1.0f;
    private static final long TOTEM_LINK_MS = 5000L;

    // ── Hit sounds ──
    private static final SoundEvent MOAN_1 = SoundEvent.of(Identifier.of("vesence", "moan1"));
    private static final SoundEvent MOAN_2 = SoundEvent.of(Identifier.of("vesence", "moan2"));
    private static final SoundEvent MOAN_3 = SoundEvent.of(Identifier.of("vesence", "moan3"));
    private static final SoundEvent MOAN_4 = SoundEvent.of(Identifier.of("vesence", "moan4"));
    private static final SoundEvent METALLIC = SoundEvent.of(Identifier.of("vesence", "metallic"));
    private static final SoundEvent CRIME = SoundEvent.of(Identifier.of("vesence", "crime"));
    private static final SoundEvent BELL = SoundEvent.of(Identifier.of("vesence", "bell"));
    private static final SoundEvent KRIT = SoundEvent.of(Identifier.of("vesence", "krit"));
    private static final SoundEvent HIT1 = SoundEvent.of(Identifier.of("vesence", "hit1"));
    private static final SoundEvent HIT2 = SoundEvent.of(Identifier.of("vesence", "hit2"));

    // ── Kill sounds ──
    private static final SoundEvent KILL1 = SoundEvent.of(Identifier.of("vesence", "kill1"));
    private static final SoundEvent KILL2 = SoundEvent.of(Identifier.of("vesence", "kill2"));
    private static final SoundEvent KILL3 = SoundEvent.of(Identifier.of("vesence", "kill3"));
    private static final SoundEvent KILL4 = SoundEvent.of(Identifier.of("vesence", "kill4"));
    private static final SoundEvent KILL5 = SoundEvent.of(Identifier.of("vesence", "kill5"));
    private static final SoundEvent KILL6 = SoundEvent.of(Identifier.of("vesence", "kill6"));

    // ── Totem sounds ──
    private static final SoundEvent TOTEM_POP_ENEMY = SoundEvent.of(Identifier.of("vesence", "totem_pop_enemy"));
    private static final SoundEvent TOTEM_POP_SELF = SoundEvent.of(Identifier.of("vesence", "totem_pop_self"));

    // ── Settings ──
    public final ModeSetting type = new ModeSetting("Тип звука",
            "Moan", "Moan", "Metallic", "Crime", "Bell", "Krit", "Hit1", "Hit2", "Off");
    public final SliderSetting volume = new SliderSetting("Громкость", 80.0, 0.0, 100.0, 1.0);

    public final BooleanSetting killSoundEnabled = new BooleanSetting("Звук убийства", true);
    public final ModeSetting killSoundType = new ModeSetting("Тип звука убийства",
            "Kill1", "Kill1", "Kill2", "Kill3", "Kill4", "Kill5", "Kill6", "Random")
            .hidden(() -> !killSoundEnabled.get());
    public final SliderSetting killVolume = new SliderSetting("Громкость убийства", 80.0, 0.0, 100.0, 1.0)
            .hidden(() -> !killSoundEnabled.get());

    public final BooleanSetting enemyTotemVoice = new BooleanSetting("Озвучка снесения тотема", true);
    public final BooleanSetting selfTotemVoice = new BooleanSetting("Озвучка потери тотема", true);
    public final SliderSetting totemVolume = new SliderSetting("Громкость тотема", 90.0, 0.0, 100.0, 1.0);

    // ── State ──
    private int moanIndex = 1;
    private UUID lastAttackedPlayerId;
    private long lastAttackedPlayerAt;
    private SoundInstance lastHitSound;
    private SoundInstance lastTotemSound;

    // ── Kill detection ──
    private static final long KILL_TIMEOUT_MS = 6000L;
    private static final long UNLOAD_KILL_WINDOW_MS = 2500L;
    private static final double UNLOAD_KILL_RANGE_SQ = 49.0;

    private LivingEntity lastTarget;
    private boolean waitingForDeath;
    private double lastTargetDistanceSq;
    private long killWindowStart;

    public HitSound() {
        this.addSettings(new Setting[]{type, volume, killSoundEnabled, killSoundType, killVolume,
                enemyTotemVoice, selfTotemVoice, totemVolume});
    }

    @EventInit
    public void onAttackEntity(AttackEvent event) {
        onAttack(event.getTarget());
    }

    @EventInit
    public void onPacket(EventPacket event) {
        if (event.getType() != EventPacket.Type.RECEIVE) return;
        if (mc.world == null) return;
        if (event.getPacket() instanceof EntityStatusS2CPacket statusPacket) {
            if (statusPacket.getStatus() == 35) {
                Entity entity = statusPacket.getEntity(mc.world);
                if (entity instanceof PlayerEntity player) {
                    onTotemPop(player);
                }
            }
        }
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        checkKills();
    }

    private void checkKills() {
        if (!killSoundEnabled.get()) return;
        if (mc.player == null || mc.world == null) return;

        if (mc.player.getHealth() <= 0.0F) {
            resetKillState();
            return;
        }
        if (!waitingForDeath || lastTarget == null) return;

        if (System.currentTimeMillis() - killWindowStart >= KILL_TIMEOUT_MS) {
            resetKillState();
            return;
        }

        Entity byId = mc.world.getEntityById(lastTarget.getId());
        boolean stillLoaded = byId == lastTarget;

        if (stillLoaded) {
            lastTargetDistanceSq = mc.player.squaredDistanceTo(lastTarget);
            boolean confirmedKill = lastTarget.isDead()
                    || lastTarget.getHealth() <= 0.0F
                    || lastTarget.deathTime > 0;
            if (confirmedKill) {
                resetKillState();
                onKill();
            }
        } else {
            boolean diedOnUnload = byId == null
                    && lastTargetDistanceSq <= UNLOAD_KILL_RANGE_SQ
                    && (System.currentTimeMillis() - killWindowStart) < UNLOAD_KILL_WINDOW_MS;
            resetKillState();
            if (diedOnUnload) {
                onKill();
            }
        }
    }

    private void resetKillState() {
        lastTarget = null;
        waitingForDeath = false;
        lastTargetDistanceSq = 0.0;
    }

    private void onAttack(Entity entity) {
        if (!(entity instanceof LivingEntity living)) return;
        if (mc.player == null) return;
        if (mc.player.getHealth() <= 0.0F) return;

        lastTarget = living;
        waitingForDeath = true;
        lastTargetDistanceSq = mc.player.squaredDistanceTo(living);
        killWindowStart = System.currentTimeMillis();

        if (entity instanceof PlayerEntity player) {
            lastAttackedPlayerId = player.getUuid();
            lastAttackedPlayerAt = System.currentTimeMillis();
        }

        if (mc.getSoundManager() == null) return;
        if (type.is("Off")) return;

        float vol = ((float) (double) volume.get() / 100.0f) * HIT_SOUND_MULTIPLIER;
        if (vol <= 0f) return;

        SoundEvent sound = switch (type.get()) {
            case "Metallic" -> METALLIC;
            case "Crime" -> CRIME;
            case "Bell" -> BELL;
            case "Krit" -> KRIT;
            case "Hit1" -> HIT1;
            case "Hit2" -> HIT2;
            default -> nextMoan();
        };
        playHitSound(sound, HIT_SOUND_PITCH, vol);
    }

    private void onKill() {
        if (!killSoundEnabled.get()) return;
        if (mc.getSoundManager() == null) return;

        float vol = ((float) (double) killVolume.get() / 100.0f) * HIT_SOUND_MULTIPLIER;
        if (vol <= 0f) return;

        SoundEvent sound;
        if (killSoundType.is("Random")) {
            int randomIndex = (int) (Math.random() * 6) + 1;
            sound = switch (randomIndex) {
                case 2 -> KILL2;
                case 3 -> KILL3;
                case 4 -> KILL4;
                case 5 -> KILL5;
                case 6 -> KILL6;
                default -> KILL1;
            };
        } else {
            sound = switch (killSoundType.get()) {
                case "Kill2" -> KILL2;
                case "Kill3" -> KILL3;
                case "Kill4" -> KILL4;
                case "Kill5" -> KILL5;
                case "Kill6" -> KILL6;
                default -> KILL1;
            };
        }
        playHitSound(sound, 1.0f, vol);
    }

    private void onTotemPop(PlayerEntity poppedPlayer) {
        if (mc.getSoundManager() == null || mc.player == null || poppedPlayer == null) return;

        float vol = ((float) (double) totemVolume.get() / 100.0f) * TOTEM_SOUND_MULTIPLIER;
        if (vol <= 0f) return;

        if (poppedPlayer == mc.player || poppedPlayer.getUuid().equals(mc.player.getUuid())) {
            if (selfTotemVoice.get()) playTotemSound(TOTEM_POP_SELF, vol);
            return;
        }
        if (!enemyTotemVoice.get()) return;
        if (lastAttackedPlayerId == null || !lastAttackedPlayerId.equals(poppedPlayer.getUuid())) return;
        if (System.currentTimeMillis() - lastAttackedPlayerAt > TOTEM_LINK_MS) return;
        playTotemSound(TOTEM_POP_ENEMY, vol);
    }

    private SoundEvent nextMoan() {
        SoundEvent sound = switch (moanIndex) {
            case 2 -> MOAN_2;
            case 3 -> MOAN_3;
            case 4 -> MOAN_4;
            default -> MOAN_1;
        };
        if (++moanIndex > 4) moanIndex = 1;
        return sound;
    }

    private void playTotemSound(SoundEvent sound, float vol) {
        SoundInstance instance = PositionedSoundInstance.ui(sound, TOTEM_SOUND_PITCH, vol);
        if (lastTotemSound != null) mc.getSoundManager().stop(lastTotemSound);
        lastTotemSound = instance;
        mc.getSoundManager().play(instance);
    }

    private void playHitSound(SoundEvent sound, float pitch, float vol) {
        if (mc.getSoundManager() == null) return;
        SoundInstance instance = PositionedSoundInstance.ui(sound, pitch, vol);
        if (lastHitSound != null) mc.getSoundManager().stop(lastHitSound);
        lastHitSound = instance;
        mc.getSoundManager().play(instance);
    }

    @Override
    public void onDisable() {
        super.onDisable();
        resetKillState();
        lastAttackedPlayerId = null;
        moanIndex = 1;
        if (lastHitSound != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(lastHitSound);
            lastHitSound = null;
        }
        if (lastTotemSound != null && mc.getSoundManager() != null) {
            mc.getSoundManager().stop(lastTotemSound);
            lastTotemSound = null;
        }
    }
}
