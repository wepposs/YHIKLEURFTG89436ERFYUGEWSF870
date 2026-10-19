package vesence.module.impl.visuals.elements.modern;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import vesence.module.impl.visuals.elements.ScoreboardElement;

/**
 * Modern-вариант кастомного скорборда. Логика и рендер полностью совпадают с
 * {@link ScoreboardElement} (порт relevant ScoreboardHud рисуется одинаково в
 * обоих HUD-режимах через {@link vesence.module.impl.visuals.HudElement#drawHudPanel}).
 * Отдельный класс нужен только для параллельного списка modernElements в
 * {@link vesence.module.impl.visuals.Hud}. Все чекбоксы — статические поля
 * {@link ScoreboardElement}, поэтому default и modern делят одни настройки.
 */
@Environment(EnvType.CLIENT)
public class ModernScoreboardElement extends ScoreboardElement {

    public ModernScoreboardElement() {
        super();
    }
}
