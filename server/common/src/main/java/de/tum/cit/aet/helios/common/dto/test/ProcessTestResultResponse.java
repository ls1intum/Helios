package de.tum.cit.aet.helios.common.dto.test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record ProcessTestResultResponse(
    Long workflowRunId, ProcessingStatus status, String errorMessage) {}
