package app.archiverecovery.service;

import app.archiverecovery.model.RenameItem;
import app.archiverecovery.model.RenameStatus;

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
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FileRenameService {
    private static final Pattern KNOWN_ARCHIVE_SUFFIX = Pattern.compile(
            "(?i)(?:\\.7z\\.\\d+|\\.zip\\.\\d+|\\.part\\d+\\.rar|\\.z\\d+|\\.r\\d+|\\.zip|\\.7z|\\.rar)$");
    private final ArchiveNameDetector archiveNameDetector = new ArchiveNameDetector();
    private final MultipartRenamePlanner multipartRenamePlanner = new MultipartRenamePlanner();

    public List<RenameItem> createPlan(Collection<Path> files, String targetExtension) {
        return createPlan(files, normalMode(targetExtension));
    }

    public List<RenameItem> createPlan(Collection<Path> files, RenameMode mode) {
        return createPlan(files, mode, false);
    }

    public List<RenameItem> createSingleMultipartGroupPlan(Collection<Path> files, RenameMode mode) {
        if (!mode.multipart()) {
            throw new IllegalArgumentException("仅分卷模式可以强制按单组生成名称");
        }
        return createPlan(files, mode, true);
    }

    public List<RenameItem> createForcedNestedPlan(Collection<Path> files, RenameMode mode) {
        if (mode.multipart()) {
            return createSingleMultipartGroupPlan(files, mode);
        }

        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        for (Path file : files) {
            Path normalized = file.toAbsolutePath().normalize();
            uniqueFiles.putIfAbsent(pathKey(normalized), normalized);
        }

        List<RenameItem> result = new ArrayList<>();
        for (Path source : uniqueFiles.values()) {
            if (!Files.isRegularFile(source)) {
                result.add(new RenameItem(source, source, "无法识别", RenameStatus.FAILED,
                        "文件不存在或不是普通文件"));
                continue;
            }
            Path target = forcedNormalTarget(source, mode.extension());
            if (pathKey(source).equals(pathKey(target))) {
                result.add(new RenameItem(source, source, "普通 " + mode.displayName(),
                        RenameStatus.UNCHANGED, "当前后缀已经符合所选格式"));
            } else {
                result.add(new RenameItem(source, target,
                        "按用户选择恢复为 " + mode.displayName(), RenameStatus.READY, ""));
            }
        }
        markConflicts(result);
        return result;
    }

    private Path forcedNormalTarget(Path source, String targetExtension) {
        String fileName = source.getFileName().toString();
        Matcher knownSuffix = KNOWN_ARCHIVE_SUFFIX.matcher(fileName);
        String baseName;
        if (knownSuffix.find() && knownSuffix.start() > 0) {
            baseName = fileName.substring(0, knownSuffix.start());
        } else {
            int lastDot = fileName.lastIndexOf('.');
            baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        }
        return source.resolveSibling(baseName + "." + targetExtension.toLowerCase(Locale.ROOT));
    }

    private List<RenameItem> createPlan(Collection<Path> files,
                                        RenameMode mode,
                                        boolean forceSingleMultipartGroup) {
        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        for (Path file : files) {
            Path normalized = file.toAbsolutePath().normalize();
            uniqueFiles.putIfAbsent(pathKey(normalized), normalized);
        }

        Map<Path, ArchiveNameDetector.Detection> detections = archiveNameDetector.detect(uniqueFiles.values());
        Map<Path, Path> multipartTargets = mode.multipart()
                ? forceSingleMultipartGroup
                ? multipartRenamePlanner.createSingleGroupTargets(uniqueFiles.values(), mode)
                : multipartRenamePlanner.createTargets(uniqueFiles.values(), mode)
                : Map.of();

        List<RenameItem> result = new ArrayList<>();
        for (Path source : uniqueFiles.values()) {
            ArchiveNameDetector.Detection detection = detections.get(source);
            if (!Files.isRegularFile(source)) {
                result.add(new RenameItem(source, source, "无法识别", RenameStatus.FAILED,
                        "文件不存在或不是普通文件"));
            } else if (mode.multipart()) {
                addMultipartPlanItem(result, source, multipartTargets.get(source), mode);
            } else if (detection.protectedName()) {
                String message = detection.multipart() ? "已自动识别分卷格式，不修改该卷名称" : "压缩后缀有效";
                result.add(new RenameItem(source, source, detection.description(), RenameStatus.UNCHANGED, message));
            } else {
                Path target = detection.multipart()
                        ? detection.targetPath(source, mode.extension())
                        : ExtensionService.targetPath(source, mode.extension());
                result.add(new RenameItem(source, target, detection.description(), RenameStatus.READY, ""));
            }
        }

        markConflicts(result);
        return result;
    }

    private void addMultipartPlanItem(List<RenameItem> result, Path source,
                                      Path target, RenameMode mode) {
        String description = "待恢复为 " + mode.displayName();
        if (target == null) {
            result.add(new RenameItem(source, source, description, RenameStatus.FAILED,
                    "无法生成分卷名称"));
        } else if (source.equals(target)) {
            result.add(new RenameItem(source, source, mode.displayName(), RenameStatus.UNCHANGED,
                    "分卷后缀已经符合所选格式"));
        } else {
            result.add(new RenameItem(source, target, description, RenameStatus.READY, ""));
        }
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
        execute(items, ignored -> {
        });
    }

    public void execute(List<RenameItem> items, Consumer<RenameItem> progress) {
        Objects.requireNonNull(progress, "progress");
        for (RenameItem item : items) {
            if (item.status() != RenameStatus.READY) {
                continue;
            }
            if (Files.exists(item.target())) {
                item.updateStatus(RenameStatus.CONFLICT, "目标文件已存在，未覆盖");
                progress.accept(item);
                continue;
            }
            try {
                Files.move(item.source(), item.target());
                item.updateStatus(RenameStatus.SUCCESS, "");
            } catch (IOException | SecurityException exception) {
                item.updateStatus(RenameStatus.FAILED, friendlyMessage(exception));
            }
            progress.accept(item);
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
