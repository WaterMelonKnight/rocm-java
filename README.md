# rocm-java

Experimental Java-native bindings for AMD ROCm/HIP using the Java Foreign Function & Memory API.

Java has mature enterprise, backend, and agent ecosystems, while direct modern Java access to
ROCm is comparatively underdeveloped. This project investigates a deliberately direct path:

```text
Java -> Project Panama FFM -> HIP Runtime -> ROCm -> AMD GPU
```

## Current status

MVP-0/MVP-1 provides lazy HIP runtime loading, device selection, explicitly owned device memory,
and host-to-device-to-host copies. It does **not** launch kernels or bind device property structs.
CPU-safe tests use a fake, package-internal HIP boundary and do not claim to validate ROCm.

## Architecture

The single Maven module has a small public API (`HipRuntime`, `HipDevice`, `HipMemory`, and typed
errors) over an internal FFM downcall layer. `HipRuntime.open()` is the explicit loading point;
ordinary class loading, compilation, and unit tests do not load ROCm.

The loader checks `$ROCM_PATH/lib`, `/opt/rocm/lib`, `/opt/rocm/lib64`, and finally the operating
system library path for `libamdhip64.so`/`amdhip64`. Missing libraries and missing required symbols
produce `HipLibraryUnavailableException`; nonzero HIP results produce `HipException`; invalid
Java arguments produce standard argument/state exceptions.

## Requirements

* JDK 25
* Maven 3.9 or newer installed on `PATH` (no binary Maven wrapper is committed)
* Linux x86-64 or AArch64 for the currently documented 64-bit `size_t` ABI
* For the opt-in smoke test only: a supported AMD GPU, working ROCm installation, and access to
  `/dev/kfd`

## Build and CPU tests

```bash
mvn test
```

This command needs neither ROCm nor a GPU. GitHub Actions runs only this CPU-safe validation; a
green CPU CI result does not prove that ROCm integration works.

## ROCm smoke test

First inspect the host, then explicitly run the integration executable:

```bash
./scripts/check-rocm.sh
JDK_JAVA_OPTIONS="--enable-native-access=ALL-UNNAMED" mvn verify -Procm
```

The smoke test requires at least one device, selects device 0, allocates 4096 bytes, copies a known
byte pattern host -> device -> host, compares it exactly, and frees the allocation. A successful
run proves only this small runtime/memory path on that host. Real validation must run on compatible
AMD hardware; AMD Developer Cloud is an appropriate environment.

## Supported HIP APIs and verified native signatures

The bindings correspond to declarations in AMD's official
[`hip/hip_runtime_api.h`](https://github.com/ROCm/HIP/blob/develop/include/hip/hip_runtime_api.h)
(checked 2026-09-28):

| HIP API | Native declaration |
|---|---|
| `hipGetDeviceCount` | `hipError_t hipGetDeviceCount(int* count)` |
| `hipGetDevice` | `hipError_t hipGetDevice(int* deviceId)` |
| `hipSetDevice` | `hipError_t hipSetDevice(int deviceId)` |
| `hipMalloc` | `hipError_t hipMalloc(void** ptr, size_t size)` |
| `hipFree` | `hipError_t hipFree(void* ptr)` |
| `hipMemcpy` | `hipError_t hipMemcpy(void* dst, const void* src, size_t sizeBytes, hipMemcpyKind kind)` |
| `hipDeviceSynchronize` | `hipError_t hipDeviceSynchronize(void)` |
| `hipGetErrorString` | `const char* hipGetErrorString(hipError_t hipError)` |

`hipError_t` and `hipMemcpyKind` use C `int` carriers, pointers use FFM `ADDRESS`, and Linux
`size_t` uses a 64-bit Java `long`. Copy-kind values come from AMD's official
[`hip/driver_types.h`](https://github.com/ROCm/HIP/blob/develop/include/hip/driver_types.h): host-to-host
0, host-to-device 1, device-to-host 2, device-to-device 3, and default 4.

## Resource lifecycle

`HipMemory` owns one `hipMalloc` result and implements `AutoCloseable`:

```java
HipRuntime runtime = HipRuntime.open();
try (HipMemory memory = runtime.malloc(4096)) {
    runtime.copyToDevice(memory, input);
    byte[] output = runtime.copyFromDevice(memory, input.length);
}
```

After `close()` successfully invokes `hipFree`, repeated closes are harmless and operations reject
the closed allocation. A failed `hipFree` leaves the allocation open so closing can be retried.
Always use try-with-resources: Java garbage collection does **not** safely or promptly release GPU
memory.

## Current limitations

* Linux ROCm only; the FFM mapping intentionally assumes the supported 64-bit Linux ABI.
* No `hipDeviceProp_t`, GPU model reporting, asynchronous copies, streams, kernels, or BLAS.
* No native call is made by CPU unit tests; run the opt-in smoke test on actual AMD hardware.
* This is an experimental API with no compatibility guarantee yet.

## Roadmap (not implemented)

* **MVP-0:** HIP runtime loading (current)
* **MVP-1:** device and memory operations (current)
* **MVP-2:** HIP module/kernel launch and VectorAdd
* **MVP-3:** hipBLAS GEMM
* **MVP-4:** JMH/native C++ performance comparison
* **MVP-5:** small-model/structured-decision inference experiment
* **MVP-6:** ROCm profiling and a performance-optimization agent
