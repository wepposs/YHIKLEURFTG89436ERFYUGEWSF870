package vesence.module.impl.visuals.custompet;

import java.util.Locale;

/**
 * Варианты (скины) кастомного питомца.
 * Перенесено из RelevantPremiumpp4 (ru.whylol...custompet.CustomPetVariant).
 * Чистая логика — версионно-независимо.
 */
public enum CustomPetVariant {
    DEFAULT("Обычная"),
    NITWIT("Нитвит"),
    GARDENER("Фермер"),
    FISHERMAN("Рыбак"),
    MERCHANT("Путешественник"),
    SORCERER("Ведьма");

    private final String settingValue;

    CustomPetVariant(String settingValue) {
        this.settingValue = settingValue;
    }

    public String getSettingValue() {
        return this.settingValue;
    }

    public static String[] settingValues() {
        CustomPetVariant[] values = CustomPetVariant.values();
        String[] settingValues = new String[values.length - 1];
        int index = 0;
        for (CustomPetVariant variant : values) {
            if (variant == DEFAULT) continue;
            settingValues[index++] = variant.settingValue;
        }
        return settingValues;
    }

    public static CustomPetVariant fromSettingValue(String value) {
        if (value == null || value.isBlank()) {
            return NITWIT;
        }
        for (CustomPetVariant variant : CustomPetVariant.values()) {
            if (!variant.settingValue.equalsIgnoreCase(value)) continue;
            return variant;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "default", "обычная" -> NITWIT;
            case "nitwit" -> NITWIT;
            case "gardener", "farmer", "садовник" -> GARDENER;
            case "fisherman" -> FISHERMAN;
            case "merchant", "traveler", "traveller", "торговец" -> MERCHANT;
            case "sorcerer", "witch", "колдун" -> SORCERER;
            default -> NITWIT;
        };
    }

    public boolean usesMerchantBody() {
        return this == MERCHANT;
    }

    public boolean usesMerchantLeaf() {
        return this == MERCHANT;
    }

    public boolean usesGardenerGear() {
        return this == GARDENER;
    }

    public boolean usesSorcererHat() {
        return this == SORCERER;
    }

    public boolean usesFishermanGear() {
        return this == FISHERMAN;
    }
}
