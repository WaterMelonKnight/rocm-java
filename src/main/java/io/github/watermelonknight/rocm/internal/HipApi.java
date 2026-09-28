package io.github.watermelonknight.rocm.internal;

import java.lang.foreign.MemorySegment;

public interface HipApi {
    int getDeviceCount(MemorySegment count);
    int getDevice(MemorySegment device);
    int setDevice(int device);
    int malloc(MemorySegment pointer, long bytes);
    int free(MemorySegment pointer);
    int memcpy(MemorySegment destination, MemorySegment source, long bytes, int kind);
    int deviceSynchronize();
    String errorString(int error);
}
