package com.example.extract;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
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

final class ArchiveExtractionPlanner {
    private static final Pattern SEVEN_ZIP_VOLUME =
            Pattern.compile("^(.+)\\.7z\\.(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_VOLUME =
            Pattern.compile("^(.+)\\.z(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_VOLUME =
            Pattern.compile("^(.+)\\.part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);
    private static final byte[] SEVEN_ZIP_SIGNATURE =
            {(byte) 0x37, (byte) 0x7A, (byte) 0xBC, (byte) 0xAF, (byte) 0x27, (byte) 0x1C};
    private static final byte[] RAR4_SIGNATURE =
            {(byte) 0x52, (byte) 0x61, (byte) 0x72, (byte) 0x21, (byte) 0x1A, (byte) 0x07, (byte) 0x00};
    private static final byte[] RAR5_SIGNATURE =
            {(byte) 0x52, (byte) 0x61, (byte) 0x72, (byte) 0x21, (byte) 0x1A, (byte) 0x07, (byte) 0x01, (byte) 0x00};
    private static final byte[] ZIP_LOCAL_SIGNATURE =
            {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04};
    private static final byte[] ZIP_SPLIT_SIGNATURE =
            {(byte) 0x50, (byte) 0x4B, (byte) 0x07, (byte) 0x08};
    private static final byte[] ZIP_END_SIGNATURE =
            {(byte) 0x50, (byte) 0x4B, (byte) 0x05, (byte) 0x06};
    private static final byte[] ZIP64_END_SIGNATURE =
            {(byte) 0x50, (byte) 0x4B, (byte) 0x06, (byte) 0x06};
    private static final int ZIP_TAIL_SCAN_BYTES = 1024 * 1024;
    private static final String COLLECTION_FOLDER = "_分卷整理";

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
        return createNestedPlan(extractedRoots, nestedDepth).tasks();
    }

    NestedArchivePlan createNestedPlan(Collection<Path> extractedRoots,
                                       int nestedDepth) {
        Set<String> visitedRoots = new HashSet<>();
        Set<String> reservedOutputs = new HashSet<>();
        List<ExtractionTask> tasks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<Path> scannedFiles = new ArrayList<>();
        Set<String> seenScannedFiles = new HashSet<>();
        Set<String> recognizedArchiveFiles = new HashSet<>();
        int consolidatedGroups = 0;
        int movedVolumes = 0;

        for (Path root : extractedRoots) {
            Path normalized = root.toAbsolutePath().normalize();
            if (!Files.isDirectory(normalized) || !visitedRoots.add(pathKey(normalized))) {
                continue;
            }

            List<Path> candidates;
            try {
                candidates = scanFiles(normalized);
            } catch (IOException exception) {
                warnings.add("无法扫描目录 " + normalized + "：" + exception.getMessage());
                continue;
            }

            ConsolidationResult consolidation = consolidateMultipartGroups(normalized, candidates);
            consolidatedGroups += consolidation.groupCount();
            movedVolumes += consolidation.movedFileCount();
            warnings.addAll(consolidation.warnings());
            if (consolidation.movedFileCount() > 0) {
                try {
                    candidates = scanFiles(normalized);
                } catch (IOException exception) {
                    warnings.add("整理分卷后无法重新扫描目录 " + normalized + "：" + exception.getMessage());
                    continue;
                }
            }

            for (Path candidate : candidates) {
                String key = pathKey(candidate);
                if (seenScannedFiles.add(key)) {
                    scannedFiles.add(candidate);
                }
                if (isRecognizedArchiveName(candidate.getFileName().toString())) {
                    recognizedArchiveFiles.add(key);
                }
            }

            for (ArchiveEntry entry : detectEntries(candidates)) {
                Path parent = entry.path().getParent();
                Path output = uniqueOutputDirectory(parent, entry.baseName(), reservedOutputs);
                tasks.add(new ExtractionTask(entry.path(), output, nestedDepth));
            }
        }
        return new NestedArchivePlan(tasks, consolidatedGroups, movedVolumes, warnings,
                scannedFiles, recognizedArchiveFiles);
    }

    private List<Path> scanFiles(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private ConsolidationResult consolidateMultipartGroups(Path root, List<Path> candidates) {
        Map<GroupKey, MultipartGroup> groups = new LinkedHashMap<>();
        Map<String, List<Path>> zipEntries = new HashMap<>();
        for (Path candidate : candidates) {
            String name = candidate.getFileName().toString();
            Matcher sevenZip = SEVEN_ZIP_VOLUME.matcher(name);
            Matcher zip = ZIP_VOLUME.matcher(name);
            Matcher rar = RAR_VOLUME.matcher(name);
            if (sevenZip.matches()) {
                addNumberedPart(groups, MultipartFormat.SEVEN_ZIP,
                        sevenZip.group(1), sevenZip.group(2), candidate);
            } else if (zip.matches()) {
                addNumberedPart(groups, MultipartFormat.ZIP,
                        zip.group(1), zip.group(2), candidate);
            } else if (rar.matches()) {
                addNumberedPart(groups, MultipartFormat.RAR,
                        rar.group(1), rar.group(2), candidate);
            } else if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
                String baseName = name.substring(0, name.length() - 4);
                zipEntries.computeIfAbsent(normalizedName(baseName), ignored -> new ArrayList<>())
                        .add(candidate);
            }
        }

        for (MultipartGroup group : groups.values()) {
            if (group.format() == MultipartFormat.ZIP) {
                List<Path> matchingEntries = zipEntries.getOrDefault(
                        normalizedName(group.baseName()), List.of());
                for (Path entry : matchingEntries) {
                    group.addSpecialEntry(entry);
                }
            }
        }

        int groupCount = 0;
        int movedFileCount = 0;
        List<String> warnings = new ArrayList<>();
        for (MultipartGroup group : groups.values()) {
            if (!group.isSpreadAcrossDirectories()) {
                continue;
            }
            String invalidReason = validateGroup(group);
            if (invalidReason != null) {
                warnings.add(group.displayName() + " 未迁移：" + invalidReason);
                continue;
            }
            try {
                int moved = moveGroup(root, group);
                groupCount++;
                movedFileCount += moved;
            } catch (IOException exception) {
                warnings.add(group.displayName() + " 迁移失败：" + exception.getMessage());
            }
        }
        return new ConsolidationResult(groupCount, movedFileCount, warnings);
    }

    private void addNumberedPart(Map<GroupKey, MultipartGroup> groups,
                                 MultipartFormat format,
                                 String baseName,
                                 String number,
                                 Path path) {
        GroupKey key = new GroupKey(format, normalizedName(baseName));
        MultipartGroup group = groups.computeIfAbsent(key,
                ignored -> new MultipartGroup(format, baseName));
        group.addNumberedPart(normalizeNumber(number), path);
    }

    private String validateGroup(MultipartGroup group) {
        if (group.hasDuplicateNumbers()) {
            return "存在重复的分卷编号，无法确定文件归属";
        }
        if (group.allPaths().size() < 2) {
            return "没有找到至少两个可归组的分卷";
        }
        if (group.format() == MultipartFormat.ZIP) {
            if (group.specialEntries().size() != 1) {
                return "ZIP 分卷必须有且只有一个同名 .zip 入口卷";
            }
            Path firstVolume = group.numberedParts().get(BigInteger.ONE);
            if (firstVolume == null) {
                return "缺少 ZIP 首卷 .z01";
            }
            if (!hasZipFirstVolumeSignature(firstVolume)
                    || !hasZipEndSignature(group.specialEntries().get(0))) {
                return "入口文件不具备 ZIP 分卷特征";
            }
            return null;
        }

        Path firstVolume = group.numberedParts().get(BigInteger.ONE);
        if (firstVolume == null) {
            return group.format() == MultipartFormat.SEVEN_ZIP
                    ? "缺少 7Z 首卷 .7z.001"
                    : "缺少 RAR 首卷 .part1.rar";
        }
        if (group.numberedParts().size() < 2) {
            return "没有找到至少两个不同编号的分卷";
        }
        if (group.format() == MultipartFormat.SEVEN_ZIP
                && !startsWith(firstVolume, SEVEN_ZIP_SIGNATURE)) {
            return "首卷不具备 7Z 文件特征";
        }
        if (group.format() == MultipartFormat.RAR
                && !startsWith(firstVolume, RAR4_SIGNATURE)
                && !startsWith(firstVolume, RAR5_SIGNATURE)) {
            return "首卷不具备 RAR 文件特征";
        }
        return null;
    }

    private int moveGroup(Path root, MultipartGroup group) throws IOException {
        Path collectionRoot = root.resolve(COLLECTION_FOLDER);
        Files.createDirectories(collectionRoot);
        Path destinationDirectory = uniqueCollectionDirectory(
                collectionRoot, group.baseName() + "-" + group.format().folderSuffix());
        Files.createDirectory(destinationDirectory);

        List<MoveRecord> moved = new ArrayList<>();
        try {
            List<Path> paths = new ArrayList<>(group.allPaths());
            paths.sort(Comparator.comparing(path -> path.getFileName().toString(),
                    String.CASE_INSENSITIVE_ORDER));
            for (Path source : paths) {
                Path destination = destinationDirectory.resolve(source.getFileName());
                if (Files.exists(destination)) {
                    throw new IOException("整理目录中存在同名文件：" + destination.getFileName());
                }
                Files.move(source, destination);
                moved.add(new MoveRecord(source, destination));
            }
            return moved.size();
        } catch (IOException moveFailure) {
            for (int index = moved.size() - 1; index >= 0; index--) {
                MoveRecord record = moved.get(index);
                try {
                    Files.move(record.destination(), record.source());
                } catch (IOException rollbackFailure) {
                    moveFailure.addSuppressed(rollbackFailure);
                }
            }
            try {
                Files.deleteIfExists(destinationDirectory);
                deleteIfEmpty(collectionRoot);
            } catch (IOException cleanupFailure) {
                moveFailure.addSuppressed(cleanupFailure);
            }
            throw moveFailure;
        }
    }

    private Path uniqueCollectionDirectory(Path parent, String folderName) {
        int occurrence = 1;
        while (true) {
            String candidateName = occurrence == 1
                    ? folderName
                    : folderName + " (" + occurrence + ")";
            Path candidate = parent.resolve(candidateName).toAbsolutePath().normalize();
            if (!Files.exists(candidate)) {
                return candidate;
            }
            occurrence++;
        }
    }

    private void deleteIfEmpty(Path directory) throws IOException {
        try (var children = Files.list(directory)) {
            if (children.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
            }
        }
    }

    private boolean hasZipFirstVolumeSignature(Path path) {
        return startsWith(path, ZIP_SPLIT_SIGNATURE) || startsWith(path, ZIP_LOCAL_SIGNATURE);
    }

    private boolean hasZipEndSignature(Path path) {
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length();
            int bytesToRead = (int) Math.min(length, ZIP_TAIL_SCAN_BYTES);
            byte[] tail = new byte[bytesToRead];
            file.seek(length - bytesToRead);
            file.readFully(tail);
            return contains(tail, ZIP_END_SIGNATURE) || contains(tail, ZIP64_END_SIGNATURE);
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean startsWith(Path path, byte[] signature) {
        try (var input = Files.newInputStream(path)) {
            byte[] actual = input.readNBytes(signature.length);
            return Arrays.equals(actual, signature);
        } catch (IOException exception) {
            return false;
        }
    }

    private boolean contains(byte[] source, byte[] target) {
        outer:
        for (int index = 0; index <= source.length - target.length; index++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[index + offset] != target[offset]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
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

    private boolean isRecognizedArchiveName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return SEVEN_ZIP_VOLUME.matcher(name).matches()
                || ZIP_VOLUME.matcher(name).matches()
                || RAR_VOLUME.matcher(name).matches()
                || lower.endsWith(".zip")
                || lower.endsWith(".7z")
                || lower.endsWith(".rar");
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
        return normalizeNumber(number).equals(BigInteger.ONE);
    }

    private BigInteger normalizeNumber(String number) {
        try {
            return new BigInteger(number);
        } catch (NumberFormatException exception) {
            return BigInteger.valueOf(-1);
        }
    }

    private String normalizedName(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    private String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private enum MultipartFormat {
        ZIP("ZIP"), SEVEN_ZIP("7Z"), RAR("RAR");

        private final String folderSuffix;

        MultipartFormat(String folderSuffix) {
            this.folderSuffix = folderSuffix;
        }

        String folderSuffix() {
            return folderSuffix;
        }
    }

    private record GroupKey(MultipartFormat format, String normalizedBaseName) {
    }

    private static final class MultipartGroup {
        private final MultipartFormat format;
        private final String baseName;
        private final Map<BigInteger, Path> numberedParts = new LinkedHashMap<>();
        private final List<Path> allNumberedPaths = new ArrayList<>();
        private final List<Path> specialEntries = new ArrayList<>();
        private boolean duplicateNumbers;

        private MultipartGroup(MultipartFormat format, String baseName) {
            this.format = format;
            this.baseName = baseName;
        }

        void addNumberedPart(BigInteger number, Path path) {
            allNumberedPaths.add(path);
            if (number.signum() < 0 || numberedParts.putIfAbsent(number, path) != null) {
                duplicateNumbers = true;
            }
        }

        void addSpecialEntry(Path path) {
            specialEntries.add(path);
        }

        MultipartFormat format() {
            return format;
        }

        String baseName() {
            return baseName;
        }

        Map<BigInteger, Path> numberedParts() {
            return numberedParts;
        }

        List<Path> specialEntries() {
            return specialEntries;
        }

        boolean hasDuplicateNumbers() {
            return duplicateNumbers;
        }

        List<Path> allPaths() {
            List<Path> paths = new ArrayList<>(allNumberedPaths);
            paths.addAll(specialEntries);
            return paths;
        }

        boolean isSpreadAcrossDirectories() {
            return allPaths().stream()
                    .map(Path::getParent)
                    .map(path -> path.toAbsolutePath().normalize())
                    .map(path -> path.toString().toLowerCase(Locale.ROOT))
                    .distinct()
                    .count() > 1;
        }

        String displayName() {
            return baseName + "（" + format.folderSuffix() + " 分卷）";
        }
    }

    record NestedArchivePlan(List<ExtractionTask> tasks,
                             int consolidatedGroupCount,
                             int movedVolumeCount,
                             List<String> warnings,
                             List<Path> scannedFiles,
                             Set<String> recognizedArchiveFiles) {
        NestedArchivePlan {
            tasks = List.copyOf(tasks);
            warnings = List.copyOf(warnings);
            scannedFiles = List.copyOf(scannedFiles);
            recognizedArchiveFiles = Set.copyOf(recognizedArchiveFiles);
        }
    }

    private record ConsolidationResult(int groupCount,
                                       int movedFileCount,
                                       List<String> warnings) {
    }

    private record MoveRecord(Path source, Path destination) {
    }

    private record ArchiveEntry(Path path, String baseName) {
    }
}
