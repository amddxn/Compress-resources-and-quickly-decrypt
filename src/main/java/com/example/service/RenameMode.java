package com.example.service;

public enum RenameMode {
    ZIP("zip", false, "ZIP"),
    SEVEN_ZIP("7z", false, "7Z"),
    RAR("rar", false, "RAR"),
    ZIP_MULTIPART("zip", true, "ZIP 分卷"),
    SEVEN_ZIP_MULTIPART("7z", true, "7Z 分卷"),
    RAR_MULTIPART("rar", true, "RAR 分卷");

    private final String extension;
    private final boolean multipart;
    private final String displayName;

    RenameMode(String extension, boolean multipart, String displayName) {
        this.extension = extension;
        this.multipart = multipart;
        this.displayName = displayName;
    }

    public String extension() {
        return extension;
    }

    public boolean multipart() {
        return multipart;
    }

    public String displayName() {
        return displayName;
    }
}
