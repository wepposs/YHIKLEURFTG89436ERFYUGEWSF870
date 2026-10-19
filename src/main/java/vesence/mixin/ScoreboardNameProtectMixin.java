package vesence.mixin;

import java.util.Comparator;
import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.scoreboard.number.NumberFormat;
import net.minecraft.scoreboard.number.StyledNumberFormat;
import net.minecraft.text.Text;
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
 * адаптированный под 1.21.11 (сигнатура renderScoreboardSidebar(DrawContext,
 * ScoreboardObjective)).
 *
 * <p>Скорборд рисуется деревьями {@link Text}, которые НЕ проходят через
 * строковый {@link DrawContextNameProtectMixin}, поэтому чистим его отдельно —
 * иначе имя/домены осталось бы видно в таблице.
 */
@Environment(EnvType.CLIENT)
@Mixin(InGameHud.class)
public abstract class ScoreboardNameProtectMixin {

    @Inject(
        method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void vesence$renderPatchedScoreboard(DrawContext drawContext, ScoreboardObjective objective, CallbackInfo ci) {
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

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return;
        }

        Scoreboard scoreboard = objective.getScoreboard();
        NumberFormat numberFormat = objective.getNumberFormatOr(StyledNumberFormat.RED);
        List<SidebarEntry> lines = scoreboard.getScoreboardEntries(objective)
            .stream()
            .filter(entry -> !entry.hidden())
            .sorted(Comparator.comparing(ScoreboardEntry::value).reversed().thenComparing(ScoreboardEntry::owner, String.CASE_INSENSITIVE_ORDER))
            .limit(15L)
            .map(entry -> {
                Team team = scoreboard.getScoreHolderTeam(entry.owner());
                Text name = this.vesence$patchText(Team.decorateName(team, entry.name()));
                // Счёт справа (красные цифры 1..N) тоже перекрашиваем красный->белый.
                Text score = np.recolorRedToWhite(entry.formatted(numberFormat));
                int scoreWidth = client.textRenderer.getWidth(score);
                return new SidebarEntry(name, score, scoreWidth);
            })
            .toList();
        Text title = this.vesence$patchText(objective.getDisplayName());
        int titleWidth = client.textRenderer.getWidth(title);
        int maxWidth = titleWidth;
        int separatorWidth = client.textRenderer.getWidth(": ");

        for (SidebarEntry line : lines) {
            maxWidth = Math.max(maxWidth, client.textRenderer.getWidth(line.name) + (line.scoreWidth > 0 ? separatorWidth + line.scoreWidth : 0));
        }

        int lineCount = lines.size();
        int totalHeight = lineCount * 9;
        int bottom = drawContext.getScaledWindowHeight() / 2 + totalHeight / 3;
        int left = drawContext.getScaledWindowWidth() - maxWidth - 3;
        int right = drawContext.getScaledWindowWidth() - 1;
        int bodyColor = client.options.getTextBackgroundColor(0.3F);
        int headerColor = client.options.getTextBackgroundColor(0.4F);
        int top = bottom - lineCount * 9;
        drawContext.fill(left - 2, top - 10, right, top - 1, headerColor);
        drawContext.fill(left - 2, top - 1, right, bottom, bodyColor);
        drawContext.drawText(client.textRenderer, title, left + maxWidth / 2 - titleWidth / 2, top - 9, -1, false);

        for (int index = 0; index < lineCount; index++) {
            SidebarEntry line = lines.get(index);
            int y = bottom - (lineCount - index) * 9;
            drawContext.drawText(client.textRenderer, line.name, left, y, -1, false);
            drawContext.drawText(client.textRenderer, line.score, right - line.scoreWidth, y, -1, false);
        }

        ci.cancel();
    }

    private Text vesence$patchText(Text text) {
        NameProtect np = NameProtect.getInstance();
        if (np == null) {
            return text;
        }
        // Замена ника/друзей в дереве Text (сохраняя стили).
        Text patched = np.patchText(text);
        // Перекраска красного (&c/&4) в белый (если галочка включена).
        patched = np.recolorRedToWhite(patched);
        // Чистим грифер/серверные слова на ПОЛНОЙ строке (склейка дублей).
        String patchedString = patched.getString();
        String cleaned = np.patchServerAndGrief(patchedString);
        return cleaned.equals(patchedString)
            ? patched
            : Text.literal(cleaned).setStyle(patched.getStyle());
    }
}
