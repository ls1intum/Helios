package de.tum.cit.aet.helios.common.dto.test;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;

@JsonSerialize
public record ProcessTestResultResponse(
    Long workflowRunId, List<TestSuite> testSuites, ProcessingStatus status, String errorMessage) {}
