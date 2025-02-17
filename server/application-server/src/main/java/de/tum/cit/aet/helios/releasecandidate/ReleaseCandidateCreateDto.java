package de.tum.cit.aet.helios.releasecandidate;

import org.springframework.lang.NonNull;

public record ReleaseCandidateCreateDto(
    // Note: The branchName is marked as @NonNull to simplify the logic.
    // In practice, it is highly unlikely that we will encounter a commit detached from any branch.
    // This comment is a reminder that the Release Management works only with the branch name.
    @NonNull String name, @NonNull String commitSha, @NonNull String branchName) {}
