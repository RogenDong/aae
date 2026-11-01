package io.dev.aae;

/** 轨道允许使用的单位步类型。 */
public enum TrackType {
    /** 标准正交轨道 */
    ORTHOGONAL,
    /** 对角线轨道 */
    DIAGONAL;

    boolean isUnitStep(GridPoint from, GridPoint to) {
        long deltaX;
        long deltaY;
        try {
            deltaX = Math.subtractExact(to.x(), from.x());
            deltaY = Math.subtractExact(to.y(), from.y());
        } catch (ArithmeticException ignored) {
            return false;
        }

        return switch (this) {
            case ORTHOGONAL ->
                    (Math.abs(deltaX) == 1 && deltaY == 0)
                            || (deltaX == 0 && Math.abs(deltaY) == 1);
            case DIAGONAL -> Math.abs(deltaX) == 1 && Math.abs(deltaY) == 1;
        };
    }
}
