package io.github.watermelonknight.rocm;

/** Indicates that the AMD HIP runtime shared library could not be loaded. */
public final class HipLibraryUnavailableException extends IllegalStateException {
    public HipLibraryUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
