package app.archiverecovery.extract;

import java.nio.file.Path;
import java.util.List;

public record NestedArchiveInspection(Path extractedRoot,
                                      int nestedDepth,
                                      List<FileEntry> files) {
    public NestedArchiveInspection {
        extractedRoot = extractedRoot.toAbsolutePath().normalize();
        files = List.copyOf(files);
    }

    public List<Path> undecidedFiles() {
        return files.stream()
                .filter(entry -> !entry.recognizedArchive())
                .map(FileEntry::path)
                .toList();
    }

    public record FileEntry(Path path, long size, boolean recognizedArchive) {
        public FileEntry {
            path = path.toAbsolutePath().normalize();
        }
    }
}
