package vesence.pathing;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Как игрок попал в узел пути. Нужен исполнителю, чтобы понять, чего от него ждут:
 * прыгнуть, спрыгнуть, плыть или просто идти.
 */
@Environment(EnvType.CLIENT)
public enum MovementType {

    /** Обычный шаг по земле на ту же высоту. */
    WALK,

    /** Шаг с подъёмом на 1 блок — нужен прыжок. */
    ASCEND,

    /** Прыжок через пропасть: разбежались и перелетели. */
    JUMP_GAP,

    /** Шаг вниз: игрок сходит с края и падает до земли. */
    DESCEND,

    /** Падение с высоты больше одного блока. */
    FALL,

    /** Движение в воде. */
    SWIM,

    /** Подъём по лестнице или лиане. */
    CLIMB,

    /**
     * Проход через прорубленный блок.
     *
     * <p>Так игрок попадает в место, куда дороги нет: копает себе ход. Узел
     * помечается этим типом, а конкретный блок для ломания лежит в
     * {@link PathNode#dig}.
     */
    DIG,

    /** Стартовый узел, движения ещё не было. */
    START;

    public boolean needsJump() {
        return this == ASCEND || this == JUMP_GAP;
    }

    public boolean isVertical() {
        return this == FALL || this == CLIMB || this == SWIM;
    }
}
