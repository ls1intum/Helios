package de.tum.cit.aet.helios.workflow.github;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.workflow.Workflow;
import de.tum.cit.aet.helios.workflow.WorkflowRepository;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

@Service
@Log4j2
public class GitHubWorkflowSyncService {

    private final GitRepoRepository gitRepoRepository;
    private final GitHubService gitHubService;
    private final GitHubWorkflowConverter workflowConverter;
    private final WorkflowRepository workflowRepository;

    public GitHubWorkflowSyncService(GitRepoRepository gitRepoRepository,
                                     GitHubService gitHubService,
                                     GitHubWorkflowConverter workflowConverter,
                                     WorkflowRepository workflowRepository) {
        this.gitRepoRepository = gitRepoRepository;
        this.gitHubService = gitHubService;
        this.workflowConverter = workflowConverter;
        this.workflowRepository = workflowRepository;
    }

    /**
     * Synchronizes all workflows from the specified GitHub repositories.
     *
     * @param repositories the list of GitHub repositories to sync workflows from
     */
    public void syncWorkflowsOfAllRepositories(@NotNull List<GHRepository> repositories) {
        repositories.forEach(this::syncWorkflowsOfRepository);
    }

    /**
     * Synchronizes all workflows from a specific GitHub repository.
     *
     * @param ghRepository the GitHub repository to sync workflows from
     */
    public void syncWorkflowsOfRepository(GHRepository ghRepository) {
        try {
            String fullName = ghRepository.getFullName();
            GitRepository repository = gitRepoRepository.findByNameWithOwner(fullName);
            if (repository == null) {
                log.warn("Repository {} not found in the database. Skipping workflow sync.", fullName);
                // TODO: sync repository
                return;
            }

            List<GHWorkflow> ghWorkflows = gitHubService.getWorkflows(fullName);

            for (GHWorkflow ghWorkflow : ghWorkflows) {
                processWorkflow(ghWorkflow, repository, ghRepository);
            }
        } catch (IOException e) {
            log.error("Failed to sync workflows for repository {}: {}", ghRepository.getFullName(), e.getMessage());
        }
    }

    /**
     * Processes a single GitHub workflow by updating or creating it in the local repository.
     *
     * @param ghWorkflow   the GitHub workflow to process
     * @param repository   the local Git repository associated with the workflow
     * @param ghRepository the GitHub repository the workflow belongs to
     */
    private void processWorkflow(@NotNull GHWorkflow ghWorkflow, @NotNull GitRepository repository, @NotNull GHRepository ghRepository) {
        Workflow workflow = workflowRepository.findById(ghWorkflow.getId())
                .orElseGet(Workflow::new);

        workflowConverter.update(ghWorkflow, workflow);

        // Link the workflow to the repository
        workflow.setRepository(repository);

        // Save the workflow
        workflowRepository.save(workflow);
    }
}
