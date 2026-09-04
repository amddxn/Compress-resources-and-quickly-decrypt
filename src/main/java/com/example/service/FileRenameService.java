package com.example.service;

import com.example.model.RenameItem;
import com.example.model.RenameStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileRenameService {
    private static final Pattern SEVEN_ZIP_VOLUME =
            Pattern.compile("^(.+)\\.7z\\.(\\d{3,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_VOLUME =
            Pattern.compile("^(.+)\\.zip\\.(\\d{3,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_Z_VOLUME =
            Pattern.compile("^(.+)\\.z(\\d{2,})$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_VOLUME =
            Pattern.compile("^(?:(.+)\\.)?part(\\d+)\\.rar$", Pattern.CASE_INSENSITIVE);
    private static final Pattern RAR_OLD_VOLUME =
            Pattern.compile("^(.+)\\.r(\\d{2,})$", Pattern.CASE_INSENSITIVE);
    private final ArchiveNameDetector archiveNameDetector = new ArchiveNameDetector();

    public List<RenameItem> createPlan(Collection<Path> files, String targetExtension) {
        return createPlan(files, normalMode(targetExtension));
    }

    public List<RenameItem> createPlan(Collection<Path> files, RenameMode mode) {

        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        for (Path file : files) {
            Path normalized = file.toAbsolutePath().normalize();
            uniqueFiles.putIfAbsent(pathKey(normalized), normalized);
        }

        Map<Path, ArchiveNameDetector.Detection> detections = archiveNameDetector.detect(uniqueFiles.values());
        Map<Path, Path> multipartTargets = mode.multipart()
                ? createMultipartTargets(uniqueFiles.values(), detections, mode)
                : Map.of();
        List<RenameItem> result = new ArrayList<>();
        for (Path source : uniqueFiles.values()) {
            ArchiveNameDetector.Detection detection = detections.get(source);
            boolean convertStandardToMultipart = multipartTargets.containsKey(source);
            if (!Files.isRegularFile(source)) {
                result.add(new RenameItem(source, source, "无法识别", RenameStatus.FAILED,
                        "文件不存在或不是普通文件"));
            } else if (detection.protectedName() && !convertStandardToMultipart) {
                String message = detection.multipart() ? "已自动识别分卷格式，不修改该卷名称" : "压缩后缀有效";
                result.add(new RenameItem(source, source, detection.description(), RenameStatus.UNCHANGED, message));
            } else {
                Path target;
                String description = detection.description();
                if (mode.multipart()) {
                    target = multipartTargets.get(source);
                    description = "待恢复为 " + mode.displayName();
                } else {
                    target = detection.multipart()
                            ? detection.targetPath(source, mode.extension())
                            : ExtensionService.targetPath(source, mode.extension());
                }
                result.add(new RenameItem(source, target, description, RenameStatus.READY, ""));
            }
        }

        markConflicts(result);
        return result;
    }

    private Map<Path, Path> createMultipartTargets(Collection<Path> files,
                                                   Map<Path, ArchiveNameDetector.Detection> detections,
                                                   RenameMode mode) {
        Set<String> legacyZipGroups = findGroupKeys(files, ZIP_Z_VOLUME);
        Set<String> legacyRarGroups = findGroupKeys(files, RAR_OLD_VOLUME);
        Map<String, Set<Integer>> occupiedNumbers = findOccupiedNumbers(
                files, mode, legacyZipGroups, legacyRarGroups);
        Map<Path, Path> targets = new HashMap<>();

        for (Path source : files) {
            ArchiveNameDetector.Detection detection = detections.get(source);
            if (detection == null) {
                continue;
            }

            if (detection.protectedName()) {
                if (detection.multipart() || !isStandardTargetArchive(source, detection, mode)) {
                    continue;
                }
                String standardBaseName = baseNameOf(source.getFileName().toString());
                if (!hasKnownGroup(occupiedNumbers, source, standardBaseName)) {
                    continue;
                }
            }

            if (detection.multipart() && detection.volumeBaseName() != null
                    && detection.volumeNumber() != null) {
                Path target = detection.targetPath(source, mode.extension());
                targets.put(source, target);
                addOccupiedNumber(occupiedNumbers, source, detection.volumeBaseName(), detection.volumeNumber());
                continue;
            }

            String baseName = resolveMultipartBaseName(source, mode, occupiedNumbers);
            String key = groupKey(source, baseName);
            Set<Integer> occupied = occupiedNumbers.computeIfAbsent(key, ignored -> new HashSet<>());
            int number = firstAvailableNumber(occupied);
            occupied.add(number);
            targets.put(source, multipartTarget(
                    source, baseName, number, mode, legacyZipGroups, legacyRarGroups));
        }
        return targets;
    }

    private String resolveMultipartBaseName(Path source, RenameMode mode,
                                            Map<String, Set<Integer>> occupiedNumbers) {
        String fileName = source.getFileName().toString();

        // 完整文件名正好等于现有分卷的主体名时，它实际上是一个无后缀的卷。
        if (hasKnownGroup(occupiedNumbers, source, fileName)) {
            return fileName;
        }

        String withoutLastExtension = baseNameOf(fileName);
        if (hasKnownGroup(occupiedNumbers, source, withoutLastExtension)) {
            return withoutLastExtension;
        }

        // 例如 name.7z.mp3：mp3 是伪后缀，已有的 7z 标记不能再次追加。
        String formatSuffix = "." + mode.extension();
        if (withoutLastExtension.toLowerCase(Locale.ROOT).endsWith(formatSuffix)) {
            String withoutDuplicateFormat = withoutLastExtension.substring(
                    0, withoutLastExtension.length() - formatSuffix.length());
            if (hasKnownGroup(occupiedNumbers, source, withoutDuplicateFormat)) {
                return withoutDuplicateFormat;
            }
            return withoutDuplicateFormat;
        }

        return withoutLastExtension;
    }

    private boolean hasKnownGroup(Map<String, Set<Integer>> occupiedNumbers,
                                  Path source, String baseName) {
        return occupiedNumbers.containsKey(groupKey(source, baseName));
    }

    private boolean isStandardTargetArchive(Path source,
                                            ArchiveNameDetector.Detection detection,
                                            RenameMode mode) {
        return mode.multipart()
                && detection != null
                && detection.protectedName()
                && !detection.multipart()
                && ExtensionService.extensionOf(source.getFileName().toString()).equals(mode.extension());
    }

    private Map<String, Set<Integer>> findOccupiedNumbers(Collection<Path> files, RenameMode mode,
                                                          Set<String> legacyZipGroups,
                                                          Set<String> legacyRarGroups) {
        Map<String, Set<Integer>> occupied = new HashMap<>();
        Pattern primaryPattern = switch (mode) {
            case SEVEN_ZIP_MULTIPART -> SEVEN_ZIP_VOLUME;
            case ZIP_MULTIPART -> ZIP_VOLUME;
            case RAR_MULTIPART -> RAR_VOLUME;
            default -> throw new IllegalArgumentException("当前模式不是分卷模式");
        };

        for (Path path : files) {
            String name = path.getFileName().toString();
            Matcher matcher = primaryPattern.matcher(name);
            if (matcher.matches()) {
                addOccupiedNumber(occupied, path, matcher.group(1), matcher.group(2));
                continue;
            }
            if (mode == RenameMode.ZIP_MULTIPART) {
                Matcher legacyZip = ZIP_Z_VOLUME.matcher(name);
                if (legacyZip.matches()) {
                    addOccupiedNumber(occupied, path, legacyZip.group(1), legacyZip.group(2));
                }
            } else if (mode == RenameMode.RAR_MULTIPART) {
                Matcher legacyRar = RAR_OLD_VOLUME.matcher(name);
                if (legacyRar.matches()) {
                    addOldRarOccupiedNumber(occupied, path, legacyRar.group(1), legacyRar.group(2));
                } else if (ExtensionService.extensionOf(name).equals("rar")) {
                    String base = baseNameOf(name);
                    if (legacyRarGroups.contains(groupKey(path, base))) {
                        addOccupiedNumber(occupied, path, base, 1);
                    }
                }
            }
        }
        return occupied;
    }

    private Set<String> findGroupKeys(Collection<Path> files, Pattern pattern) {
        Set<String> result = new HashSet<>();
        for (Path path : files) {
            Matcher matcher = pattern.matcher(path.getFileName().toString());
            if (matcher.matches()) {
                result.add(groupKey(path, matcher.group(1)));
            }
        }
        return result;
    }

    private void addOccupiedNumber(Map<String, Set<Integer>> occupied, Path path,
                                   String baseName, String numberText) {
        try {
            addOccupiedNumber(occupied, path, baseName, parseNumber(numberText));
        } catch (NumberFormatException ignored) {
            // 无法放入 int 的异常大卷号不参与缺号计算，但原文件仍保持不变。
        }
    }

    private void addOccupiedNumber(Map<String, Set<Integer>> occupied, Path path,
                                   String baseName, int number) {
        if (number > 0) {
            occupied.computeIfAbsent(groupKey(path, baseName), ignored -> new HashSet<>()).add(number);
        }
    }

    private void addOldRarOccupiedNumber(Map<String, Set<Integer>> occupied, Path path,
                                         String baseName, String oldStyleNumber) {
        try {
            addOccupiedNumber(occupied, path, baseName, parseNumber(oldStyleNumber) + 2);
        } catch (NumberFormatException ignored) {
            // 异常大卷号保持原名，但不参与缺号计算。
        }
    }

    private int parseNumber(String text) {
        return Integer.parseInt(text);
    }

    private Path multipartTarget(Path source, String baseName, int number, RenameMode mode,
                                 Set<String> legacyZipGroups, Set<String> legacyRarGroups) {
        String formattedNumber = String.format(Locale.ROOT, "%03d", number);
        String key = groupKey(source, baseName);
        String targetName = switch (mode) {
            case SEVEN_ZIP_MULTIPART -> baseName + ".7z." + formattedNumber;
            case ZIP_MULTIPART -> legacyZipGroups.contains(key)
                    ? baseName + ".z" + String.format(Locale.ROOT, "%02d", number)
                    : baseName + ".zip." + formattedNumber;
            case RAR_MULTIPART -> {
                if (legacyRarGroups.contains(key)) {
                    yield number == 1
                            ? baseName + ".rar"
                            : baseName + ".r" + String.format(Locale.ROOT, "%02d", number - 2);
                }
                yield baseName + ".part" + formattedNumber + ".rar";
            }
            default -> throw new IllegalArgumentException("当前模式不是分卷模式");
        };
        return source.resolveSibling(targetName);
    }

    private int firstAvailableNumber(Set<Integer> occupied) {
        int number = 1;
        while (occupied.contains(number)) {
            number++;
        }
        return number;
    }

    private String baseNameOf(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }

    private String groupKey(Path path, String baseName) {
        Path parent = path.getParent();
        String directory = parent == null ? "" : parent.toAbsolutePath().normalize().toString();
        return (directory + "\u0000" + baseName).toLowerCase(Locale.ROOT);
    }

    private RenameMode normalMode(String targetExtension) {
        if (targetExtension == null) {
            throw new IllegalArgumentException("目标后缀只能是 zip、7z 或 rar");
        }
        return switch (targetExtension.toLowerCase(Locale.ROOT)) {
            case "zip" -> RenameMode.ZIP;
            case "7z" -> RenameMode.SEVEN_ZIP;
            case "rar" -> RenameMode.RAR;
            default -> throw new IllegalArgumentException("目标后缀只能是 zip、7z 或 rar");
        };
    }

    public void execute(List<RenameItem> items) {
        for (RenameItem item : items) {
            if (item.status() != RenameStatus.READY) {
                continue;
            }
            if (Files.exists(item.target())) {
                item.updateStatus(RenameStatus.CONFLICT, "目标文件已存在，未覆盖");
                continue;
            }
            try {
                Files.move(item.source(), item.target());
                item.updateStatus(RenameStatus.SUCCESS, "");
            } catch (IOException | SecurityException exception) {
                item.updateStatus(RenameStatus.FAILED, friendlyMessage(exception));
            }
        }
    }

    private void markConflicts(List<RenameItem> items) {
        Map<String, Integer> targetCounts = new HashMap<>();
        for (RenameItem item : items) {
            if (item.status() == RenameStatus.READY) {
                targetCounts.merge(pathKey(item.target()), 1, Integer::sum);
            }
        }

        for (RenameItem item : items) {
            if (item.status() != RenameStatus.READY) {
                continue;
            }
            if (targetCounts.getOrDefault(pathKey(item.target()), 0) > 1) {
                item.updateStatus(RenameStatus.CONFLICT, "多个文件将产生相同名称");
            } else if (Files.exists(item.target())) {
                item.updateStatus(RenameStatus.CONFLICT, "目标文件已存在");
            }
        }
    }

    private String pathKey(Path path) {
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private String friendlyMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
