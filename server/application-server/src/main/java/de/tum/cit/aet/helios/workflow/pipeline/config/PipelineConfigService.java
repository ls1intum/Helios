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
import java.util.Objects;
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

    // Sanitize on write: skip nameless categories / keyless-or-labelless nodes and drop null/blank
    // matchers, so a malformed payload can't persist a NOT-NULL violation or a null matcher that
    // would then NPE every subsequent read (the 15s pipeline poll included). Keys are made unique
    // within a category, since the pipeline view tracks nodes by key.
    int categoryOrder = 0;
    for (CategoryConfig categoryConfig : config.categories()) {
      if (isBlank(categoryConfig.name())) {
        continue;
      }
      final PipelineCategory category = new PipelineCategory();
      category.setGitRepoSettings(settings);
      category.setName(categoryConfig.name().trim());
      category.setOrderIndex(categoryOrder++);

      final java.util.Set<String> usedKeys = new java.util.HashSet<>();
      int nodeOrder = 0;
      for (NodeConfig nodeConfig : categoryConfig.nodes()) {
        if (isBlank(nodeConfig.label())) {
          continue;
        }
        final PipelineNode node = new PipelineNode();
        node.setPipelineCategory(category);
        node.setNodeKey(uniqueKey(nodeConfig.key(), nodeConfig.label(), usedKeys));
        node.setLabel(nodeConfig.label().trim());
        final String workflowMatcher = nodeConfig.workflowNameMatcher();
        node.setWorkflowNameMatcher(isBlank(workflowMatcher) ? null : workflowMatcher.trim());
        node.setJobNameMatchers(
            nodeConfig.jobNameMatchers().stream()
                .filter(matcher -> !isBlank(matcher))
                .map(String::trim)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new)));
        node.setOrderIndex(nodeOrder++);
        category.getNodes().add(node);
      }
      categoryRepository.save(category);
    }
    return toDto(categoryRepository.findByRepositoryIdOrdered(repositoryId));
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /** A slug-safe, unique-within-category node key (falls back to the label, then a suffix). */
  private static String uniqueKey(String key, String label, java.util.Set<String> used) {
    String base = isBlank(key) ? label : key;
    base = base.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-")
        .replaceAll("(^-+|-+$)", "");
    if (base.isEmpty()) {
      base = "node";
    }
    String candidate = base;
    int suffix = 2;
    while (!used.add(candidate)) {
      candidate = base + "-" + suffix++;
    }
    return candidate;
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
                                        // null-safe: a legacy row could hold a null matcher, and
                                        // List.copyOf throws on null elements.
                                        node.getJobNameMatchers().stream()
                                            .filter(Objects::nonNull)
                                            .toList(),
                                        node.getWorkflowNameMatcher()))
                            .toList()))
            .toList());
  }
}
