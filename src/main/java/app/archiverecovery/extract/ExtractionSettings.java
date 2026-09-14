package app.archiverecovery.extract;

import java.nio.file.Path;
import java.util.Objects;

public record ExtractionSettings(Path outputRoot, int concurrency, int maxNestedDepth) {
    public ExtractionSettings(Path outputRoot, int concurrency) {
        this(outputRoot, concurrency, 10);
    }

    public ExtractionSettings {
        outputRoot = Objects.requireNonNull(outputRoot).toAbsolutePath().normalize();
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalArgumentException("并发数量必须在 1 到 8 之间");
        }
        if (maxNestedDepth < 1 || maxNestedDepth > 50) {
            throw new IllegalArgumentException("最大嵌套层数必须在 1 到 50 之间");
        }
    }
}
