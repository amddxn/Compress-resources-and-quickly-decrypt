package app.archiverecovery.extract;

import java.nio.file.Path;

/**
 * Structured progress information for a batch extraction operation.
 * archivePercent is populated only when bz.exe emits an actual percentage.
 */
public record ExtractionProgress(Phase phase,
                                 int nestedDepth,
                                 int completed,
                                 int total,
                                 Path archive,
                                 String message,
                                 int archivePercent) {
    public ExtractionProgress(Phase phase,
                              int nestedDepth,
                              int completed,
                              int total,
                              Path archive,
                              String message) {
        this(phase, nestedDepth, completed, total, archive, message, -1);
    }

    public ExtractionProgress {
        completed = Math.max(0, completed);
        total = Math.max(0, total);
        if (total > 0) {
            completed = Math.min(completed, total);
        }
        message = message == null ? "" : message;
        archivePercent = Math.max(-1, Math.min(100, archivePercent));
    }

    public int percent() {
        if (hasExactPercentage()) {
            return archivePercent;
        }
        return total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
    }

    public boolean hasExactPercentage() {
        return archivePercent >= 0;
    }

    public enum Phase {
        PREPARING_LAYER,
        EXTRACTING,
        SCANNING_NESTED,
        WAITING_FOR_USER
    }
}
