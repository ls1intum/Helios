package de.tum.cit.aet.helios.tests;

import java.util.Set;

public record ProcessTestResultsRequest(
    long repositoryId, long workflowRunId, Set<String> artifactNames) {}
