package io.dev.aae;

/** 轨道重叠不满足静态衔接规则时的具体异常类型。 */
public final class InvalidTrackOverlapException extends InvalidTrackNetworkException {

    private static final long serialVersionUID = 1L;

    public InvalidTrackOverlapException(Reason reason, String message) {
        super(reason, message);
    }
}
