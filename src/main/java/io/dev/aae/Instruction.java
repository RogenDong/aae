package io.dev.aae;

/**
 * 机构指令带支持的指令。
 *
 * <p>符号区分大小写：{@code g/t} 沿轨道正/负方向移动机构，{@code a/d} 绕基座旋转，
 * {@code q/e} 绕抓握点枢轴旋转，{@code w/s} 伸缩长度，{@code f/r}
 * 抓取与释放。</p>
 */
public enum Instruction {
    /** 沿所在轨道的 {@code +} 方向移动一步。 */
    MOVE_FORWARD('g'),
    /** 沿所在轨道的 {@code -} 方向移动一步。 */
    MOVE_BACKWARD('t'),
    /** 逆时针-以抓握点为圆心 */
    PIVOT_CCW('q'),
    /** 顺时针-以抓握点为圆心 */
    PIVOT_CW('e'),
    /** 逆时针-以臂基座为圆心 */
    ROTATE_CCW('a'),
    /** 顺时针-以臂基座为圆心 */
    ROTATE_CW('d'),
    /** 伸展 */
    EXTEND_PISTON('w'),
    /** 收缩 */
    RETRACT_PISTON('s'),
    GRAB('f'),
    RELEASE('r'),
    WAIT('.');

    private final char symbol;

    Instruction(char symbol) {
        this.symbol = symbol;
    }

    public char symbol() {
        return symbol;
    }

    public static Instruction fromSymbol(char symbol) {
        for (Instruction instruction : values()) {
            if (instruction.symbol == symbol) {
                return instruction;
            }
        }
        throw new IllegalArgumentException("unsupported instruction symbol: " + symbol);
    }
}
