package io.github.watermelonknight.rocm;

/** Values from {@code hip/driver_types.h}'s {@code hipMemcpyKind}. */
public enum HipMemcpyKind {
    HOST_TO_HOST(0),
    HOST_TO_DEVICE(1),
    DEVICE_TO_HOST(2),
    DEVICE_TO_DEVICE(3),
    DEFAULT(4);

    private final int nativeValue;

    HipMemcpyKind(int nativeValue) {
        this.nativeValue = nativeValue;
    }

    public int nativeValue() {
        return nativeValue;
    }
}
