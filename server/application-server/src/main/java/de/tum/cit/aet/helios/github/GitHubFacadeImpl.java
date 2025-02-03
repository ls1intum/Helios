package de.tum.cit.aet.helios.github;

import java.io.IOException;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder;
import org.kohsuke.github.GHUser;

/** Facade for GitHub API. */
public class GitHubFacadeImpl implements GitHubFacade {

  private final GitHubClientManager clientManager;

  public GitHubFacadeImpl(GitHubClientManager clientManager) {
    this.clientManager = clientManager;
  }

  @Override
  public GHRepositorySearchBuilder searchRepositories() {
    return clientManager.getGitHubClient().searchRepositories();
  }

  @Override
  public GHUser getUser(String login) throws IOException {
    return clientManager.getGitHubClient().getUser(login);
  }

  @Override
  public GHOrganization getOrganization(String name) throws IOException {
    return clientManager.getGitHubClient().getOrganization(name);
  }

  @Override
  public GHRepository getRepository(String name) throws IOException {
    return clientManager.getGitHubClient().getRepository(name);
  }

  @Override
  public GHRepository getRepositoryById(long id) throws IOException {
    return clientManager.getGitHubClient().getRepositoryById(id);
  }

  @Override
  public String getGithubAppName() {
    return clientManager.getAppName();
  }
}
