package app.archiverecovery.extract;

@FunctionalInterface
public interface NestedArchiveDecisionProvider {
    NestedArchiveDecision requestDecision(NestedArchiveInspection inspection);
}
