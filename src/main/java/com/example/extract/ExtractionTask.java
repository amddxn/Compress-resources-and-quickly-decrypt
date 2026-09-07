package com.example.extract;

import java.nio.file.Path;

public record ExtractionTask(Path archive, Path outputDirectory) {
}
