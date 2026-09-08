package app.archiverecovery.extract;

import java.util.List;

public record ExtractionBatchResult(List<ExtractionResult> results,
                                    int nestedArchiveCount,
                                    boolean depthLimitReached,
                                    int consolidatedMultipartGroupCount,
                                    int movedMultipartFileCount,
                                    List<String> organizationWarnings,
                                    int recoveredDisguisedFileCount,
                                    int userConfirmedMultipartMovedCount,
                                    boolean stoppedByUser) {
    public ExtractionBatchResult(List<ExtractionResult> results) {
        this(results, 0, false, 0, 0, List.of(), 0, 0, false);
    }

    public ExtractionBatchResult(List<ExtractionResult> results,
                                 int nestedArchiveCount,
                                 boolean depthLimitReached) {
        this(results, nestedArchiveCount, depthLimitReached, 0, 0, List.of(), 0, 0, false);
    }

    public ExtractionBatchResult(List<ExtractionResult> results,
                                 int nestedArchiveCount,
                                 boolean depthLimitReached,
                                 int consolidatedMultipartGroupCount,
                                 int movedMultipartFileCount,
                                 List<String> organizationWarnings) {
        this(results, nestedArchiveCount, depthLimitReached,
                consolidatedMultipartGroupCount, movedMultipartFileCount,
                organizationWarnings, 0, 0, false);
    }

    public ExtractionBatchResult {
        results = List.copyOf(results);
        organizationWarnings = List.copyOf(organizationWarnings);
    }

    public long successCount() {
        return results.stream().filter(ExtractionResult::success).count();
    }

    public long failureCount() {
        return results.size() - successCount();
    }
}
