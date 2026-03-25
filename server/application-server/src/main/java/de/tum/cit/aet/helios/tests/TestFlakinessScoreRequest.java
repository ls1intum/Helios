package de.tum.cit.aet.helios.tests;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.lang.NonNull;

public record TestFlakinessScoreRequest(
    @NonNull @NotEmpty @Valid List<TestCaseIdentifier> testCases) {

  public record TestCaseIdentifier(
      @NonNull @NotBlank String testName,
      @NonNull @NotBlank String className,
      @NonNull @NotBlank String testSuiteName) {}
}
