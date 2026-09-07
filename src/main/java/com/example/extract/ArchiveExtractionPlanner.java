package com.example.extract;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ArchiveExtractionPlanner {
    private static final Pattern SEVEN_ZIP_VOLUME =
            Pattern.compile("^(.+)\\.7z\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_VOLUME =
            Pattern.compile("^(.+)\\.part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);

    List<ExtractionTask> createTasks(Collection<Path> candidates, Path outputRoot) {
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

        Map<String, Integer> folderOccurrences = new HashMap<>();
        List<ExtractionTask> tasks = new ArrayList<>();
        for (ArchiveEntry entry : entries) {
            String folderKey = entry.baseName().toLowerCase(Locale.ROOT);
            int occurrence = folderOccurrences.merge(folderKey, 1, Integer::sum);
            String folderName = occurrence == 1
                    ? entry.baseName()
                    : entry.baseName() + " (" + occurrence + ")";
            tasks.add(new ExtractionTask(entry.path(), outputRoot.resolve(folderName)));
        }
        return tasks;
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

    private boolean isFirstNumber(String number) {
        try {
            return Integer.parseInt(number) == 1;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private String pathKey(Path path) {
        return path.toString().toLowerCase(Locale.ROOT);
    }

    private record ArchiveEntry(Path path, String baseName) {
    }
}
