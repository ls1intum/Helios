package de.tum.cit.aet.helios.github;

import de.tum.cit.aet.helios.util.GitHubAppJwtHelper;
import java.io.File;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.springframework.beans.factory.annotation.Value;

/**
 * Manages the GitHub client for the application.
 *
 * <p>This class is responsible for creating and refreshing the GitHub client
 * used to interact with the GitHub API. It handles authentication using
 * either a GitHub App or a personal access token (PAT), and ensures that
 * the client is always up-to-date with a valid token.
 * </p>
 *
 * <p>The GitHub client is cached and refreshed every 20 minutes to ensure
 * that the token does not expire during usage.
 * </p>
 */
@Log4j2
public class GitHubClientManager {


  /**
   * The personal access token (PAT) for GitHub authentication.
   * Note: All PAT related features should be removed in later iterations.
   */
  private final String ghAuthToken;
  /**
   * The organization name to find the installation ID of the GitHub App.
   */
  private final String organizationName;
  /**
   * The GitHub App ID.
   */
  private final Long appId;
  /**
   * The installation ID of the GitHub App. It can be found in organization settings.
   * Example URL: {@code https://github.com/organizations/<org-name>/settings/installations/<installation-id>}
   * If installationId is not provided, it will be found by the organization name.
   */
  private Long installationId;
  /**
   * The path to the private key file (pem) of the GitHub App.
   * The private key must be generated in GitHub App settings.
   * Generated key is in PKCS#1 format. It must be converted to PKCS#8 format.
   */
  private final String privateKeyPath;

  private final OkHttpClient okHttpClient;

  /**
   * The GitHub client instance.
   */
  private volatile GitHub gitHubClient;
  /**
   * The current token used for authentication.
   */
  private volatile String currentToken;
  /**
   * The expiration time of the current token.
   */
  private volatile Instant tokenExpirationTime;

  private final ReentrantLock lock = new ReentrantLock();

  @Value("${http.cache.ttl}")
  private int cacheTtl;

  public GitHubClientManager(String organizationName,
                             String ghAuthToken,
                             Long appId,
                             Long installationId,
                             String privateKeyPath,
                             OkHttpClient okHttpClient) {
    this.organizationName = organizationName;
    this.ghAuthToken = ghAuthToken;
    this.appId = appId;
    this.installationId = installationId;
    this.privateKeyPath = privateKeyPath;
    this.okHttpClient = okHttpClient;
  }

  /**
   * Returns the current GitHub token. It is either a PAT or an installation token.
   *
   * @return the current token
   */
  public String getCurrentToken() {
    if (currentToken == null || Instant.now().isAfter(tokenExpirationTime)) {
      refreshClient();
    }
    return currentToken;
  }

  /**
   * Returns a GitHub client instance.
   * If the client is null or the token has expired,
   * a new client will be created. Generated client is valid for 1 hour.
   * Every 20 minutes, the client will be refreshed.
   *
   * @return the GitHub client
   */
  public GitHub getGitHubClient() {
    if (gitHubClient == null || Instant.now().isAfter(tokenExpirationTime)) {
      refreshClient();
    }
    return gitHubClient;
  }

  /**
   * Refreshes the GitHub client in every 20 minutes.
   */
  private void refreshClient() {
    lock.lock();
    try {
      if (gitHubClient == null || Instant.now().isAfter(tokenExpirationTime)) {
        log.info("Refreshing GitHub client...");
        gitHubClient = createGitHubClientWithCache();
        log.info("GitHub client refreshed successfully");
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Creates a GitHub client with caching enabled.
   *
   * @return the GitHub client
   */
  public GitHub createGitHubClientWithCache() {
    try {
      GitHub github = null;

      if (appId != null && privateKeyPath != null) {
        // Initialize GitHub client with GitHub App credentials
        github = createGitHubClientForGitHubApp();

        // Set token expiration time to 20 minutes from now
        tokenExpirationTime = Instant.now().plusSeconds(60 * 20);
      } else if (ghAuthToken == null || ghAuthToken.isEmpty()) {
        // Set current token to ghAuthToken
        currentToken = ghAuthToken;
        // Initialize GitHub client with PAT
        github =
            new GitHubBuilder()
                .withConnector(new OkHttpGitHubConnector(okHttpClient, cacheTtl))
                .withOAuthToken(ghAuthToken)
                .build();

        // Set token expiration time to 1 year from now (PATs don't change)
        tokenExpirationTime = Instant.now().plusSeconds(60 * 60 * 24 * 365);
      } else {
        log.error(
            "GitHub auth token or private key is not provided! GitHub client will be disabled.");
        return GitHub.offline();
      }

      if (github == null || github.isOffline()) {
        log.error("GitHub client is null or Offline! GitHub client will be disabled.");
        return GitHub.offline();
      }

      if (!github.isCredentialValid()) {
        log.error("Invalid GitHub credentials!");
        throw new IllegalStateException("Invalid GitHub credentials");
      }
      log.info("GitHub client initialized successfully");
      return github;

    } catch (Exception e) {
      log.error(
          "An unexpected error occurred during GitHub client initialization: {}", e.getMessage());
      throw new RuntimeException("Unexpected error during GitHub client initialization", e);
    }
  }

  /**
   * Creates a GitHub client for a GitHub App installation.
   * Follows the GitHub documentation <a href="https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/about-authentication-with-a-github-app">About authentication with a GitHub App</a>
   *
   * @return the GitHub client
   */
  public GitHub createGitHubClientForGitHubApp() {
    // Load the private key file from disk
    File pem = new File(privateKeyPath);
    if (!pem.exists()) {
      log.error("GitHub App PEM file not found at path: {}", privateKeyPath);
      return GitHub.offline();
    }

    try {
      // Load the private key
      PrivateKey pk = GitHubAppJwtHelper.loadPrivateKey(pem);
      // Generate short-lived JWT for the App
      String jwt = GitHubAppJwtHelper.generateAppJwt(appId, pk);

      // Create a GitHub client using the JWT
      GitHub githubAppClient =
          new GitHubBuilder()
              .withConnector(new OkHttpGitHubConnector(okHttpClient, cacheTtl))
              .withJwtToken(jwt)
              .build();

      // If the installation ID is not provided, try to find it by org name
      if (installationId == null) {
        GHApp app = githubAppClient.getApp();
        List<GHAppInstallation> installs = app.listInstallations().toList();
        log.info("Found {} installations for this GitHub App", installs.size());

        // Pick the correct one by org name
        installationId = installs.stream()
            .filter(inst -> organizationName != null
                && organizationName.equalsIgnoreCase(inst.getAccount().getLogin()))
            .map(GHAppInstallation::getId)
            .findFirst()
            .orElse(null);

        if (installationId == null) {
          log.error("Could not find a matching installation for org: {}", organizationName);
          return GitHub.offline();
        }

        log.info("Found installation ID for org {}: {}", organizationName, installationId);
      }

      // Create an installation token
      GHAppInstallation installation = githubAppClient.getApp().getInstallationById(installationId);
      GHAppInstallationToken token = installation.createToken().create();
      String installationToken = token.getToken();
      // Set the current token to the installation token
      currentToken = installationToken;

      // Build the final client using the installation token
      GitHub installationClient =
          new GitHubBuilder()
              .withConnector(new OkHttpGitHubConnector(okHttpClient, cacheTtl))
              .withAppInstallationToken(installationToken)
              .build();

      log.info("GitHub Installation client created successfully (installationId={})",
          installationId);
      return installationClient;

    } catch (Exception e) {
      log.error("Failed to create GitHub App installation client: {}", e.getMessage(), e);
      return GitHub.offline();
    }

  }
}