package app.archiverecovery.model;

import java.nio.file.Path;
import java.util.Objects;

public final class RenameItem {
    private final Path source;
    private final Path target;
    private final String detection;
    private RenameStatus status;
    private String message;

    public RenameItem(Path source, Path target, String detection, RenameStatus status, String message) {
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        this.detection = Objects.requireNonNull(detection);
        this.status = Objects.requireNonNull(status);
        this.message = message == null ? "" : message;
    }

    public Path source() {
        return source;
    }

    public Path target() {
        return target;
    }

    public String detection() {
        return detection;
    }

    public RenameStatus status() {
        return status;
    }

    public String message() {
        return message;
    }

    public void updateStatus(RenameStatus status, String message) {
        this.status = Objects.requireNonNull(status);
        this.message = message == null ? "" : message;
    }
}
