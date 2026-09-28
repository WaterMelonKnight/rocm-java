package io.github.watermelonknight.rocm.internal;

import io.github.watermelonknight.rocm.HipLibraryUnavailableException;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * Minimal Linux HIP ABI binding verified against {@code hip/hip_runtime_api.h}.
 * {@code size_t} maps to a 64-bit Java long on supported 64-bit ROCm Linux systems;
 * pointer and pointer-to-pointer arguments both map to ADDRESS carriers.
 */
public final class HipNative implements HipApi {
    private static final FunctionDescriptor INT_POINTER = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS);
    private static final FunctionDescriptor INT_INT = FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT);
    private static final FunctionDescriptor INT_VOID = FunctionDescriptor.of(ValueLayout.JAVA_INT);

    private final MethodHandle getDeviceCount;
    private final MethodHandle getDevice;
    private final MethodHandle setDevice;
    private final MethodHandle malloc;
    private final MethodHandle free;
    private final MethodHandle memcpy;
    private final MethodHandle synchronize;
    private final MethodHandle getErrorString;

    public HipNative(SymbolLookup symbols) {
        Linker linker = Linker.nativeLinker();
        getDeviceCount = downcall(linker, symbols, "hipGetDeviceCount", INT_POINTER);
        getDevice = downcall(linker, symbols, "hipGetDevice", INT_POINTER);
        setDevice = downcall(linker, symbols, "hipSetDevice", INT_INT);
        // hipMalloc(void**, size_t): the first ADDRESS points to writable pointer storage.
        malloc = downcall(linker, symbols, "hipMalloc", FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
        free = downcall(linker, symbols, "hipFree", INT_POINTER);
        memcpy = downcall(linker, symbols, "hipMemcpy", FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
        synchronize = downcall(linker, symbols, "hipDeviceSynchronize", INT_VOID);
        getErrorString = downcall(linker, symbols, "hipGetErrorString", FunctionDescriptor.of(
                ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    }

    public static HipNative load() {
        return new HipNative(HipLibrary.load());
    }

    private static MethodHandle downcall(Linker linker, SymbolLookup symbols, String name, FunctionDescriptor descriptor) {
        MemorySegment symbol = symbols.find(name).orElseThrow(() -> new HipLibraryUnavailableException(
                "Loaded HIP library does not export required symbol " + name, null));
        return linker.downcallHandle(symbol, descriptor);
    }

    private static int invokeInt(MethodHandle handle, Object... arguments) {
        try {
            return (int) handle.invokeWithArguments(arguments);
        } catch (Throwable failure) {
            throw new IllegalStateException("HIP native invocation failed", failure);
        }
    }

    @Override public int getDeviceCount(MemorySegment count) { return invokeInt(getDeviceCount, count); }
    @Override public int getDevice(MemorySegment device) { return invokeInt(getDevice, device); }
    @Override public int setDevice(int device) { return invokeInt(setDevice, device); }
    @Override public int malloc(MemorySegment pointer, long bytes) { return invokeInt(malloc, pointer, bytes); }
    @Override public int free(MemorySegment pointer) { return invokeInt(free, pointer); }
    @Override public int memcpy(MemorySegment destination, MemorySegment source, long bytes, int kind) {
        return invokeInt(memcpy, destination, source, bytes, kind);
    }
    @Override public int deviceSynchronize() { return invokeInt(synchronize); }

    @Override
    public String errorString(int error) {
        try {
            MemorySegment pointer = (MemorySegment) getErrorString.invokeExact(error);
            return pointer.equals(MemorySegment.NULL) ? "unknown HIP error" : pointer.reinterpret(4096).getString(0);
        } catch (Throwable failure) {
            return "unable to obtain HIP error text (" + failure.getClass().getSimpleName() + ")";
        }
    }
}
