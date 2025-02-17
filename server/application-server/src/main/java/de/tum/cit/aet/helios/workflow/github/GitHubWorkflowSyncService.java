package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import jakarta.transaction.Transactional;
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
}
