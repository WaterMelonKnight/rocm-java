package io.github.watermelonknight.rocm;

import java.lang.foreign.MemorySegment;

/**
 * An owning HIP device allocation. Always use try-with-resources; garbage collection is not a
 * substitute for calling {@link #close()}.
 */
public final class HipMemory implements AutoCloseable {
    private final HipRuntime runtime;
    private final MemorySegment address;
    private final long byteSize;
    private volatile boolean closed;

    HipMemory(HipRuntime runtime, MemorySegment address, long byteSize) {
        this.runtime = runtime;
        this.address = address;
        this.byteSize = byteSize;
    }

    public long byteSize() {
        return byteSize;
    }

    public boolean isClosed() {
        return closed;
    }

    HipRuntime runtime() {
        return runtime;
    }

    MemorySegment address() {
        requireOpen();
        return address;
    }

    void requireOpen() {
        if (closed) {
            throw new IllegalStateException("HIP memory has been closed");
        }
    }

    /** Frees this allocation; after a successful free, repeated calls are harmless. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        runtime.free(address);
        closed = true;
    }
}
