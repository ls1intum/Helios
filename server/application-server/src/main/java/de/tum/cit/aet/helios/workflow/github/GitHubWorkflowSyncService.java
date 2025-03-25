package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class GitHubWorkflowSyncService {

  private final GitHubWorkflowConverter workflowConverter;
  private final WorkflowRepository workflowRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubService gitHubService;

  /**
   * Processes a single GitHub workflow by updating or creating it in the local repository.
   *
   * @param ghWorkflow the GitHub workflow to process
   * @param repository the local Git repository associated with the workflow
   * @param ghRepository the GitHub repository the workflow belongs to
   */
  @Transactional
  public void processWorkflow(
      @NotNull GHWorkflow ghWorkflow,
      @NotNull GitRepository repository,
      @NotNull GHRepository ghRepository) {
    Workflow workflow = workflowRepository.findById(ghWorkflow.getId()).orElseGet(Workflow::new);

    workflowConverter.update(ghWorkflow, workflow);

    // Link the workflow to the repository
    workflow.setRepository(repository);

    // Save the workflow
    workflowRepository.save(workflow);
  }

  public void syncRepositoryWorkflows(final long repositoryId) {
    var repo = gitRepoRepository.findById(repositoryId).orElseThrow();

    try {
      var ghRepo = this.gitHubService.getRepository(repo.getNameWithOwner());
      List<GHWorkflow> ghWorkflows = gitHubService.getWorkflows(repo.getNameWithOwner());

      for (GHWorkflow ghWorkflow : ghWorkflows) {
        this.processWorkflow(ghWorkflow, repo, ghRepo);
      }
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to sync workflows for repository " + repo.getNameWithOwner(), e);
    }
  }
}
