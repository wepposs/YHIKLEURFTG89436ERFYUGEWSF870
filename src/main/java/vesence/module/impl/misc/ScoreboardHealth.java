package vesence.module.impl.misc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.phys.AABB;
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
        if (!isEnabled() || !(target instanceof Player)) {
            return originalHealth;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return originalHealth;

        ServerData serverInfo = mc.getCurrentServer();
        if (serverInfo != null) {
            String ip = serverInfo.ip.toLowerCase();
            if (ip.contains("funtime.su") || ip.contains("funtime.me") || ip.contains("spookytime.net")) {
                if (target.hasEffect(MobEffects.INVISIBILITY)) {
                    return -1f;
                }

                AABB box = target.getBoundingBox().inflate(0.5, 2.0, 0.5);
                List<ArmorStand> armorStands = mc.level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(ArmorStand.class), box, armorStand -> armorStand.getY() > target.getY() + 1.0);

                for (ArmorStand armorStand : armorStands) {
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
            Scoreboard scoreboard = mc.level.getScoreboard();

            DisplaySlot[] slots = {
                DisplaySlot.BELOW_NAME,
                DisplaySlot.SIDEBAR
            };

            for (DisplaySlot slot : slots) {
                Objective objective = scoreboard.getDisplayObjective(slot);
                if (objective == null) continue;

                net.minecraft.world.scores.ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(target.getScoreboardName()), objective);
                if (score != null) {
                    int points = score.value();
                    if (points > 0) {
                        return (float) points;
                    }
                }
            }

            for (Objective objective : scoreboard.getObjectives()) {
                net.minecraft.world.scores.ReadOnlyScoreInfo score = scoreboard.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(target.getScoreboardName()), objective);
                if (score != null) {
                    int points = score.value();
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
