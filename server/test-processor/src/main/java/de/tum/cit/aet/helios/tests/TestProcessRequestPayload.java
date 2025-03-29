package de.tum.cit.aet.helios.tests;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Set;

@JsonSerialize
public record TestProcessRequestPayload(
    long repositoryId, long workflowRunId, Set<String> artifactNames) {}
