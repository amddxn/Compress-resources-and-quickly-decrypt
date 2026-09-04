package com.example.model;

import java.nio.file.Path;
import java.util.Objects;

public final class RenameItem {
    private final Path source;
    private final Path target;
    private RenameStatus status;
    private String message;

    public RenameItem(Path source, Path target, RenameStatus status, String message) {
        this.source = Objects.requireNonNull(source);
        this.target = Objects.requireNonNull(target);
        this.status = Objects.requireNonNull(status);
        this.message = message == null ? "" : message;
    }

    public Path source() {
        return source;
    }

    public Path target() {
        return target;
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
