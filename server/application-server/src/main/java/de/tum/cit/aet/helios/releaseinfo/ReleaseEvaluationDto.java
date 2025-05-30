package de.tum.cit.aet.helios.releaseinfo;

import jakarta.validation.constraints.Size;

public record ReleaseEvaluationDto(
    String name,
    boolean isWorking,
    @Size(max = 500, message = "Comment cannot exceed 500 characters") String comment) {}
