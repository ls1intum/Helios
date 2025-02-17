package de.tum.cit.aet.helios.github;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepositorySearchBuilder;
import org.kohsuke.github.GHUser;
import org.springframework.beans.factory.annotation.Value;

/**
 * Facade for GitHub API.
 */
@Log4j2
@RequiredArgsConstructor
public class GitHubFacadeImpl implements GitHubFacade {


  @Value("${monitoring.repositories:}")
  private String[] repositoriesToMonitor;

  private final GitHubClientManager clientManager;

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

  @Override
  public List<String> getInstalledRepositoriesForGitHubApp() throws IOException {
    if (clientManager.getAuthType() == null) {
      return List.of();
    }

    // Sync repositories that defined in the environment variable
    Stream<String> envRepoNames = Arrays.stream(repositoriesToMonitor);
    log.info("Repositories from environment: {}", Arrays.toString(repositoriesToMonitor));

    if (GitHubClientManager.AuthType.PAT.equals(clientManager.getAuthType())) {
      return envRepoNames.toList();
    }

    List<String> installedRepositories = clientManager.getGitHubClient()
        .getInstallation()
        .listRepositories()
        .toList()
        .stream()
        .map(GHRepository::getFullName)
        .toList();

    List<String> allRepositories = Stream.concat(envRepoNames, installedRepositories.stream())
        .distinct()
        .toList();

    log.info("Final list of repositories to sync: {}", allRepositories);
    return allRepositories;
  }
}
