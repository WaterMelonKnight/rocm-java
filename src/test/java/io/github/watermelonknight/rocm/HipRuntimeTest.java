package io.github.watermelonknight.rocm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.watermelonknight.rocm.internal.HipApi;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.Test;

class HipRuntimeTest {
    @Test
    void exposesOfficialMemcpyKindValues() {
        assertEquals(0, HipMemcpyKind.HOST_TO_HOST.nativeValue());
        assertEquals(1, HipMemcpyKind.HOST_TO_DEVICE.nativeValue());
        assertEquals(2, HipMemcpyKind.DEVICE_TO_HOST.nativeValue());
        assertEquals(3, HipMemcpyKind.DEVICE_TO_DEVICE.nativeValue());
        assertEquals(4, HipMemcpyKind.DEFAULT.nativeValue());
    }

    @Test
    void validatesAllocationAndDeviceArgumentsBeforeNativeCall() {
        HipRuntime runtime = HipRuntime.forTesting(new FakeApi());
        assertThrows(IllegalArgumentException.class, () -> runtime.malloc(0));
        assertThrows(IllegalArgumentException.class, () -> runtime.selectDevice(-1));
    }

    @Test
    void closesAllocationOnlyOnceAndRejectsUseAfterClose() {
        FakeApi api = new FakeApi();
        HipRuntime runtime = HipRuntime.forTesting(api);
        HipMemory memory = runtime.malloc(16);

        memory.close();
        memory.close();

        assertTrue(memory.isClosed());
        assertEquals(1, api.freeCalls);
        assertThrows(IllegalStateException.class, () -> runtime.copyToDevice(memory, new byte[1]));
    }

    @Test
    void remainsOpenWhenFreeFailsAndCanRetryClose() {
        FakeApi api = new FakeApi();
        api.freeResult = 101;
        HipMemory memory = HipRuntime.forTesting(api).malloc(16);

        assertThrows(HipException.class, memory::close);
        assertEquals(1, api.freeCalls);
        assertFalse(memory.isClosed());

        api.freeResult = 0;
        memory.close();
        memory.close();
        assertTrue(memory.isClosed());
        assertEquals(2, api.freeCalls);
    }

    @Test
    void validatesCopyBounds() {
        HipRuntime runtime = HipRuntime.forTesting(new FakeApi());
        try (HipMemory memory = runtime.malloc(4)) {
            assertThrows(IllegalArgumentException.class, () -> runtime.copyToDevice(memory, new byte[5]));
            assertThrows(IllegalArgumentException.class, () -> runtime.copyFromDevice(memory, 5));
        }
    }

    @Test
    void convertsHipFailuresToTypedException() {
        FakeApi api = new FakeApi();
        api.setDeviceResult = 101;
        HipException exception = assertThrows(HipException.class,
                () -> HipRuntime.forTesting(api).selectDevice(0));
        assertEquals(101, exception.errorCode());
        assertTrue(exception.getMessage().contains("fake error"));
    }

    @Test
    void hostDeviceRoundTripUsesExpectedDirections() {
        FakeApi api = new FakeApi();
        HipRuntime runtime = HipRuntime.forTesting(api);
        byte[] expected = {1, 2, 3, 4};
        try (HipMemory memory = runtime.malloc(expected.length)) {
            runtime.copyToDevice(memory, expected);
            assertArrayEquals(expected, runtime.copyFromDevice(memory, expected.length));
        }
        assertEquals(1, api.hostToDeviceCalls);
        assertEquals(1, api.deviceToHostCalls);
    }

    private static final class FakeApi implements HipApi {
        private final byte[] deviceMemory = new byte[1024];
        private int freeCalls;
        private int hostToDeviceCalls;
        private int deviceToHostCalls;
        private int setDeviceResult;
        private int freeResult;

        @Override public int getDeviceCount(MemorySegment count) {
            count.set(ValueLayout.JAVA_INT, 0, 1);
            return 0;
        }
        @Override public int getDevice(MemorySegment device) {
            device.set(ValueLayout.JAVA_INT, 0, 0);
            return 0;
        }
        @Override public int setDevice(int device) { return setDeviceResult; }
        @Override public int malloc(MemorySegment pointer, long bytes) {
            pointer.set(ValueLayout.ADDRESS, 0, MemorySegment.ofAddress(0x1000));
            return 0;
        }
        @Override public int free(MemorySegment pointer) { freeCalls++; return freeResult; }
        @Override public int memcpy(MemorySegment destination, MemorySegment source, long bytes, int kind) {
            if (kind == HipMemcpyKind.HOST_TO_DEVICE.nativeValue()) {
                MemorySegment.copy(source, 0, MemorySegment.ofArray(deviceMemory), 0, bytes);
                hostToDeviceCalls++;
            } else if (kind == HipMemcpyKind.DEVICE_TO_HOST.nativeValue()) {
                MemorySegment.copy(MemorySegment.ofArray(deviceMemory), 0, destination, 0, bytes);
                deviceToHostCalls++;
            }
            return 0;
        }
        @Override public int deviceSynchronize() { return 0; }
        @Override public String errorString(int error) { return "fake error"; }
    }
}
