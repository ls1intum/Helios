package de.tum.cit.aet.helios.common.dto.test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@JsonSerialize
public record ProcessTestResultRequest(Long repositoryId, Long workflowRunId) {}
