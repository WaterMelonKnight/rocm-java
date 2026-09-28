# Agent guidance

* Maven is the canonical build system. Do not introduce Gradle unless explicitly requested.
* Target Java 25 unless a scoped change intentionally changes the baseline.
* Prefer the Java Foreign Function & Memory API. Do not add JNI or JNA unless explicitly approved.
* Verify every native signature and constant against official ROCm headers or AMD documentation.
* Keep CPU tests runnable without ROCm; every ROCm/GPU test must be explicitly opt-in.
* Never fabricate benchmark results or claim GPU tests passed unless they ran on compatible AMD hardware.
* Do not commit generated binaries, shared libraries, HSACO files, or build artifacts.
* Do not add a Maven wrapper binary to a Codex Cloud-created pull request.
* Make native resource ownership explicit; never imply that GC safely releases GPU resources.
* Never silently ignore HIP error codes.
* Avoid broad generated API surfaces and premature abstractions without a scoped task.
* Do not add inference, Spring, LLM/agent, MCP, or hipBLAS functionality unless explicitly requested.
