package de.tum.cit.aet.helios.github;

import de.tum.cit.aet.helios.util.GitHubAppJwtHelper;
import java.io.File;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.kohsuke.github.GHApp;
import org.kohsuke.github.GHAppInstallation;
import org.kohsuke.github.GHAppInstallationToken;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@Log4j2
public class GitHubConfig {
  @Getter
  @Value("${github.organizationName}")
  private String organizationName;

  @Value("${github.authToken}")
  private String ghAuthToken;

  @Value("${github.appId}")
  private Long appId;

  @Value("${github.clientId}")
  private String clientId;

  @Value("${github.installationId}")
  private Long installationId;

  @Value("${github.privateKeyPath}")
  private String privateKeyPath;

  @Value("${github.cache.enabled}")
  private boolean cacheEnabled;

  @Value("${github.cache.ttl}")
  private int cacheTtl;

  @Value("${github.cache.size}")
  private int cacheSize;

  private final Environment environment;

  public GitHubConfig(Environment environment) {
    this.environment = environment;
  }

  private OkHttpClient.Builder buildOkHttpClientBuilder() {
    // Set up a logging interceptor for debugging
    HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
    if (environment.acceptsProfiles("debug")) {
      log.warn("Requests to GitHub will be logged with full body. Use only for debugging!");
      loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
    } else {
      log.info("Requests to GitHub will be logged with basic information.");
      loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
    }

    OkHttpClient.Builder builder = new OkHttpClient.Builder();

    if (cacheEnabled) {
      File cacheDir = new File("./build/github-cache");
      Cache cache = new Cache(cacheDir, cacheSize * 1024L * 1024L);
      builder.cache(cache);
      log.info("Cache enabled with TTL {} seconds and size {} MB", cacheTtl, cacheSize);
    } else {
      log.info("Cache is disabled");
    }

    // Configure timeouts and add the logging interceptor
    builder
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor);

    return builder;
  }

  @Bean
  public OkHttpClient okHttpClient() {
    return buildOkHttpClientBuilder().build();
  }

  @Bean
  public GitHub createGitHubClientWithCache(OkHttpClient okHttpClient) {
    try {
      GitHub github = null;

      if (appId != null && privateKeyPath != null) {
        // Initialize GitHub client with GitHub App credentials
        github = createGitHubClientForGitHubApp();
      } else if (ghAuthToken == null || ghAuthToken.isEmpty()) {
        // Initialize GitHub client with PAT
        github =
            new GitHubBuilder()
                .withConnector(new OkHttpGitHubConnector(okHttpClient, cacheTtl))
                .withOAuthToken(ghAuthToken)
                .build();
        if (github.isOffline()) {
          return github;
        }
      } else {
        log.error(
            "GitHub auth token or private key is not provided! GitHub client will be disabled.");
        return GitHub.offline();
      }

      if (github == null) {
        log.error("GitHub client is null! GitHub client will be disabled.");
        return GitHub.offline();
      }

      if (!github.isCredentialValid()) {
        log.error("Invalid GitHub credentials!");
        throw new IllegalStateException("Invalid GitHub credentials");
      }
      log.info("GitHub client initialized successfully");
      return github;

    } catch (IOException e) {
      log.error("Failed to initialize GitHub client: {}", e.getMessage());
      throw new RuntimeException("GitHub client initialization failed", e);
    } catch (Exception e) {
      log.error(
          "An unexpected error occurred during GitHub client initialization: {}", e.getMessage());
      throw new RuntimeException("Unexpected error during GitHub client initialization", e);
    }
  }

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
              .withConnector(new OkHttpGitHubConnector(okHttpClient(), cacheTtl))
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
      }

      // Create an installation token
      GHAppInstallation installation = githubAppClient.getApp().getInstallationById(installationId);
      GHAppInstallationToken token = installation.createToken().create();
      String installationToken = token.getToken();

      // Build the final client using the installation token
      GitHub installationClient =
          new GitHubBuilder()
              .withConnector(new OkHttpGitHubConnector(okHttpClient(), cacheTtl))
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
