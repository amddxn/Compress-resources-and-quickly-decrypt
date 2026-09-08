package app.archiverecovery.extract;

import app.archiverecovery.model.RenameItem;
import app.archiverecovery.model.RenameStatus;
import app.archiverecovery.service.FileRenameService;
import app.archiverecovery.service.RenameMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class NestedArchiveRecoveryService {
    private static final String COLLECTION_FOLDER = "_分卷整理";
    private final FileRenameService renameService = new FileRenameService();

    RecoveryResult recover(Path extractedRoot,
                           List<Path> selectedFiles,
                           RenameMode mode) {
        Path normalizedRoot = extractedRoot.toAbsolutePath().normalize();
        List<String> warnings = new ArrayList<>();
        List<Path> files;
        try {
            files = validateSelection(normalizedRoot, selectedFiles);
        } catch (IOException exception) {
            return new RecoveryResult(List.of(), 0, 0, List.of(exception.getMessage()));
        }

        int movedCount = 0;
        StagedFiles stagedFiles = null;
        if (mode.multipart() && spansDirectories(files)) {
            try {
                stagedFiles = stageMultipartGroup(normalizedRoot, files, mode);
                files = stagedFiles.paths();
                movedCount = stagedFiles.paths().size();
            } catch (IOException exception) {
                return new RecoveryResult(List.of(), 0, 0,
                        List.of("分卷集中迁移失败：" + friendlyMessage(exception)));
            }
        }

        List<RenameItem> plan = renameService.createForcedNestedPlan(files, mode);
        boolean hasPlanningFailure = plan.stream().anyMatch(item ->
                item.status() == RenameStatus.CONFLICT || item.status() == RenameStatus.FAILED);
        if (hasPlanningFailure) {
            for (RenameItem item : plan) {
                if (item.status() == RenameStatus.CONFLICT || item.status() == RenameStatus.FAILED) {
                    warnings.add(item.source().getFileName() + "：" + item.message());
                }
            }
            if (stagedFiles != null) {
                try {
                    rollbackStaging(stagedFiles);
                    files = stagedFiles.moves().stream().map(MoveRecord::source).toList();
                    movedCount = 0;
                } catch (IOException exception) {
                    warnings.add("修改计划失败且无法完整回滚迁移：" + friendlyMessage(exception));
                }
            }
            return new RecoveryResult(files, 0, movedCount, warnings);
        }

        renameService.execute(plan);
        List<Path> finalPaths = new ArrayList<>();
        int renamedCount = 0;
        for (RenameItem item : plan) {
            if (item.status() == RenameStatus.SUCCESS) {
                finalPaths.add(item.target());
                renamedCount++;
            } else if (item.status() == RenameStatus.UNCHANGED) {
                finalPaths.add(item.source());
            } else {
                finalPaths.add(Files.exists(item.target()) ? item.target() : item.source());
                warnings.add(item.source().getFileName() + "：" + item.message());
            }
        }
        return new RecoveryResult(finalPaths, renamedCount, movedCount, warnings);
    }

    private List<Path> validateSelection(Path root, List<Path> selectedFiles) throws IOException {
        Map<String, Path> unique = new LinkedHashMap<>();
        for (Path selected : selectedFiles) {
            Path normalized = selected.toAbsolutePath().normalize();
            if (!normalized.startsWith(root)) {
                throw new IOException("所选文件不属于当前解压目录：" + normalized);
            }
            if (!Files.isRegularFile(normalized)) {
                throw new IOException("所选文件不存在或不是普通文件：" + normalized);
            }
            unique.putIfAbsent(pathKey(normalized), normalized);
        }
        if (unique.isEmpty()) {
            throw new IOException("没有选择需要恢复后缀的文件");
        }
        return new ArrayList<>(unique.values());
    }

    private boolean spansDirectories(List<Path> files) {
        return files.stream()
                .map(Path::getParent)
                .map(this::pathKey)
                .distinct()
                .count() > 1;
    }

    private StagedFiles stageMultipartGroup(Path root,
                                            List<Path> files,
                                            RenameMode mode) throws IOException {
        Path collectionRoot = root.resolve(COLLECTION_FOLDER);
        Files.createDirectories(collectionRoot);
        Path destinationDirectory = uniqueDirectory(collectionRoot,
                "待恢复-" + mode.displayName().replace(' ', '-'));
        Files.createDirectory(destinationDirectory);

        Set<String> destinationNames = new HashSet<>();
        for (Path source : files) {
            String nameKey = source.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!destinationNames.add(nameKey)) {
                Files.deleteIfExists(destinationDirectory);
                deleteIfEmpty(collectionRoot);
                throw new IOException("不同文件夹中存在同名文件，无法安全集中：" + source.getFileName());
            }
        }

        List<MoveRecord> moved = new ArrayList<>();
        try {
            for (Path source : files) {
                Path destination = destinationDirectory.resolve(source.getFileName());
                Files.move(source, destination);
                moved.add(new MoveRecord(source, destination));
            }
            return new StagedFiles(destinationDirectory, collectionRoot, moved);
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

    private void rollbackStaging(StagedFiles stagedFiles) throws IOException {
        IOException failure = null;
        List<MoveRecord> moves = stagedFiles.moves();
        for (int index = moves.size() - 1; index >= 0; index--) {
            MoveRecord record = moves.get(index);
            try {
                Files.move(record.destination(), record.source());
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        try {
            Files.deleteIfExists(stagedFiles.destinationDirectory());
            deleteIfEmpty(stagedFiles.collectionRoot());
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private Path uniqueDirectory(Path parent, String baseName) {
        int number = 1;
        while (true) {
            Path candidate = parent.resolve(number == 1 ? baseName : baseName + " (" + number + ")");
            if (!Files.exists(candidate)) {
                return candidate;
            }
            number++;
        }
    }

    private void deleteIfEmpty(Path directory) throws IOException {
        try (var children = Files.list(directory)) {
            if (children.findAny().isEmpty()) {
                Files.deleteIfExists(directory);
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

    record RecoveryResult(List<Path> finalPaths,
                          int renamedCount,
                          int movedCount,
                          List<String> warnings) {
        RecoveryResult {
            finalPaths = List.copyOf(finalPaths);
            warnings = List.copyOf(warnings);
        }
    }

    private record StagedFiles(Path destinationDirectory,
                               Path collectionRoot,
                               List<MoveRecord> moves) {
        List<Path> paths() {
            return moves.stream().map(MoveRecord::destination).toList();
        }
    }

    private record MoveRecord(Path source, Path destination) {
    }
}
