package de.tum.cit.aet.helios.workflow.pipeline.config;

import java.util.List;
import org.springframework.lang.Nullable;

/**
 * The editable, per-repository pipeline configuration (the WRITE model), distinct from the runtime
 * {@code PipelineDto} the pipeline view renders. Nested names are {@code CategoryConfig} /
 * {@code NodeConfig} (not Category/Node) to avoid OpenAPI schema collisions with the read model.
 */
public record PipelineConfigDto(List<CategoryConfig> categories) {

  public PipelineConfigDto {
    categories = categories == null ? List.of() : categories;
  }

  /** A configurable stage lane and its ordered nodes. */
  public record CategoryConfig(String name, List<NodeConfig> nodes) {
    public CategoryConfig {
      nodes = nodes == null ? List.of() : nodes;
    }
  }

  /** A configurable node mapped to CI jobs by name prefix (+ optional workflow-name filter). */
  public record NodeConfig(
      String key,
      String label,
      List<String> jobNameMatchers,
      @Nullable String workflowNameMatcher) {
    public NodeConfig {
      jobNameMatchers = jobNameMatchers == null ? List.of() : jobNameMatchers;
    }
  }
}
