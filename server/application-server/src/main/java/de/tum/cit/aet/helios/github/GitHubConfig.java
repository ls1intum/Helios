package de.tum.cit.aet.helios.github;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Log4j2
public class GitHubConfig {
  @Getter
  @Value("${github.organizationName}")
  private String organizationName;

  @Value("${github.authToken}")
  private String ghAuthToken;

  @Value("${github.appName}")
  private String appNameWithoutSuffix;

  @Value("${github.appId:#{null}}")
  private Long appId;

  @Value("${github.clientId}")
  private String clientId;

  @Value("${github.installationId:#{null}}")
  private Long installationId;

  @Value("${github.privateKeyPath}")
  private String privateKeyPath;

  private final OkHttpClient okHttpClient;

  public GitHubConfig(OkHttpClient okHttpClient) {
    this.okHttpClient = okHttpClient;
  }

  @Bean
  public GitHubClientManager gitHubClientManager() {
    return new GitHubClientManager(
        organizationName,
        ghAuthToken,
        appNameWithoutSuffix,
        appId,
        installationId,
        privateKeyPath,
        okHttpClient);
  }

  /**
   * Creates a GitHubFacade instance. This facade is used instead of directly using the GitHub bean
   * to handle the automatic refreshing of the JWT token used for GitHub API calls.
   *
   * @param manager The GitHub client manager.
   * @return The GitHubFacade instance.
   */
  @Bean
  public GitHubFacade githubClient(GitHubClientManager manager) {
    return new GitHubFacadeImpl(manager);
  }
}
