package de.tum.cit.aet.helios.workflow.pipeline.config;

import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettingsRepository;
import de.tum.cit.aet.helios.workflow.WorkflowJobRepository;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.CategoryConfig;
import de.tum.cit.aet.helios.workflow.pipeline.config.PipelineConfigDto.NodeConfig;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes the per-repository pipeline configuration.
 *
 * <p>{@link #getConfig} is read-only and never writes: it returns the persisted config if present,
 * otherwise a transient default auto-detected from the repo's observed CI jobs (always the
 * Build/Test/Quality lanes, populated when jobs exist). {@link #updateConfig} persists a
 * maintainer's edits, replacing the whole collection.
 */
@Service
@RequiredArgsConstructor
public class PipelineConfigService {

  private final PipelineCategoryRepository categoryRepository;
  private final GitRepoSettingsRepository gitRepoSettingsRepository;
  private final GitRepoRepository gitRepoRepository;
  private final WorkflowJobRepository workflowJobRepository;
  private final PipelineDetectionService detectionService;

  /** Persisted config for the repo, or a transient auto-detected default when none exists yet. */
  @Transactional(readOnly = true)
  public PipelineConfigDto getConfig(Long repositoryId) {
    final List<PipelineCategory> categories =
        categoryRepository.findByRepositoryIdOrdered(repositoryId);
    return categories.isEmpty() ? suggest(repositoryId) : toDto(categories);
  }

  /** A suggested config auto-detected from the repository's observed CI job names. */
  @Transactional(readOnly = true)
  public PipelineConfigDto suggest(Long repositoryId) {
    return detectionService.suggest(
        workflowJobRepository.findDistinctJobNamesByRepositoryId(repositoryId));
  }

  /** Replaces the repository's pipeline config with the given categories/nodes. */
  @Transactional
  public PipelineConfigDto updateConfig(Long repositoryId, PipelineConfigDto config) {
    final GitRepoSettings settings = getOrCreateSettings(repositoryId);

    // Delete existing categories (cascades nodes + matchers) and flush before inserting new ones,
    // so the (repository_settings_id, order_index) unique constraint isn't violated mid-flush.
    categoryRepository.deleteAll(categoryRepository.findByRepositoryIdOrdered(repositoryId));
    categoryRepository.flush();

    int categoryOrder = 0;
    for (CategoryConfig categoryConfig : config.categories()) {
      final PipelineCategory category = new PipelineCategory();
      category.setGitRepoSettings(settings);
      category.setName(categoryConfig.name());
      category.setOrderIndex(categoryOrder++);

      int nodeOrder = 0;
      for (NodeConfig nodeConfig : categoryConfig.nodes()) {
        final PipelineNode node = new PipelineNode();
        node.setPipelineCategory(category);
        node.setNodeKey(nodeConfig.key());
        node.setLabel(nodeConfig.label());
        node.setWorkflowNameMatcher(nodeConfig.workflowNameMatcher());
        node.setJobNameMatchers(new ArrayList<>(nodeConfig.jobNameMatchers()));
        node.setOrderIndex(nodeOrder++);
        category.getNodes().add(node);
      }
      categoryRepository.save(category);
    }
    return toDto(categoryRepository.findByRepositoryIdOrdered(repositoryId));
  }

  private GitRepoSettings getOrCreateSettings(Long repositoryId) {
    return gitRepoSettingsRepository
        .findByRepositoryRepositoryId(repositoryId)
        .orElseGet(
            () -> {
              final GitRepository repository =
                  gitRepoRepository
                      .findByRepositoryId(repositoryId)
                      .orElseThrow(
                          () ->
                              new IllegalArgumentException(
                                  "Repository not found: " + repositoryId));
              final GitRepoSettings created = new GitRepoSettings();
              created.setRepository(repository);
              return gitRepoSettingsRepository.save(created);
            });
  }

  private static PipelineConfigDto toDto(List<PipelineCategory> categories) {
    return new PipelineConfigDto(
        categories.stream()
            .map(
                category ->
                    new CategoryConfig(
                        category.getName(),
                        category.getNodes().stream()
                            .map(
                                node ->
                                    new NodeConfig(
                                        node.getNodeKey(),
                                        node.getLabel(),
                                        List.copyOf(node.getJobNameMatchers()),
                                        node.getWorkflowNameMatcher()))
                            .toList()))
            .toList());
  }
}
