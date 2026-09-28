package io.github.watermelonknight.rocm.internal;

import io.github.watermelonknight.rocm.HipLibraryUnavailableException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lazily locates libamdhip64; merely loading a public API class never loads ROCm. */
public final class HipLibrary {
    private HipLibrary() {}

    public static SymbolLookup load() {
        List<String> attempts = new ArrayList<>();
        List<Path> paths = new ArrayList<>();
        String rocmPath = System.getenv("ROCM_PATH");
        if (rocmPath != null && !rocmPath.isBlank()) {
            paths.add(Path.of(rocmPath, "lib", "libamdhip64.so"));
        }
        paths.add(Path.of("/opt/rocm/lib/libamdhip64.so"));
        paths.add(Path.of("/opt/rocm/lib64/libamdhip64.so"));

        Throwable last = null;
        for (Path path : paths) {
            attempts.add(path.toString());
            if (Files.isRegularFile(path)) {
                try {
                    return SymbolLookup.libraryLookup(path, Arena.global());
                } catch (Throwable failure) {
                    last = failure;
                }
            }
        }
        attempts.add("amdhip64 (system library path)");
        try {
            return SymbolLookup.libraryLookup("amdhip64", Arena.global());
        } catch (Throwable failure) {
            last = failure;
        }
        throw new HipLibraryUnavailableException(
                "Unable to load libamdhip64.so; tried " + String.join(", ", attempts)
                        + ". Set ROCM_PATH or the system library path to a ROCm installation.", last);
    }
}
