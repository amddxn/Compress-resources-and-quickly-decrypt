package com.example.extract;

import java.nio.file.Path;
import java.util.Objects;

public record ExtractionSettings(Path bzExecutable, Path outputRoot, int concurrency) {
    public ExtractionSettings {
        bzExecutable = Objects.requireNonNull(bzExecutable).toAbsolutePath().normalize();
        outputRoot = Objects.requireNonNull(outputRoot).toAbsolutePath().normalize();
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("并发数量必须在 1 到 8 之间");
        }
    }
}
