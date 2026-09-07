package com.example.extract;

import java.util.List;

public record ExtractionBatchResult(List<ExtractionResult> results,
                                    int nestedArchiveCount,
                                    boolean depthLimitReached) {
    public ExtractionBatchResult(List<ExtractionResult> results) {
        this(results, 0, false);
    }

    public ExtractionBatchResult {
        results = List.copyOf(results);
    }

    public long successCount() {
        return results.stream().filter(ExtractionResult::success).count();
    }

    public long failureCount() {
        return results.size() - successCount();
    }
}
