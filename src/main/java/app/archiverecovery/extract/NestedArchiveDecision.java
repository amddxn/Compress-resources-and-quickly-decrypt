package app.archiverecovery.extract;

import app.archiverecovery.service.RenameMode;

import java.nio.file.Path;
import java.util.List;

public record NestedArchiveDecision(Action action,
                                    List<Path> selectedFiles,
                                    RenameMode renameMode) {
    public NestedArchiveDecision {
        action = action == null ? Action.STOP_ALL : action;
        selectedFiles = selectedFiles == null ? List.of() : List.copyOf(selectedFiles);
        if (action == Action.APPLY && (selectedFiles.isEmpty() || renameMode == null)) {
            throw new IllegalArgumentException("应用修改时必须选择文件和目标格式");
        }
    }

    public static NestedArchiveDecision apply(List<Path> selectedFiles, RenameMode renameMode) {
        return new NestedArchiveDecision(Action.APPLY, selectedFiles, renameMode);
    }

    public static NestedArchiveDecision finishLayer() {
        return new NestedArchiveDecision(Action.FINISH_LAYER, List.of(), null);
    }

    public static NestedArchiveDecision stopAll() {
        return new NestedArchiveDecision(Action.STOP_ALL, List.of(), null);
    }

    public enum Action {
        APPLY,
        FINISH_LAYER,
        STOP_ALL
    }
}
