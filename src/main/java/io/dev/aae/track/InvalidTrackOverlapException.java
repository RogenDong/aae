package io.dev.aae.track;

/** 不同轨道声明共享格点时的具体异常类型。 */
public final class InvalidTrackOverlapException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public InvalidTrackOverlapException(String message) {
        super(message);
    }
}
