package com.example.extract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArchiveExtractionPlanner {
    private static final Pattern SEVEN_ZIP_VOLUME =
            Pattern.compile("^(.+)\\.7z\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_VOLUME =
            Pattern.compile("^(.+)\\.part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);

    List<ExtractionTask> createTasks(Collection<Path> candidates, Path outputRoot) {
        List<ArchiveEntry> entries = detectEntries(candidates);
        Set<String> reservedOutputs = new HashSet<>();
        List<ExtractionTask> tasks = new ArrayList<>();
        for (ArchiveEntry entry : entries) {
            Path output = uniqueOutputDirectory(outputRoot, entry.baseName(), reservedOutputs);
            tasks.add(new ExtractionTask(entry.path(), output, 0));
        }
        return tasks;
    }

    List<ExtractionTask> createNestedTasks(Collection<Path> extractedRoots,
                                           int nestedDepth) {
        List<Path> candidates = new ArrayList<>();
        Set<String> visitedRoots = new HashSet<>();
        for (Path root : extractedRoots) {
            Path normalized = root.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalized) || !visitedRoots.add(pathKey(normalized))) {
                continue;
            }
            try (var paths = Files.walk(normalized)) {
                paths.filter(Files::isRegularFile).forEach(candidates::add);
            } catch (java.io.IOException ignored) {
                // 某个输出目录无法读取时跳过它，不影响其他已完成的解压任务。
            }
        }

        List<ArchiveEntry> entries = detectEntries(candidates);
        Set<String> reservedOutputs = new HashSet<>();
        List<ExtractionTask> tasks = new ArrayList<>();
        for (ArchiveEntry entry : entries) {
            Path parent = entry.path().getParent();
            Path output = uniqueOutputDirectory(parent, entry.baseName(), reservedOutputs);
            tasks.add(new ExtractionTask(entry.path(), output, nestedDepth));
        }
        return tasks;
    }

    private List<ArchiveEntry> detectEntries(Collection<Path> candidates) {
        List<ArchiveEntry> entries = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized) || !seenPaths.add(pathKey(normalized))) {
                continue;
            }
            ArchiveEntry entry = detectEntry(normalized);
            if (entry != null) {
                entries.add(entry);
            }
        }
        return entries;
    }

    private ArchiveEntry detectEntry(Path path) {
        String name = path.getFileName().toString();
        Matcher sevenZipVolume = SEVEN_ZIP_VOLUME.matcher(name);
        if (sevenZipVolume.matches()) {
            return isFirstNumber(sevenZipVolume.group(2))
                    ? new ArchiveEntry(path, sevenZipVolume.group(1))
                    : null;
        }

        Matcher rarVolume = RAR_VOLUME.matcher(name);
        if (rarVolume.matches()) {
            return isFirstNumber(rarVolume.group(2))
                    ? new ArchiveEntry(path, rarVolume.group(1))
                    : null;
        }

        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            return new ArchiveEntry(path, name.substring(0, name.length() - 4));
        }
        if (lower.endsWith(".7z")) {
            return new ArchiveEntry(path, name.substring(0, name.length() - 3));
        }
        if (lower.endsWith(".rar")) {
            return new ArchiveEntry(path, name.substring(0, name.length() - 4));
        }
        return null;
    }

    private Path uniqueOutputDirectory(Path parent, String baseName, Set<String> reservedOutputs) {
        String safeBaseName = baseName.isBlank() ? "解压结果" : baseName;
        int occurrence = 1;
        while (true) {
            String folderName = occurrence == 1
                    ? safeBaseName
                    : safeBaseName + " (" + occurrence + ")";
            Path candidate = parent.resolve(folderName).toAbsolutePath().normalize();
            String key = pathKey(candidate);
            if (!Files.exists(candidate) && reservedOutputs.add(key)) {
                return candidate;
            }
            occurrence++;
        }
    }

    private boolean isFirstNumber(String number) {
        try {
            return Integer.parseInt(number) == 1;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private record ArchiveEntry(Path path, String baseName) {
    }
}
