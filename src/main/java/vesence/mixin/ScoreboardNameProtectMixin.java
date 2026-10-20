package vesence.mixin;

import java.util.Comparator;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Team;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vesence.Vesence;
import vesence.module.impl.misc.NameProtect;
import vesence.module.impl.visuals.NoRender;
import vesence.utils.other.SidebarEntry;

/**
 * Streamer Mode (4): перерисовывает ванильный scoreboard-сайдбар с применением
 * замены ника/друзей, скрытия грифер- и серверных слов и перекраски красного
 * текста в белый. Порт из RelevantPremiumpp4 (GorbunovRendererMixin),
 * адаптированный под 1.21.11 (сигнатура renderScoreboardSidebar(GuiGraphicsExtractor,
 * Objective)).
 *
 * <p>Скорборд рисуется деревьями {@link Component}, которые НЕ проходят через
 * строковый {@link DrawContextNameProtectMixin}, поэтому чистим его отдельно —
 * иначе имя/домены осталось бы видно в таблице.
 */
@Environment(EnvType.CLIENT)
@Mixin(Hud.class) // TODO(port26): Gui#renderScoreboardSidebar moved to Hud#displayScoreboardSidebar in 26.3
public abstract class ScoreboardNameProtectMixin {

    @Inject(
        method = "displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vesence$renderPatchedScoreboard(GuiGraphicsExtractor drawContext, Objective objective, CallbackInfo ci) {
        // Кастомный скорборд (ScoreboardElement) активен — подавляем И ванильную
        // отрисовку, И NameProtect-перерисовку: скорборд рисует HUD-элемент,
        // который сам применяет замену ника/грифа/сервер-слов и red->white.
        if (vesence.module.impl.visuals.elements.ScoreboardElement.shouldSuppressVanilla()) {
            ci.cancel();
            return;
        }

        NameProtect np = NameProtect.getInstance();
        if (np == null || !np.enable) {
            return; // модуль выключен — рисуем ванильно
        }

        // Если NoRender прячет таблицу — отменяем без отрисовки (совместимость).
        if (Vesence.isModInitialized() && Vesence.get.manager != null) {
            NoRender nr = Vesence.get.manager.get(NoRender.class);
            if (nr != null && nr.enable && Boolean.TRUE.equals(NoRender.elements.get("Таблица"))) {
                ci.cancel();
                return;
            }
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.font == null) {
            return;
        }

        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<SidebarEntry> lines = scoreboard.listPlayerScores(objective)
            .stream()
            .filter(entry -> !entry.isHidden())
            .sorted(Comparator.comparing(PlayerScoreEntry::value).reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER))
            .limit(15L)
            .map(entry -> {
                Team team = scoreboard.getPlayersTeam(entry.owner());
                Component name = this.vesence$patchText(PlayerTeam.formatNameForTeam(team, entry.ownerName()));
                // Счёт справа (красные цифры 1..N) тоже перекрашиваем красный->белый.
                Component score = np.recolorRedToWhite(entry.formatValue(numberFormat));
                int scoreWidth = client.font.width(score);
                return new SidebarEntry(name, score, scoreWidth);
            })
            .toList();
        Component title = this.vesence$patchText(objective.getDisplayName());
        int titleWidth = client.font.width(title);
        int maxWidth = titleWidth;
        int separatorWidth = client.font.width(": ");

        for (SidebarEntry line : lines) {
            maxWidth = Math.max(maxWidth, client.font.width(line.name) + (line.scoreWidth > 0 ? separatorWidth + line.scoreWidth : 0));
        }

        int lineCount = lines.size();
        int totalHeight = lineCount * 9;
        int bottom = drawContext.guiHeight() / 2 + totalHeight / 3;
        int left = drawContext.guiWidth() - maxWidth - 3;
        int right = drawContext.guiWidth() - 1;
        int bodyColor = ((int)(client.options.textBackgroundOpacity().get() * 0.3F * 255.0F) << 24) & 0xFF000000;
        int headerColor = ((int)(client.options.textBackgroundOpacity().get() * 0.4F * 255.0F) << 24) & 0xFF000000;
        int top = bottom - lineCount * 9;
        drawContext.fill(left - 2, top - 10, right, top - 1, headerColor);
        drawContext.fill(left - 2, top - 1, right, bottom, bodyColor);
        drawContext.text(client.font, title, left + maxWidth / 2 - titleWidth / 2, top - 9, -1, false);

        for (int index = 0; index < lineCount; index++) {
            SidebarEntry line = lines.get(index);
            int y = bottom - (lineCount - index) * 9;
            drawContext.text(client.font, line.name, left, y, -1, false);
            drawContext.text(client.font, line.score, right - line.scoreWidth, y, -1, false);
        }

        ci.cancel();
    }

    private Component vesence$patchText(Component text) {
        NameProtect np = NameProtect.getInstance();
        if (np == null) {
            return text;
        }
        // Замена ника/друзей в дереве Component (сохраняя стили).
        Component patched = np.patchText(text);
        // Перекраска красного (&c/&4) в белый (если галочка включена).
        patched = np.recolorRedToWhite(patched);
        // Чистим грифер/серверные слова на ПОЛНОЙ строке (склейка дублей).
        String patchedString = patched.getString();
        String cleaned = np.patchServerAndGrief(patchedString);
        return cleaned.equals(patchedString)
            ? patched
            : Component.literal(cleaned).setStyle(patched.getStyle());
    }
}
