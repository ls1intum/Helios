package de.tum.cit.aet.helios.pullrequest;

import java.util.List;

public record PullRequestStateReconciliationResultDto(
    boolean dryRun,
    Long repositoryId,
    String repositoryNameWithOwner,
    int scannedCount,
    int updatedCount,
    List<Long> updatedPullRequestIds,
    List<Integer> updatedPullRequestNumbers,
    int unchangedCount,
    int errorCount,
    List<String> errors) {}
