package app.archiverecovery.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MultipartRenamePlanner {
    private static final Pattern SEVEN_ZIP =
            Pattern.compile("^(.+)\\.7z\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_NUMBERED =
            Pattern.compile("^(.+)\\.zip\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_Z =
            Pattern.compile("^(.+)\\.z(\\d{2})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_PART =
            Pattern.compile("^(.+)\\.part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_OLD =
            Pattern.compile("^(.+)\\.r(\\d{2,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PART_NUMBER_ANYWHERE =
            Pattern.compile("part(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRAILING_DIGITS = Pattern.compile("(\\d{3,})$");
    private static final Pattern NOISY_Z_NUMBER =
            Pattern.compile("^z.*?(\\d{2})$", Pattern.CASE_INSENSITIVE);

    Map<Path, Path> createTargets(Collection<Path> files, RenameMode mode) {
        return createTargets(files, mode, false);
    }

    Map<Path, Path> createSingleGroupTargets(Collection<Path> files, RenameMode mode) {
        return createTargets(files, mode, true);
    }

    private Map<Path, Path> createTargets(Collection<Path> files,
                                          RenameMode mode,
                                          boolean forceSingleGroup) {
        if (!mode.multipart()) {
            throw new IllegalArgumentException("仅分卷模式可以生成分卷目标名称");
        }

        List<Entry> entries = new ArrayList<>();
        for (Path path : files) {
            Path normalized = path.toAbsolutePath().normalize();
            entries.add(parseEntry(normalized, mode));
        }

        applyKnownBaseAnchors(entries);
        inferSharedBases(entries);
        applyKnownBaseAnchors(entries);
        applyFallbackBases(entries);
        if (forceSingleGroup) {
            forceSharedBase(entries);
            sortSingleGroupEntries(entries);
        }
        assignMissingNumbers(entries);

        Map<Path, Path> targets = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Path target = entry.preserveOriginal
                    ? entry.path
                    : entry.path.resolveSibling(officialName(entry.baseName, entry.number, mode));
            targets.put(entry.path, target);
        }
        return targets;
    }

    private void forceSharedBase(List<Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        String commonBase = entries.get(0).fileName;
        for (int index = 1; index < entries.size() && commonBase != null; index++) {
            commonBase = commonDotPrefix(commonBase, entries.get(index).fileName);
        }
        commonBase = sanitizeBase(commonBase);

        if (commonBase == null || commonBase.isBlank()) {
            commonBase = entries.stream()
                    .map(entry -> sanitizeBase(entry.baseName))
                    .filter(base -> base != null && !base.isBlank())
                    .min((first, second) -> Integer.compare(first.length(), second.length()))
                    .orElse("嵌套分卷");
        }
        for (Entry entry : entries) {
            entry.baseName = commonBase;
        }
    }

    private void sortSingleGroupEntries(List<Entry> entries) {
        entries.sort(Comparator
                .comparing((Entry entry) -> entry.number == null ? 1 : 0)
                .thenComparing(entry -> entry.number == null ? Integer.MAX_VALUE : entry.number)
                .thenComparing(Comparator.comparingLong(this::fileSize).reversed())
                .thenComparing(entry -> entry.path.toString(), String.CASE_INSENSITIVE_ORDER));
    }

    private long fileSize(Entry entry) {
        try {
            return Files.size(entry.path);
        } catch (java.io.IOException exception) {
            return -1;
        }
    }

    private Entry parseEntry(Path path, RenameMode mode) {
        String name = path.getFileName().toString();
        ParsedVolume parsed = parseCanonicalVolume(name);
        if (parsed != null) {
            return new Entry(path, name, sanitizeBase(parsed.baseName()), parsed.number(), false);
        }

        String extension = ExtensionService.extensionOf(name);
        if (mode == RenameMode.ZIP_MULTIPART && extension.equals("zip")) {
            return new Entry(path, name, sanitizeBase(baseNameOf(name)), 1, true);
        }
        if (mode == RenameMode.ZIP_MULTIPART
                && looksLikeDisturbedZipEntry(lastSegment(name))) {
            return new Entry(path, name, sanitizeBase(baseNameOf(name)), 1, false);
        }
        if (extension.equals("zip") || extension.equals("7z") || extension.equals("rar")) {
            return new Entry(path, name, sanitizeBase(baseNameOf(name)), null, false);
        }
        return new Entry(path, name, null, extractDisturbedNumber(name, mode), false);
    }

    private ParsedVolume parseCanonicalVolume(String name) {
        Matcher matcher = SEVEN_ZIP.matcher(name);
        if (matcher.matches()) {
            return parsed(matcher.group(1), matcher.group(2), 0);
        }
        matcher = ZIP_NUMBERED.matcher(name);
        if (matcher.matches()) {
            return parsed(matcher.group(1), matcher.group(2), 0);
        }
        matcher = ZIP_Z.matcher(name);
        if (matcher.matches()) {
            return parsed(matcher.group(1), matcher.group(2), 1);
        }
        matcher = RAR_PART.matcher(name);
        if (matcher.matches()) {
            return parsed(matcher.group(1), matcher.group(2), 0);
        }
        matcher = RAR_OLD.matcher(name);
        if (matcher.matches()) {
            return parsed(matcher.group(1), matcher.group(2), 2);
        }
        return null;
    }

    private ParsedVolume parsed(String baseName, String numberText, int offset) {
        Integer number = safeParseDigits(numberText);
        return number == null ? null : new ParsedVolume(baseName, number + offset);
    }

    private Integer extractDisturbedNumber(String name, RenameMode mode) {
        String suffix = lastSegment(name);

        Matcher part = PART_NUMBER_ANYWHERE.matcher(name);
        if (part.find()) {
            return safeParseDigits(part.group(1));
        }

        Matcher noisyZ = NOISY_Z_NUMBER.matcher(suffix);
        if (noisyZ.matches()) {
            Integer suffixNumber = safeParseDigits(noisyZ.group(1));
            return suffixNumber == null ? null : suffixNumber + 1;
        }

        if (mode == RenameMode.ZIP_MULTIPART
                && suffix.toLowerCase(Locale.ROOT).startsWith("z")) {
            String separatedDigits = suffix.replaceAll("\\D", "");
            if (separatedDigits.length() == 1 || separatedDigits.length() == 2) {
                Integer suffixNumber = safeParseDigits(separatedDigits);
                return suffixNumber == null ? null : suffixNumber + 1;
            }
        }

        if (suffix.chars().allMatch(Character::isDigit) && suffix.length() >= 3) {
            return safeParseDigits(suffix);
        }

        Matcher trailing = TRAILING_DIGITS.matcher(suffix);
        if (trailing.find()) {
            return safeParseDigits(trailing.group(1));
        }

        String digitsOnly = suffix.replaceAll("\\D", "");
        if (digitsOnly.length() >= 3) {
            return safeParseDigits(digitsOnly);
        }
        return null;
    }

    private boolean looksLikeDisturbedZipEntry(String suffix) {
        String lettersOnly = suffix.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        int z = lettersOnly.indexOf('z');
        int i = z < 0 ? -1 : lettersOnly.indexOf('i', z + 1);
        int p = i < 0 ? -1 : lettersOnly.indexOf('p', i + 1);
        return z == 0 && i > z && p > i;
    }

    private void applyKnownBaseAnchors(List<Entry> entries) {
        List<BaseAnchor> anchors = entries.stream()
                .filter(entry -> entry.baseName != null)
                .map(entry -> new BaseAnchor(entry.path.getParent(), entry.baseName))
                .distinct()
                .toList();

        for (Entry entry : entries) {
            if (entry.baseName != null) {
                continue;
            }
            BaseAnchor anchor = bestAnchor(entry, anchors);
            if (anchor != null) {
                entry.baseName = anchor.baseName();
            }
        }
    }

    private BaseAnchor bestAnchor(Entry entry, List<BaseAnchor> anchors) {
        BaseAnchor best = null;
        for (BaseAnchor anchor : anchors) {
            if (!sameDirectory(entry.path.getParent(), anchor.parent())) {
                continue;
            }
            boolean exact = entry.fileName.equalsIgnoreCase(anchor.baseName());
            boolean prefix = entry.fileName.regionMatches(true, 0, anchor.baseName(),
                    0, anchor.baseName().length())
                    && entry.fileName.length() > anchor.baseName().length()
                    && entry.fileName.charAt(anchor.baseName().length()) == '.';
            if ((exact || prefix) && (best == null
                    || anchor.baseName().length() > best.baseName().length())) {
                best = anchor;
            }
        }
        return best;
    }

    private void inferSharedBases(List<Entry> entries) {
        Map<Entry, Set<String>> candidates = new HashMap<>();
        for (int firstIndex = 0; firstIndex < entries.size(); firstIndex++) {
            Entry first = entries.get(firstIndex);
            if (first.baseName != null) {
                continue;
            }
            for (int secondIndex = firstIndex + 1; secondIndex < entries.size(); secondIndex++) {
                Entry second = entries.get(secondIndex);
                if (second.baseName != null || !sameDirectory(first.path.getParent(), second.path.getParent())) {
                    continue;
                }
                if (first.number == null && second.number == null) {
                    continue;
                }

                String commonBase = sanitizeBase(commonDotPrefix(first.fileName, second.fileName));
                if (commonBase == null || commonBase.isBlank()) {
                    continue;
                }
                if (first.number == null && !first.fileName.equalsIgnoreCase(commonBase)) {
                    continue;
                }
                if (second.number == null && !second.fileName.equalsIgnoreCase(commonBase)) {
                    continue;
                }
                candidates.computeIfAbsent(first, ignored -> new HashSet<>()).add(commonBase);
                candidates.computeIfAbsent(second, ignored -> new HashSet<>()).add(commonBase);
            }
        }

        for (Map.Entry<Entry, Set<String>> candidateEntry : candidates.entrySet()) {
            candidateEntry.getKey().baseName = candidateEntry.getValue().stream()
                    .max((first, second) -> Integer.compare(first.length(), second.length()))
                    .orElse(null);
        }
    }

    private String commonDotPrefix(String first, String second) {
        String[] firstParts = first.split("\\.");
        String[] secondParts = second.split("\\.");
        int commonCount = 0;
        while (commonCount < firstParts.length && commonCount < secondParts.length
                && firstParts[commonCount].equalsIgnoreCase(secondParts[commonCount])) {
            commonCount++;
        }
        if (commonCount == 0) {
            return null;
        }
        return String.join(".", java.util.Arrays.copyOf(firstParts, commonCount));
    }

    private void applyFallbackBases(List<Entry> entries) {
        for (Entry entry : entries) {
            if (entry.baseName != null) {
                continue;
            }
            String base = baseNameOf(entry.fileName);
            if (entry.number != null) {
                base = sanitizeBase(base);
            }
            entry.baseName = base == null || base.isBlank() ? entry.fileName : base;
        }
    }

    private void assignMissingNumbers(List<Entry> entries) {
        Map<String, Set<Integer>> reservedByGroup = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.number != null && entry.number > 0) {
                reservedByGroup.computeIfAbsent(groupKey(entry), ignored -> new HashSet<>()).add(entry.number);
            }
        }

        Map<String, Set<Integer>> seenExplicitByGroup = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.preserveOriginal && entry.number != null && entry.number > 0) {
                seenExplicitByGroup.computeIfAbsent(groupKey(entry), ignored -> new HashSet<>())
                        .add(entry.number);
            }
        }
        Map<String, Set<Integer>> assignedByGroup = new HashMap<>();
        for (Entry entry : entries) {
            if (entry.preserveOriginal) {
                continue;
            }
            String key = groupKey(entry);
            Set<Integer> assigned = assignedByGroup.computeIfAbsent(key, ignored -> new HashSet<>());
            Set<Integer> seenExplicit = seenExplicitByGroup.computeIfAbsent(key, ignored -> new HashSet<>());
            boolean uniqueExplicit = entry.number != null && entry.number > 0
                    && seenExplicit.add(entry.number);
            if (!uniqueExplicit) {
                Set<Integer> unavailable = new HashSet<>(reservedByGroup.getOrDefault(key, Set.of()));
                unavailable.addAll(assigned);
                entry.number = firstAvailable(unavailable);
            }
            assigned.add(entry.number);
        }
    }

    private int firstAvailable(Set<Integer> used) {
        int number = 1;
        while (used.contains(number)) {
            number++;
        }
        return number;
    }

    private String officialName(String baseName, int number, RenameMode mode) {
        return switch (mode) {
            case SEVEN_ZIP_MULTIPART -> baseName + ".7z." + String.format(Locale.ROOT, "%03d", number);
            case ZIP_MULTIPART -> number == 1
                    ? baseName + ".zip"
                    : baseName + ".z" + String.format(Locale.ROOT, "%02d", number - 1);
            case RAR_MULTIPART -> baseName + ".part" + number + ".rar";
            default -> throw new IllegalArgumentException("当前模式不是分卷模式");
        };
    }

    private String sanitizeBase(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return baseName;
        }
        String result = baseName;
        String lower = result.toLowerCase(Locale.ROOT);
        for (String suffix : List.of(".7z", ".zip", ".rar")) {
            if (lower.endsWith(suffix)) {
                result = result.substring(0, result.length() - suffix.length());
                lower = result.toLowerCase(Locale.ROOT);
                break;
            }
        }

        int lastDot = result.lastIndexOf('.');
        if (lastDot > 0 && looksLikeDisturbedArchiveMarker(result.substring(lastDot + 1))) {
            result = result.substring(0, lastDot);
        }
        return result;
    }

    private boolean looksLikeDisturbedArchiveMarker(String segment) {
        String lower = segment.toLowerCase(Locale.ROOT);
        String lettersOnly = lower.replaceAll("[^a-z]", "");
        return lettersOnly.equals("zip")
                || lettersOnly.equals("rar")
                || (lower.indexOf('7') >= 0 && lower.indexOf('z') > lower.indexOf('7'));
    }

    private String baseNameOf(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private String lastSegment(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot >= 0 ? fileName.substring(lastDot + 1) : fileName;
    }

    private Integer safeParseDigits(String text) {
        try {
            int value = Integer.parseInt(text);
            return value > 0 ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean sameDirectory(Path first, Path second) {
        Path normalizedFirst = first == null ? Path.of("").toAbsolutePath().normalize()
                : first.toAbsolutePath().normalize();
        Path normalizedSecond = second == null ? Path.of("").toAbsolutePath().normalize()
                : second.toAbsolutePath().normalize();
        return normalizedFirst.equals(normalizedSecond);
    }

    private String groupKey(Entry entry) {
        Path parent = entry.path.getParent();
        String directory = parent == null ? "" : parent.toString();
        return (directory + "\u0000" + entry.baseName).toLowerCase(Locale.ROOT);
    }

    private static final class Entry {
        private final Path path;
        private final String fileName;
        private final boolean preserveOriginal;
        private String baseName;
        private Integer number;

        private Entry(Path path, String fileName, String baseName, Integer number,
                      boolean preserveOriginal) {
            this.path = path;
            this.fileName = fileName;
            this.baseName = baseName;
            this.number = number;
            this.preserveOriginal = preserveOriginal;
        }
    }

    private record ParsedVolume(String baseName, int number) {
    }

    private record BaseAnchor(Path parent, String baseName) {
    }
}
