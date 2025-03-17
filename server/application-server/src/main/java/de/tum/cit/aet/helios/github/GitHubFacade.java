package de.tum.cit.aet.helios.github;

import java.io.IOException;
import java.util.List;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder;
import org.kohsuke.github.GHUser;

/**
 * This facade is used as a wrapper instead of directly using the GitHub bean. It ensures that the
 * implementation uses the most up-to-date GitHub client with the latest JWT token and calls the
 * respective GitHub methods.
 *
 * <p>If new methods of GitHub need to be called, this interface and implementation should be
 * updated to add that method. Use the same method signature as in GitHub.
 */
public interface GitHubFacade {
  public GHRepositorySearchBuilder searchRepositories();

  public GHUser getUser(String login) throws IOException;

  public GHOrganization getOrganization(String name) throws IOException;

  public GHRepository getRepository(String name) throws IOException;

  public GHRepository getRepositoryById(long id) throws IOException;

  /**
   * Creates a commit status for a specified commit in a GitHub repository.
   *
   * <p>This method creates a commit status that can be used to indicate build status, test results,
   * or other CI/CD information associated with a specific commit.
   *
   * @param repositoryNameWithOwner The repository name including the owner (e.g., "owner/repo")
   * @param sha The SHA hash of the commit for which to create the status
   * @param state The state of the commit status (e.g., PENDING, SUCCESS, ERROR, FAILURE)
   * @param targetUrl The URL that will be linked from the status for more details
   * @param description A short description explaining the status
   * @param context A string label to differentiate this status from statuses of other systems
   * @throws IOException If there's an error communicating with the GitHub API
   */
  public void createCommitStatus(String repositoryNameWithOwner, String sha,
                                 GHCommitState state,
                                 String targetUrl, String description, String context)
      throws IOException;

  public String getGithubAppName();

  /**
   * Retrieves the list of repositories associated with the GitHub App.
   *
   * <p>The method determines the repositories based on the available credentials:
   * </p>
   * <ul>
   *   <li>If no credentials are found, an empty list is returned.</li>
   *   <li>If a Personal Access Token (PAT) is available, the method returns the repositories
   *       specified in the corresponding environment variable.</li>
   *   <li>If GitHub App credentials are found, the method returns a combined list containing
   *       both the repositories from the environment variable and the actual installed repositories
   *       of the GitHub App.</li>
   * </ul>
   *
   * @return a list of repository full names in the format {@code owner/repository}.
   * @throws IOException if an error occurs while retrieving the repository list.
   */
  public List<String> getInstalledRepositoriesForGitHubApp() throws IOException;
}
