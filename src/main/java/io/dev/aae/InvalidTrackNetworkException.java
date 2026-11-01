package io.dev.aae;

/** 轨道静态衔接规则被违反时抛出的运行时异常。 */
public class InvalidTrackNetworkException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        LOOP_TRACK_OVERLAP,
        MORE_THAN_TWO_TRACKS_OVERLAP,
        NON_ENDPOINT_OVERLAP
    }

    private final Reason reason;

    public InvalidTrackNetworkException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }

    public Reason getReason() {
        return reason;
    }
}
