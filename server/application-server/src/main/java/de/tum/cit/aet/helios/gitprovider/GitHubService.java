package de.tum.cit.aet.helios.gitprovider;

import de.tum.cit.aet.helios.config.GitHubConfig;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class GitHubService {

    private static final Logger logger = LoggerFactory.getLogger(GitHubService.class);

    private final GitHub github;

    private final GitHubConfig gitHubConfig;

    private GHOrganization gitHubOrganization;

    @Autowired
    public GitHubService(GitHub github, GitHubConfig gitHubConfig) {
        this.github = github;
        this.gitHubConfig = gitHubConfig;
    }

    /**
     * Retrieves the GitHub organization client.
     *
     * @return the GitHub organization client
     * @throws IOException if an I/O error occurs
     */
    public GHOrganization getOrganizationClient() throws IOException {
        if (gitHubOrganization == null) {
            final String organizationName = gitHubConfig.getOrganizationName();
            if (organizationName == null || organizationName.isEmpty()) {
                logger.error("No organization name provided in the configuration. GitHub organization client will not be initialized.");
                throw new RuntimeException("No organization name provided in the configuration.");
            }
            gitHubOrganization = github.getOrganization(organizationName);
        }
        return gitHubOrganization;
    }

    /**
     * Retrieves the GitHub repository.
     *
     * @param repoNameWithOwners the repository name with owners
     * @return the GitHub repository
     * @throws IOException if an I/O error occurs
     */
    public GHRepository getRepository(String repoNameWithOwners) throws IOException {
        return github.getRepository(repoNameWithOwners);
    }

    /**
     * Retrieves the list of workflows for a given repository.
     *
     * @param repoNameWithOwners the repository name with owners
     * @return the list of workflows
     * @throws IOException if an I/O error occurs
     */
    public List<GHWorkflow> getWorkflows(String repoNameWithOwners) throws IOException {
        return getRepository(repoNameWithOwners).listWorkflows().toList();
    }

    /**
     * Retrieves a specific workflow for a given repository.
     *
     * @param repoNameWithOwners   the repository name with owners
     * @param workflowFileNameOrId the workflow file name or ID
     * @return the GitHub workflow
     * @throws IOException if an I/O error occurs
     */
    public GHWorkflow getWorkflow(String repoNameWithOwners, String workflowFileNameOrId) throws IOException {
        return getRepository(repoNameWithOwners).getWorkflow(workflowFileNameOrId);
    }

    /**
     * Dispatches a workflow for a given repository.
     *
     * @param repoNameWithOwners   the repository name with owners
     * @param workflowFileNameOrId the workflow file name or ID
     * @param ref                  the reference (branch or tag) to run the workflow on
     * @param inputs               the inputs for the workflow
     * @throws IOException if an I/O error occurs
     */
    public void dispatchWorkflow(String repoNameWithOwners, String workflowFileNameOrId, String ref, Map<String, Object> inputs) throws IOException {
        getRepository(repoNameWithOwners).getWorkflow(workflowFileNameOrId).dispatch(ref, inputs);
    }

}
