package app.archiverecovery.extract;

import java.nio.file.Path;

public record ExtractionTask(Path archive, Path outputDirectory, int nestedDepth) {
    public ExtractionTask(Path archive, Path outputDirectory) {
        this(archive, outputDirectory, 0);
    }
}
