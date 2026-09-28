package io.github.watermelonknight.rocm;

/** Indicates that a HIP Runtime call returned an error. */
public final class HipException extends RuntimeException {
    private final int errorCode;

    public HipException(String operation, int errorCode, String errorText) {
        super(operation + " failed with HIP error " + errorCode + ": " + errorText);
        this.errorCode = errorCode;
    }

    public int errorCode() {
        return errorCode;
    }
}
