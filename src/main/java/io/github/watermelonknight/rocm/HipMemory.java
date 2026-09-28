package io.github.watermelonknight.rocm;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An owning HIP device allocation. Always use try-with-resources; garbage collection is not a
 * substitute for calling {@link #close()}.
 */
public final class HipMemory implements AutoCloseable {
    private final HipRuntime runtime;
    private final MemorySegment address;
    private final long byteSize;
    private final AtomicBoolean closed = new AtomicBoolean();

    HipMemory(HipRuntime runtime, MemorySegment address, long byteSize) {
        this.runtime = runtime;
        this.address = address;
        this.byteSize = byteSize;
    }

    public long byteSize() {
        return byteSize;
    }

    public boolean isClosed() {
        return closed.get();
    }

    HipRuntime runtime() {
        return runtime;
    }

    MemorySegment address() {
        requireOpen();
        return address;
    }

    void requireOpen() {
        if (closed.get()) {
            throw new IllegalStateException("HIP memory has been closed");
        }
    }

    /** Frees this allocation exactly once. Repeated calls are harmless. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            runtime.free(address);
        }
    }
}
