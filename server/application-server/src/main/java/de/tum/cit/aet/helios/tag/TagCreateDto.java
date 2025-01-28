package de.tum.cit.aet.helios.tag;

import org.springframework.lang.NonNull;

public record TagCreateDto(
    @NonNull String name,
    @NonNull String commitSha,
    String branchName,
    @NonNull String createdByLogin) {}
