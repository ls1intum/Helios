package de.tum.cit.aet.helios.github;

import de.tum.cit.aet.helios.deployment.GitHubDeployment;
import jakarta.annotation.PostConstruct;
import okhttp3.Request.Builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.environment.GitHubEnvironment;
import de.tum.cit.aet.helios.environment.GitHubEnvironmentResponse;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHWorkflow;
import org.kohsuke.github.GitHub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@Log4j2
public class GitHubService {
    private final GitHub github;

    private final GitHubConfig gitHubConfig;

    private final ObjectMapper objectMapper;

    private final OkHttpClient okHttpClient;

    @Value("${github.authToken}")
    private String ghAuthToken;

    // Request builder for GitHub API calls with authorization header
    private Builder requestBuilder;

    private GHOrganization gitHubOrganization;

    public GitHubService(GitHub github, GitHubConfig gitHubConfig, ObjectMapper objectMapper, OkHttpClient okHttpClient) {
        this.github = github;
        this.gitHubConfig = gitHubConfig;
        this.objectMapper = objectMapper;
        this.okHttpClient = okHttpClient;
    }

    @PostConstruct
    public void init() {
        this.requestBuilder = new Request.Builder()
                .header("Authorization", "token " + ghAuthToken)
                .header("Accept", "application/vnd.github+json");
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
                log.error("No organization name provided in the configuration. GitHub organization client will not be initialized.");
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

    /**
     * Retrieves environments from a GitHub repository and maps them to the GitHubEnvironment.
     *
     * @param repository the GitHub repository as a GHRepository object
     * @return a list of GitHubEnvironment objects
     * @throws IOException if an I/O error occurs
     */
    public List<GitHubEnvironment> getEnvironments(GHRepository repository) throws IOException {
        final String owner = repository.getOwnerName();
        final String repoName = repository.getName();
        final String url = String.format("https://api.github.com/repos/%s/%s/environments", owner, repoName);

        Request request = requestBuilder
                .url(url)
                .build();

        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("GitHub API call failed with response code: " + response.code());
            }

            if (response.body() == null) {
                throw new IOException("Response body is null");
            }

            String responseBody = response.body().string();
            GitHubEnvironmentResponse envResponse = objectMapper.readValue(responseBody, GitHubEnvironmentResponse.class);
            return envResponse.getEnvironments();
        } catch (JsonProcessingException e) {
            log.error("Error processing JSON response: {}", e.getMessage());
            throw e;
        } catch (IOException e) {
            log.error("Error occurred while fetching environments: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Retrieves a GitHub deployment iterator for a given repository and environment.
     *
     * @param repository      the GitHub repository as a GHRepository object
     * @param environmentName the environment name
     * @return a GitHubDeploymentIterator object
     */
    public Iterator<GitHubDeployment> getDeploymentIterator(GHRepository repository, String environmentName) {
        return new GitHubDeploymentIterator(repository, environmentName, okHttpClient, requestBuilder, objectMapper);
    }
}
