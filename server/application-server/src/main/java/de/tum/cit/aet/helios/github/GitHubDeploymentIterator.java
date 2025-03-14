package de.tum.cit.aet.helios.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.deployment.github.GitHubDeploymentDto;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Queue;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.kohsuke.github.GHRepository;

@Log4j2
@RequiredArgsConstructor
public class GitHubDeploymentIterator implements Iterator<GitHubDeploymentDto> {

  private final GHRepository repository;
  private final String environmentName;
  private final OkHttpClient okHttpClient;
  private final Request.Builder requestBuilder;
  private final ObjectMapper objectMapper;
  private final Optional<OffsetDateTime> since;

  private int currentPage = 1;
  private static final int PER_PAGE = 100;
  private boolean hasMore = true;
  private final Queue<GitHubDeploymentDto> deploymentQueue = new LinkedList<>();

  @Override
  public boolean hasNext() {
    if (deploymentQueue.isEmpty() && hasMore) {
      fetchNextPage();
    }
    return !deploymentQueue.isEmpty();
  }

  @Override
  public GitHubDeploymentDto next() {
    if (!hasNext()) {
      throw new NoSuchElementException("No more deployments available.");
    }
    return deploymentQueue.poll();
  }

  private void fetchNextPage() {
    String owner = repository.getOwnerName();
    String repoName = repository.getName();
    String baseUrl =
        String.format("https://api.github.com/repos/%s/%s/deployments", owner, repoName);
    String url =
        String.format(
            "%s?environment=%s&page=%d&per_page=%d",
            baseUrl,
            URLEncoder.encode(environmentName, StandardCharsets.UTF_8),
            currentPage,
            PER_PAGE);

    Request request = requestBuilder.url(url).build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        if (response.code() == 404) {
          hasMore = false;
          return;
        }
        throw new IOException("GitHub API call failed with response code: " + response.code());
      }

      if (response.body() == null) {
        throw new IOException("Response body is null");
      }

      String responseBody = response.body().string();
      List<GitHubDeploymentDto> deployments =
          objectMapper.readValue(responseBody, new TypeReference<>() {});

      // Filter deployments based on the `since` parameter if provided
      if (since.isPresent()) {
        OffsetDateTime sinceTime = since.get();
        deployments.removeIf(deployment -> deployment.getCreatedAt().isBefore(sinceTime));
      }

      if (deployments.size() < PER_PAGE) {
        hasMore = false;
      } else {
        currentPage++;
      }

      deploymentQueue.addAll(deployments);
    } catch (IOException e) {
      log.error("Error occurred while fetching deployments: {}", e.getMessage());
      hasMore = false;
      throw new RuntimeException("Failed to fetch deployments", e);
    }
  }
}
