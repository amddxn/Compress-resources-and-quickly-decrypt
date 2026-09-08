package com.example.extract;

@FunctionalInterface
public interface NestedArchiveDecisionProvider {
    NestedArchiveDecision requestDecision(NestedArchiveInspection inspection);
}
