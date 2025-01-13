package de.tum.cit.aet.helios.github;

import java.io.IOException;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder;
import org.kohsuke.github.GHUser;

/**
 * This facade is used as a wrapper instead of directly using the GitHub bean.
 * It ensures that the implementation uses the most up-to-date GitHub client
 * with the latest JWT token and calls the respective GitHub methods.
 *
 * <p>If new methods of GitHub need to be called, this interface and implementation
 * should be updated to add that method. Use the same method signature as in GitHub.
 * </p>
 */
public interface GitHubFacade {
  public GHRepositorySearchBuilder searchRepositories();

  public GHUser getUser(String login) throws IOException;

  public GHOrganization getOrganization(String name) throws IOException;

  public GHRepository getRepository(String name) throws IOException;
}
