package io.dev.aae.glyph;

/**
 * 一个周期内部的两个半周期。
 *
 * <p>每个周期先走{@link #FIRST_HALF}，再走 {@link #SECOND_HALF}，
 * 两个半周期都结束后 {@code cycle} 才加一。机构指令按类型分半执行
 * —— 第一半只处理抓取与释放，第二半处理移动、旋转、枢轴旋转和活塞伸缩
 * —— 因此输入与输出可以在同一个周期的两个半周期里分别生产或消费。</p>
 */
public enum GlyphPhase {

    /** 第一半周期 */
    FIRST_HALF,

    /** 第二半周期 */
    SECOND_HALF;

    /** 是否为第一半周期。 */
    public boolean isFirstHalf() {
        return this == FIRST_HALF;
    }

    /** 是否为第二半周期。 */
    public boolean isSecondHalf() {
        return this == SECOND_HALF;
    }
}
