package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.scoreboard.ReadableScoreboardScore;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.math.Box;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@IModule(name = "ScoreboardHealth", description = "Берёт здоровье из scoreboard вместо реального", category = Category.MISC, bind = -1)
@Environment(EnvType.CLIENT)
public class ScoreboardHealth extends Module {

    private static ScoreboardHealth instance;

    public ScoreboardHealth() {
        instance = this;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    public static float getHealth(LivingEntity target, float originalHealth) {
        if (!isEnabled() || !(target instanceof PlayerEntity)) {
            return originalHealth;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return originalHealth;

        ServerInfo serverInfo = mc.getCurrentServerEntry();
        if (serverInfo != null) {
            String ip = serverInfo.address.toLowerCase();
            if (ip.contains("funtime.su") || ip.contains("funtime.me") || ip.contains("spookytime.net")) {
                if (target.hasStatusEffect(StatusEffects.INVISIBILITY)) {
                    return -1f;
                }

                Box box = target.getBoundingBox().expand(0.5, 2.0, 0.5);
                List<ArmorStandEntity> armorStands = mc.world.getEntitiesByClass(ArmorStandEntity.class, box, armorStand -> armorStand.getY() > target.getY() + 1.0);

                for (ArmorStandEntity armorStand : armorStands) {
                    if (armorStand.hasCustomName()) {
                        String name = armorStand.getCustomName().getString();
                        Pattern pattern = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*❤");
                        Matcher matcher = pattern.matcher(name);
                        if (matcher.find()) {
                            try {
                                return Float.parseFloat(matcher.group(1));
                            } catch (NumberFormatException ignored) {}
                        }

                        pattern = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");
                        matcher = pattern.matcher(name);
                        if (matcher.find()) {
                            try {
                                return Float.parseFloat(matcher.group(1));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
            }
        }

        try {
            Scoreboard scoreboard = mc.world.getScoreboard();

            ScoreboardDisplaySlot[] slots = {
                ScoreboardDisplaySlot.BELOW_NAME,
                ScoreboardDisplaySlot.SIDEBAR
            };

            for (ScoreboardDisplaySlot slot : slots) {
                ScoreboardObjective objective = scoreboard.getObjectiveForSlot(slot);
                if (objective == null) continue;

                ReadableScoreboardScore score = scoreboard.getScore(target, objective);
                if (score != null) {
                    int points = score.getScore();
                    if (points > 0) {
                        return (float) points;
                    }
                }
            }

            for (ScoreboardObjective objective : scoreboard.getObjectives()) {
                ReadableScoreboardScore score = scoreboard.getScore(target, objective);
                if (score != null) {
                    int points = score.getScore();
                    if (points > 0) {
                        return (float) points;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return originalHealth;
    }
}
