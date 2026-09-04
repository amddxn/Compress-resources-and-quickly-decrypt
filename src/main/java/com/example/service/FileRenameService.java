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

    public List<RenameItem> createPlan(Collection<Path> files, String targetExtension) {
        if (!ExtensionService.isAllowedTarget(targetExtension)) {
            throw new IllegalArgumentException("目标后缀只能是 zip、7z 或 rar");
        }

        Map<String, Path> uniqueFiles = new LinkedHashMap<>();
        for (Path file : files) {
            Path normalized = file.toAbsolutePath().normalize();
            uniqueFiles.putIfAbsent(pathKey(normalized), normalized);
        }

        List<RenameItem> result = new ArrayList<>();
        for (Path source : uniqueFiles.values()) {
            if (!Files.isRegularFile(source)) {
                result.add(new RenameItem(source, source, RenameStatus.FAILED, "文件不存在或不是普通文件"));
            } else if (ExtensionService.hasProtectedExtension(source)) {
                result.add(new RenameItem(source, source, RenameStatus.UNCHANGED,
                        "后缀已经是 zip、7z 或 rar"));
            } else {
                Path target = ExtensionService.targetPath(source, targetExtension);
                result.add(new RenameItem(source, target, RenameStatus.READY, ""));
            }
        }

        markConflicts(result);
        return result;
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
