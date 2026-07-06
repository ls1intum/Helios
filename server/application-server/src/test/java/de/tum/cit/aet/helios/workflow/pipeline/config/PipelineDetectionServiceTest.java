package de.tum.cit.aet.helios.workflow.pipeline.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.CategoryConfig;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.NodeConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Unit tests for the job-name → Build/Test/Quality classification heuristics. */
class PipelineDetectionServiceTest {

  private final PipelineDetectionService service = new PipelineDetectionService();

  private CategoryConfig category(PipelineConfigDto dto, String name) {
    return dto.categories().stream()
        .filter(c -> c.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("missing category " + name));
  }

  private Optional<NodeConfig> node(CategoryConfig category, String label) {
    return category.nodes().stream().filter(n -> n.label().equals(label)).findFirst();
  }

  @Test
  void buildTestQualityLanesAlwaysPresentEvenWithNoJobs() {
    PipelineConfigDto dto = service.suggest(List.of());
    assertEquals(List.of("Build", "Test", "Quality"), dto.categories().stream()
        .map(CategoryConfig::name).toList());
    assertTrue(dto.categories().stream().allMatch(c -> c.nodes().isEmpty()));
  }

  @Test
  void classifiesReusableWorkflowPrefixJobsIntoTheirStage() {
    PipelineConfigDto dto =
        service.suggest(
            List.of(
                "Build / Build .war artifact",
                "Test / Client Tests",
                "Test / Server Tests (PostgreSQL)",
                "E2E / Report E2E Overall Status",
                "Quality / Client Code Style"));

    assertTrue(node(category(dto, "Build"), "Build .war artifact").isPresent());
    assertTrue(node(category(dto, "Test"), "Client Tests").isPresent());
    // matrix parenthetical is stripped from the label
    assertTrue(node(category(dto, "Test"), "Server Tests").isPresent());
    // E2E prefix classifies into Test (e2e keyword)
    assertTrue(node(category(dto, "Test"), "Report E2E Overall Status").isPresent());
    assertTrue(node(category(dto, "Quality"), "Client Code Style").isPresent());
  }

  @Test
  void matcherIsTheParenStrippedFullNameSoMatrixLegsCollapseToOneNode() {
    PipelineConfigDto dto =
        service.suggest(
            List.of(
                "Build / Build and Push Docker Image (PR, amd64)",
                "Build / Build and Push Docker Image (arm64)"));
    CategoryConfig build = category(dto, "Build");
    assertEquals(1, build.nodes().size(), "matrix legs should collapse to one node");
    NodeConfig docker = build.nodes().get(0);
    assertEquals(List.of("Build / Build and Push Docker Image"), docker.jobNameMatchers());
  }

  @Test
  void classifiesFlatJobNamesByKeyword() {
    PipelineConfigDto dto =
        service.suggest(List.of("server-tests", "Linting Server (Java)", "build-client"));
    assertTrue(node(category(dto, "Test"), "server-tests").isPresent());
    assertTrue(node(category(dto, "Quality"), "Linting Server").isPresent()); // lint keyword
    assertTrue(node(category(dto, "Build"), "build-client").isPresent());
  }

  @Test
  void unclassifiableJobsGoToOther() {
    PipelineConfigDto dto = service.suggest(List.of("Deploy to Prod", "Notify Slack"));
    CategoryConfig other = category(dto, "Other");
    assertEquals(2, other.nodes().size());
  }

  @Test
  void prefixThatDoesNotClassifyFallsBackToFullNameKeyword() {
    // "Deploy" is not a Build/Test/Quality prefix, but the full name contains "test" → Test lane.
    PipelineConfigDto dto = service.suggest(List.of("Deploy / Run server tests"));
    assertTrue(node(category(dto, "Test"), "Run server tests").isPresent());
  }

  @Test
  void distinctNamesWithTheSameSlugGetDistinctKeys() {
    // Both names slugify to "build-build" but are genuinely distinct jobs; neither is dropped and
    // their keys are made unique (the pipeline view tracks nodes by key).
    PipelineConfigDto dto = service.suggest(List.of("Build / Build", "Build / Build!"));
    CategoryConfig build = category(dto, "Build");
    assertEquals(2, build.nodes().size(), "distinct job names must not be de-duplicated");
    List<String> keys = build.nodes().stream().map(NodeConfig::key).toList();
    assertEquals(2, keys.stream().distinct().count(), "node keys must be unique within a category");
  }
}
