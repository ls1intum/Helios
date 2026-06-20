package de.tum.cit.aet.helios.deployment.approval;

/**
 * Body for {@code POST /api/deployments/{id}/decline}. The {@code comment} is appended to the
 * Helios-generated audit comment that flows through to the GitHub "Reviewing deployment" entry —
 * lets the reviewer say "wrong branch" or similar.
 */
public record ReviewDeploymentRequest(String comment) {}
