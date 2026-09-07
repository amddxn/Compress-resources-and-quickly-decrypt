package com.example.extract;

import java.util.List;

public record ExtractionBatchResult(List<ExtractionResult> results,
                                    int nestedArchiveCount,
                                    boolean depthLimitReached,
                                    int consolidatedMultipartGroupCount,
                                    int movedMultipartFileCount,
                                    List<String> organizationWarnings) {
    public ExtractionBatchResult(List<ExtractionResult> results) {
        this(results, 0, false, 0, 0, List.of());
    }

    public ExtractionBatchResult(List<ExtractionResult> results,
                                 int nestedArchiveCount,
                                 boolean depthLimitReached) {
        this(results, nestedArchiveCount, depthLimitReached, 0, 0, List.of());
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
