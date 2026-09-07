package com.example.extract;

import java.nio.file.Path;

public record ExtractionResult(Path archive, Path outputDirectory, boolean success,
                               int exitCode, String message) {
}
