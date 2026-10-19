package vesence.module.impl.misc;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.text.MutableText;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import vesence.event.EventInit;
import vesence.event.impl.EventUpdate;
import vesence.module.api.Category;
import vesence.module.api.IModule;
import vesence.module.api.Module;
import vesence.module.api.setting.Setting;
import vesence.module.api.setting.impl.BooleanSetting;
import vesence.module.api.setting.impl.StringSetting;
import vesence.utils.friends.FriendStorage;
import vesence.utils.other.ReplaceUtils;

/**
 * Streamer Mode (порт NameProtect из RelevantPremiumpp4).
 *
 * <p>Полностью скрывает никнейм игрока (и опционально друзей) во всём тексте
 * клиента, а также содержит набор «стримерских» галочек: цензура ввода чата,
 * скрытие серверных сфер/шаров (подмена на голову игрока), дефолтные скины,
 * скрытие рамок/баннеров, скрытие серверных доменов и перекраска красного
 * текста скорборда в белый.
 *
 * <p>ВАЖНО: сохранён исходный статический API vesence
 * ({@link #getReplaced(String)}, {@link #isEnabled()}, {@link #getInstance()},
 * {@link #fakeName}) — на него завязаны Nametags и DiscordRCP.
 */
@IModule(
    name = "NameProtect",
    description = "Скрывает ваш никнейм и приватность на стримах",
    category = Category.MISC,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class NameProtect extends Module {

    private static NameProtect instance;

    /** Кэш подменного имени (обновляется каждый тик). Читается DiscordRCP. */
    public static String fakeName = "";

    /** Бренд-строка клиента, которой заменяются серверные слова/грифер-домены. */
    private static final String BRAND = "vesence";

    // ─── Галочки ───────────────────────────────────────────────────────────────
    /** Подменное имя (ник), которым заменяется ваш реальный никнейм. */
    public StringSetting name = new StringSetting("Ваше новое имя", "vesence");
    /** Скрывать также ники друзей. */
    public BooleanSetting hideFriends = new BooleanSetting("Скрывать друзей", false);
    /** Заменять грифер-названия ("Анархия-N", "ГРИФ #N", домены) на бренд. */
    public BooleanSetting grief = new BooleanSetting("Скрывать гриф", false);
    /** Цензура: чёрный квадрат поверх ввода после /tpa, /login, /reg и т.д. */
    public BooleanSetting censorChat = new BooleanSetting("Цензура", false);
    /** (1) Серверные косметик-сферы/шары рендерить как голову игрока. */
    public BooleanSetting hideSpheres = new BooleanSetting("Скрывать шары/сферы", false);
    /** (2) Подменять серверные скины на дефолтные Steve/Alex. */
    public BooleanSetting defaultSkins = new BooleanSetting("Дефолтные скины", false);
    /** (3) Делать серверные рамки/баннеры/картины полностью невидимыми. */
    public BooleanSetting hideFrames = new BooleanSetting("Скрывать рамки/баннеры", false);
    /** (4) Заменять серверные домены (time/spooky/fun/sky-слова) на бренд. */
    public BooleanSetting hideServerWords = new BooleanSetting("Скрывать сервер", false);
    /** (4) В scoreboard заменять красный (&c/&4) текст на белый. */
    public BooleanSetting scoreboardRedToWhite = new BooleanSetting("Скорборд: красный → белый", false);

    /**
     * Слова-маркеры серверов, которые надо «зачистить» в любом месте текста
     * (чат/scoreboard/bossbar/tab). Матчим как ЦЕЛЬНОЕ слово — токен должен
     * быть либо составным именем (SpookyTime, FunTime, SkyWars, SkyBlock), либо
     * одиночным словом spooky/fun/sky/time, за которым идёт граница (пробел,
     * пунктуация, цифра, домен или конец строки). Поэтому "sometime", "funny",
     * "skyscraper", "skype", "function" НЕ ломаются, тогда как "SpookyTime",
     * "FunTime.su", "SkyWars", "Time #5", "sky.su", "join fun!" → бренд.
     */
    private static final Pattern SERVER_WORD_PATTERN = Pattern.compile(
        "(?iu)\\b(?:spooky\\s*time|funtime|spookytime|fun\\s*time|sky\\s*wars|skywars"
        + "|spooky|skyblock|skypvp"
        + "|(?:spooky|funtime|fun|sky|time)(?=[\\s.\\-#:!,]|\\d|$)"
        + ")"
        + "(?:\\.(?:su|net|ru|com|org|gg|fun|club|pro|online))?",
        Pattern.UNICODE_CHARACTER_CLASS);

    /** Красные legacy-коды Minecraft, которые scoreboard-ремап превращает в белый. */
    private static final int RED_DARK_RED_RGB_C = 0xFF5555; // &c
    private static final int RED_DARK_RED_RGB_4 = 0xAA0000; // &4
    private static final int WHITE_RGB = 0xFFFFFF;

    /**
     * Команды, после которых нужно закрашивать всё что вводит игрок.
     * Цензура накладывается ПОСЛЕ команды и пробела (т.е. закрывается аргумент).
     */
    private static final String[] CENSORED_COMMANDS = {
        "/tpa", "/login", "/l", "/reg", "/register",
        "/warp", "/pay", ".friend add"
    };

    /**
     * Команды, для которых цензура накладывается СРАЗУ С НАЧАЛА команды
     * (включая сам слеш), без требования пробела/конца строки.
     */
    private static final String[] CENSORED_COMMANDS_FROM_START = {
        "/an"
    };

    public NameProtect() {
        instance = this;
        addSettings(new Setting[]{
            this.name, this.hideFriends, this.grief, this.censorChat,
            this.hideSpheres, this.defaultSkins, this.hideFrames,
            this.hideServerWords, this.scoreboardRedToWhite
        });
    }

    /**
     * Отображаемое имя — «Streamer Mode» вместо технического NameProtect.
     * Поле {@code name}/@IModule.name оставлено NameProtect, чтобы не ломать
     * сериализацию конфига и обращения по классу.
     */
    @Override
    public String getDisplayName() {
        return "Streamer Mode";
    }

    @EventInit
    private void onUpdate(EventUpdate e) {
        fakeName = this.getReplacementName();
    }

    // ─── Ядро: замена имени ──────────────────────────────────────────────────

    /**
     * Статическая замена (СОВМЕСТИМОСТЬ: используется Nametags/DiscordRCP).
     * Заменяет ник игрока и (опционально) друзей на подменное имя. Грифер- и
     * серверные-слова здесь НЕ трогаются — для этого есть {@link #patch(String)}.
     */
    public static String getReplaced(String input) {
        NameProtect np = instance;
        if (np == null || !np.enable || input == null) {
            return input;
        }
        String replacement = np.getReplacementName();
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSession() != null) {
            input = np.replaceIgnoreCase(input, mc.getSession().getUsername(), replacement);
        }
        if (np.hideFriends.get()) {
            for (String friend : FriendStorage.getFriends()) {
                input = np.replaceIgnoreCase(input, friend, replacement);
            }
        }
        return input;
    }

    public static boolean isEnabled() {
        return instance != null && instance.enable;
    }

    public static NameProtect getInstance() {
        return instance;
    }

    /**
     * Полный патч строки для рендера: имя+друзья, затем грифер- и серверные слова.
     * Вызывается из GuiGraphics-миксина (глобальный текст: чат, actionbar и т.д.).
     */
    public String patch(String text) {
        if (!this.shouldPatch()) {
            return text;
        }
        String replacement = this.getReplacementName();
        String out = this.replaceIgnoreCase(text, mc.getSession().getUsername(), replacement);
        if (this.hideFriends.get()) {
            for (String friend : FriendStorage.getFriends()) {
                out = this.replaceIgnoreCase(out, friend, replacement);
            }
        }
        return this.patchGrief(out);
    }

    public String patchIncomingText(String text) {
        return this.patch(text);
    }

    /** Патч дерева {@link Component} (имя+друзья) с сохранением стилей. */
    public Component patchText(Component text) {
        if (text == null) {
            return null;
        } else if (!this.shouldPatch()) {
            return text;
        } else {
            String replacement = this.getReplacementName();
            Component output = ReplaceUtils.replace(text, mc.getSession().getUsername(), replacement);
            if (this.hideFriends.get()) {
                for (String friend : FriendStorage.getFriends()) {
                    output = ReplaceUtils.replace(output, friend, replacement);
                }
            }
            return output;
        }
    }

    public String getReplacementName() {
        String value = this.name.get();
        return value != null && !value.isBlank() ? value : BRAND;
    }

    public boolean shouldHideGrief() {
        return this.grief.get();
    }

    // ─── Цензура ввода чата ──────────────────────────────────────────────────

    public boolean isCensorChatEnabled() {
        return this.enable && this.censorChat.get();
    }

    /**
     * Индекс начала «чувствительной части» в тексте чата (после команды и
     * пробела) — туда нужно наложить чёрный квадрат. Для команд из
     * {@link #CENSORED_COMMANDS_FROM_START} цензура начинается с начала строки.
     * Возвращает -1, если цензура не нужна.
     */
    public int getCensorStartIndex(String text) {
        if (!this.isCensorChatEnabled() || text == null || text.isEmpty()) return -1;
        String lower = text.toLowerCase(Locale.ROOT);

        for (String cmd : CENSORED_COMMANDS_FROM_START) {
            if (lower.startsWith(cmd) || cmd.startsWith(lower)) {
                return 0;
            }
        }

        for (String cmd : CENSORED_COMMANDS) {
            if (lower.startsWith(cmd)) {
                int len = cmd.length();
                if (lower.length() == len) return len;
                if (lower.charAt(len) == ' ') return len;
            }
        }
        return -1;
    }

    // ─── Замена по подстроке (кэш скомпилированных паттернов) ────────────────

    private String replaceIgnoreCase(String text, String target, String replacement) {
        if (text == null || target == null || target.isEmpty()) {
            return text;
        }
        Pattern pattern = getOrCachePattern(target);
        return pattern.matcher(text).replaceAll(Matcher.quoteReplacement(replacement));
    }

    private static final int PATTERN_CACHE_LIMIT = 64;
    private static final Map<String, Pattern> PATTERN_CACHE = new LinkedHashMap<String, Pattern>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
            return this.size() > PATTERN_CACHE_LIMIT;
        }
    };

    private static Pattern getOrCachePattern(String target) {
        synchronized (PATTERN_CACHE) {
            Pattern cached = PATTERN_CACHE.get(target);
            if (cached != null) {
                return cached;
            }
            Pattern compiled = Pattern.compile(Pattern.quote(target), Pattern.CASE_INSENSITIVE);
            PATTERN_CACHE.put(target, compiled);
            return compiled;
        }
    }

    // ─── Грифер- и серверные слова ───────────────────────────────────────────

    private String patchGrief(String text) {
        if (text == null) return null;
        String out = text;
        if (this.grief.get()) {
            out = out.replaceAll("Анархия-\\d+", BRAND);
            out = out.replaceAll("ГРИФ #\\d+", BRAND);
            out = out.replaceAll("(?i)funtime\\.su", BRAND);
            out = out.replaceAll("(?i)spookytime\\.net", BRAND);
        }
        out = this.patchServerWords(out);
        return out;
    }

    /**
     * «Умная» замена серверных слов. Срабатывает ТОЛЬКО при включённой галочке
     * «Скрывать сервер». Матчит spooky/fun/time/sky как отдельные слова.
     * Подряд идущие маркеры схлопываются в один бренд.
     */
    private String patchServerWords(String text) {
        if (text == null || text.isEmpty() || !this.hideServerWords.get()) {
            return text;
        }
        // §/& и &#rrggbb цвет-коды «приклеиваются» к словам и рвут границу \b —
        // поэтому детект и замену выполняем на версии БЕЗ цвет-кодов.
        String stripped = stripColorCodes(text);
        if (!SERVER_WORD_PATTERN.matcher(stripped).find()) {
            return text;
        }
        String replaced = SERVER_WORD_PATTERN.matcher(stripped).replaceAll(BRAND);
        replaced = replaced.replaceAll(Pattern.quote(BRAND) + "(?:[\\s\\-]*" + Pattern.quote(BRAND) + ")+", BRAND);
        return replaced;
    }

    /** Убирает legacy/hex цвет-коды из строки для нужд детекции сервер-слов. */
    private static String stripColorCodes(String s) {
        if (s == null) return "";
        String out = s.replaceAll("(?i)&#[0-9a-f]{6}", "");
        out = out.replaceAll("[<{][0-9a-fA-F]{6}[>}]", "");
        out = out.replaceAll("(?i)[\u00A7&$][0-9a-fk-orx]", "");
        return out;
    }

    /** Публичная обёртка над {@link #patchServerWords(String)} для мик-синов. */
    public String applyServerWords(String text) {
        return this.patchServerWords(text);
    }

    /**
     * Грифер-названия + серверные слова (БЕЗ замены ника) на полной строке.
     * Для мик-синов, которые уже заменили ник в дереве {@link Component} и хотят
     * дочистить домены/грифер на склеенной строке (напр. scoreboard-перерисовка).
     */
    public String patchServerAndGrief(String text) {
        return this.patchGrief(text);
    }

    // ─── (1) Скрытие шаров/сфер ──────────────────────────────────────────────

    public boolean shouldHideSpheres() {
        return this.enable && this.hideSpheres.get();
    }

    private static final String[] SPHERE_ITEM_KEYWORDS = {
        "сфера", "сферу", "сферы", "шар", "orb", "sphere"
    };

    public boolean isSphereItemName(String displayName) {
        if (displayName == null || displayName.isEmpty()) return false;
        String lower = displayName.toLowerCase(Locale.ROOT);
        for (String kw : SPHERE_ITEM_KEYWORDS) {
            if (lower.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Строит предмет-замену для сферы: голова игрока {@code holder} (PLAYER_HEAD
     * с его GameProfile). Кэшируем по UUID. {@code null}, если неприменимо.
     */
    public ItemStack buildHeadReplacement(net.minecraft.entity.player.Player holder) {
        if (holder == null) return null;
        UUID uuid = holder.getUuid();
        if (uuid == null) return null;
        HeadStack cached = HEAD_STACK_CACHE.get(uuid);
        if (cached != null && cached.stack != null) {
            return cached.stack;
        }
        try {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponentTypes.PROFILE, ProfileComponent.ofStatic(holder.getGameProfile()));
            HeadStack hs = new HeadStack();
            hs.stack = head;
            HEAD_STACK_CACHE.put(uuid, hs);
            return head;
        } catch (Throwable t) {
            return null;
        }
    }

    private static final Map<UUID, HeadStack> HEAD_STACK_CACHE = new ConcurrentHashMap<>();
    private static final class HeadStack { ItemStack stack; }

    // ─── (2) Дефолтные скины ─────────────────────────────────────────────────

    public boolean shouldForceDefaultSkins() {
        return this.enable && this.defaultSkins.get();
    }

    // ─── (3) Скрытие рамок/баннеров ──────────────────────────────────────────

    public boolean shouldHideFrames() {
        return this.enable && this.hideFrames.get();
    }

    // ─── (4) Скорборд: красный → белый ───────────────────────────────────────

    public boolean shouldScoreboardRedToWhite() {
        return this.enable && this.scoreboardRedToWhite.get();
    }

    /**
     * Если включён ремап «красный → белый» и {@code argb} — ЛЮБОЙ оттенок
     * красного (см. {@link #isRedColor(int)}), возвращает белый с сохранением
     * альфа-канала. Иначе — исходный цвет.
     */
    public int remapScoreboardColor(int argb) {
        if (!this.shouldScoreboardRedToWhite()) return argb;
        int rgb = argb & 0xFFFFFF;
        int alpha = argb & 0xFF000000;
        if (isRedColor(rgb)) {
            return alpha | WHITE_RGB;
        }
        return argb;
    }

    /**
     * Рекурсивно перекрашивает ЛЮБОЙ красный текст в белый во всём дереве
     * {@link Component}, сохраняя структуру, контент и остальные стили. Возвращает
     * исходный текст, если перекраска выключена.
     */
    public Component recolorRedToWhite(Component text) {
        if (text == null || !this.shouldScoreboardRedToWhite()) {
            return text;
        }
        return rebuildRedToWhite(text);
    }

    private static Component rebuildRedToWhite(Component node) {
        Style style = node.getStyle();
        TextColor color = style.getColor();
        if (color != null && isRedColor(color.getRgb() & 0xFFFFFF)) {
            style = style.withColor(TextColor.fromRgb(WHITE_RGB));
        }
        MutableText rebuilt = MutableText.of(node.getContent()).setStyle(style);
        for (Component sibling : node.getSiblings()) {
            rebuilt.append(rebuildRedToWhite(sibling));
        }
        return rebuilt;
    }

    /**
     * Эвристика «этот цвет — красный». Ловит НЕ только легаси &c
     * ({@value #RED_DARK_RED_RGB_C}) / &4 ({@value #RED_DARK_RED_RGB_4}), но и
     * любые кастомные hex-оттенки красного (ярко-красный, тёмно-красный,
     * бордовый и т.п.): красный канал доминирует, зелёный и синий заметно ниже.
     * Оранжевый/розовый/жёлтый/белый/серый НЕ трогаются (их G или B слишком
     * высоки относительно R).
     */
    private static boolean isRedColor(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r >= 90 && g <= 120 && b <= 120 && (r - g) >= 45 && (r - b) >= 45;
    }

    private boolean shouldPatch() {
        return this.enable && mc != null && mc.player != null && mc.level != null;
    }
}
