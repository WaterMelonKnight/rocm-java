package io.github.watermelonknight.rocm;

/** A HIP device ordinal. Device properties are intentionally outside this MVP. */
public record HipDevice(int ordinal) {
    public HipDevice {
        if (ordinal < 0) {
            throw new IllegalArgumentException("Device ordinal must not be negative");
        }
    }
}
