package de.tum.cit.aet.helios.tests.type;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TestTypeDto(
    Long id,
    @NotBlank(message = "Name is required")
        @Size(max = 255, message = "Name must be less than 255 characters")
        String name,
    @NotBlank(message = "Artifact name is required")
        @Size(max = 255, message = "Artifact name must be less than 255 characters")
        String artifactName,
    @NotNull(message = "Workflow ID is required") Long workflowId) {
  public static TestTypeDto fromTestType(TestType testType) {
    return new TestTypeDto(
        testType.getId(),
        testType.getName(),
        testType.getArtifactName(),
        testType.getWorkflow() != null ? testType.getWorkflow().getId() : null);
  }
}
