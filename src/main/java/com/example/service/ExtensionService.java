package com.example.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class ExtensionService {
    private static final Set<String> PROTECTED_EXTENSIONS = Set.of("zip", "7z", "rar");

    private ExtensionService() {
    }

    public static boolean isAllowedTarget(String extension) {
        return extension != null
                && PROTECTED_EXTENSIONS.contains(extension.toLowerCase(Locale.ROOT));
    }

    public static boolean hasProtectedExtension(Path path) {
        return PROTECTED_EXTENSIONS.contains(extensionOf(path.getFileName().toString()));
    }

    public static String extensionOf(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    public static Path targetPath(Path source, String targetExtension) {
        if (!isAllowedTarget(targetExtension)) {
            throw new IllegalArgumentException("目标后缀只能是 zip、7z 或 rar");
        }
        if (hasProtectedExtension(source)) {
            return source;
        }

        String fileName = source.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String baseName = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        return source.resolveSibling(baseName + "." + targetExtension.toLowerCase(Locale.ROOT));
    }
}
