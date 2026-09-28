package io.github.watermelonknight.rocm;

import io.github.watermelonknight.rocm.internal.HipApi;
import io.github.watermelonknight.rocm.internal.HipNative;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/** Entry point for the deliberately small HIP Runtime binding. */
public final class HipRuntime {
    private final HipApi api;

    private HipRuntime(HipApi api) {
        this.api = api;
    }

    /** Loads ROCm and resolves the required HIP symbols. Loading is explicit and lazy. */
    public static HipRuntime open() {
        return new HipRuntime(HipNative.load());
    }

    /** Probes library/symbol availability without retaining an exception. */
    public static boolean isAvailable() {
        try {
            open();
            return true;
        } catch (HipLibraryUnavailableException | UnsatisfiedLinkError exception) {
            return false;
        }
    }

    static HipRuntime forTesting(HipApi api) {
        return new HipRuntime(Objects.requireNonNull(api, "api"));
    }

    public int deviceCount() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = arena.allocate(ValueLayout.JAVA_INT);
            check("hipGetDeviceCount", api.getDeviceCount(result));
            return result.get(ValueLayout.JAVA_INT, 0);
        }
    }

    public HipDevice currentDevice() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment result = arena.allocate(ValueLayout.JAVA_INT);
            check("hipGetDevice", api.getDevice(result));
            return new HipDevice(result.get(ValueLayout.JAVA_INT, 0));
        }
    }

    public HipDevice selectDevice(int ordinal) {
        if (ordinal < 0) {
            throw new IllegalArgumentException("Device ordinal must not be negative");
        }
        check("hipSetDevice", api.setDevice(ordinal));
        return new HipDevice(ordinal);
    }

    public HipMemory malloc(long bytes) {
        if (bytes <= 0) {
            throw new IllegalArgumentException("Allocation size must be positive");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pointerSlot = arena.allocate(ValueLayout.ADDRESS);
            check("hipMalloc", api.malloc(pointerSlot, bytes));
            MemorySegment pointer = pointerSlot.get(ValueLayout.ADDRESS, 0);
            if (pointer.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("hipMalloc succeeded but returned a null pointer");
            }
            return new HipMemory(this, pointer, bytes);
        }
    }

    public void copyToDevice(HipMemory destination, byte[] source) {
        Objects.requireNonNull(source, "source");
        requireOwnedOpen(destination, source.length);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate(source.length);
            MemorySegment.copy(source, 0, host, ValueLayout.JAVA_BYTE, 0, source.length);
            copy(destination.address(), host, source.length, HipMemcpyKind.HOST_TO_DEVICE);
        }
    }

    public byte[] copyFromDevice(HipMemory source, int bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("Copy size must not be negative");
        }
        requireOwnedOpen(source, bytes);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment host = arena.allocate(bytes);
            copy(host, source.address(), bytes, HipMemcpyKind.DEVICE_TO_HOST);
            return host.toArray(ValueLayout.JAVA_BYTE);
        }
    }

    public void synchronize() {
        check("hipDeviceSynchronize", api.deviceSynchronize());
    }

    private void copy(MemorySegment destination, MemorySegment source, long bytes, HipMemcpyKind kind) {
        check("hipMemcpy", api.memcpy(destination, source, bytes, kind.nativeValue()));
    }

    private void requireOwnedOpen(HipMemory memory, long bytes) {
        Objects.requireNonNull(memory, "memory");
        if (memory.runtime() != this) {
            throw new IllegalArgumentException("HIP memory belongs to a different runtime");
        }
        memory.requireOpen();
        if (bytes > memory.byteSize()) {
            throw new IllegalArgumentException("Copy size exceeds the device allocation");
        }
    }

    void free(MemorySegment pointer) {
        check("hipFree", api.free(pointer));
    }

    private void check(String operation, int result) {
        if (result != 0) {
            throw new HipException(operation, result, api.errorString(result));
        }
    }
}
