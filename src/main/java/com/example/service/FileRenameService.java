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

public final class FileRenameService {
    private final ArchiveNameDetector archiveNameDetector = new ArchiveNameDetector();
    private final MultipartRenamePlanner multipartRenamePlanner = new MultipartRenamePlanner();

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
                ? multipartRenamePlanner.createTargets(uniqueFiles.values(), mode)
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
