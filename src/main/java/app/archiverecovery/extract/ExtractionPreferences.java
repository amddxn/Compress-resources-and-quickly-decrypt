package app.archiverecovery.extract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

public final class ExtractionPreferences {
    private static final String BZ_PATH = "bzPath";
    private static final String OUTPUT_ROOT = "outputRoot";
    private static final String CONCURRENCY = "concurrency";
    private static final String MAX_NESTED_DEPTH = "maxNestedDepth";
    private final Preferences preferences = Preferences.userNodeForPackage(ExtractionPreferences.class);

    public String bzPath() {
        return preferences.get(BZ_PATH, "");
    }

    public String outputRoot() {
        return preferences.get(OUTPUT_ROOT, "");
    }

    public int concurrency() {
        return Math.max(1, Math.min(8, preferences.getInt(CONCURRENCY, 1)));
    }

    public int maxNestedDepth() {
        return Math.max(1, Math.min(50, preferences.getInt(MAX_NESTED_DEPTH, 10)));
    }

    public void save(ExtractionSettings settings) {
        preferences.put(BZ_PATH, settings.bzExecutable().toString());
        preferences.put(OUTPUT_ROOT, settings.outputRoot().toString());
        preferences.putInt(CONCURRENCY, settings.concurrency());
        preferences.putInt(MAX_NESTED_DEPTH, settings.maxNestedDepth());
    }

    public Optional<ExtractionSettings> loadSettings() {
        if (bzPath().isBlank() || outputRoot().isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ExtractionSettings(
                    Path.of(bzPath()), Path.of(outputRoot()), concurrency(), maxNestedDepth()));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    public static Optional<Path> findDefaultBzExecutable() {
        for (Path candidate : defaultBzLocations()) {
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate.toAbsolutePath().normalize());
            }
        }
        return Optional.empty();
    }

    public static List<Path> defaultBzLocations() {
        String programFiles = System.getenv("ProgramFiles");
        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        return java.util.stream.Stream.of(programFiles, programFilesX86)
                .filter(value -> value != null && !value.isBlank())
                .map(value -> Path.of(value, "Bandizip", "bz.exe"))
                .distinct()
                .toList();
    }
}
