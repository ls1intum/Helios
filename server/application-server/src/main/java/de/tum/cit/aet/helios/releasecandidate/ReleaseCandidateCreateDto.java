package de.tum.cit.aet.helios.releasecandidate;

import org.springframework.lang.NonNull;

public record ReleaseCandidateCreateDto(
    @NonNull String name, @NonNull String commitSha, String branchName) {}
