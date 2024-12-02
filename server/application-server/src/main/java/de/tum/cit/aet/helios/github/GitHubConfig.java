package de.tum.cit.aet.helios.github;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import okhttp3.Cache;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Configuration
@Log4j2
public class GitHubConfig {
    @Getter
    @Value("${github.organizationName}")
    private String organizationName;

    @Value("${github.authToken}")
    private String ghAuthToken;

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

    @Bean
    public GitHub createGitHubClientWithCache() {
        if (ghAuthToken == null || ghAuthToken.isEmpty()) {
            log.error("GitHub auth token is not provided! GitHub client will be disabled.");
            return GitHub.offline();
        }

        // Set up a logging interceptor for debugging
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        if (environment.matchesProfiles("debug")) {
            log.warn("The requests to GitHub will be logged with the full body. This exposes sensitive data such as OAuth tokens. Use only for debugging!");
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        } else {
            log.info("The requests to GitHub will be logged with the basic information.");
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        }

        OkHttpClient.Builder builder = new OkHttpClient().newBuilder();

        if (cacheEnabled) {
            File cacheDir = new File("./build/github-cache");
            Cache cache = new Cache(cacheDir, cacheSize * 1024L * 1024L);
            builder.cache(cache);
            log.info("Cache is enabled with TTL {} seconds and size {} MB", cacheTtl, cacheSize);
        } else {
            log.info("Cache is disabled");
        }

        // Configure OkHttpClient with the cache and logging
        OkHttpClient okHttpClient = builder
                .connectTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(loggingInterceptor)
                .build();

        try {
            // Initialize GitHub client with OAuth token and custom OkHttpClient
            GitHub github = new GitHubBuilder()
                    .withConnector(new OkHttpGitHubConnector(okHttpClient, cacheTtl))
                    .withOAuthToken(ghAuthToken)
                    .build();
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
            log.error("An unexpected error occurred during GitHub client initialization: {}", e.getMessage());
            throw new RuntimeException("Unexpected error during GitHub client initialization", e);
        }
    }
}
