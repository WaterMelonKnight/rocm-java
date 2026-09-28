package io.github.watermelonknight.rocm.example;

import io.github.watermelonknight.rocm.HipMemory;
import io.github.watermelonknight.rocm.HipRuntime;
import java.util.Arrays;

/** Explicit, GPU-dependent end-to-end diagnostic; not part of the unit-test task. */
public final class RocmSmokeTest {
    private RocmSmokeTest() {}

    public static void main(String[] args) {
        HipRuntime runtime = HipRuntime.open();
        System.out.println("ROCm available");
        int devices = runtime.deviceCount();
        System.out.println("HIP devices: " + devices);
        if (devices < 1) {
            throw new IllegalStateException("ROCm loaded, but no HIP device is available");
        }
        runtime.selectDevice(0);
        System.out.println("Selected device: 0");

        byte[] expected = new byte[4096];
        for (int index = 0; index < expected.length; index++) {
            expected[index] = (byte) (index * 31);
        }
        try (HipMemory memory = runtime.malloc(expected.length)) {
            System.out.println("Allocated: " + memory.byteSize() + " bytes");
            runtime.copyToDevice(memory, expected);
            runtime.synchronize();
            byte[] actual = runtime.copyFromDevice(memory, expected.length);
            if (!Arrays.equals(expected, actual)) {
                throw new AssertionError("Host -> Device -> Host contents differ");
            }
        }
        System.out.println("Host -> Device -> Host verification: PASS");
    }
}
