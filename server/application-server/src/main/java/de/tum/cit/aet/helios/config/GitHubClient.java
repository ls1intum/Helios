package de.tum.cit.aet.helios.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.github.permissions.GitHubPermissionsResponse;
import de.tum.cit.aet.helios.github.permissions.GitHubRepositoryRoleDto;
import de.tum.cit.aet.helios.github.permissions.RepoPermissionType;
import java.io.IOException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GitHubClient {
  private final OkHttpClient okHttpClient;
  private final ObjectMapper objectMapper;
  private final String githubToken;

  public GitHubClient(
      OkHttpClient okHttpClient,
      ObjectMapper objectMapper,
      @Value("${github.authToken}") String githubToken) {
    this.okHttpClient = okHttpClient;
    this.objectMapper = objectMapper;
    this.githubToken = githubToken;
  }

  public GitHubRepositoryRoleDto getRepositoryPermissions(
      String repositoryId, String githubUsername) throws IOException {
    String url =
        "https://api.github.com/repositories/"
            + repositoryId
            + "/collaborators/"
            + githubUsername
            + "/permission";

    Request request =
        new Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer " + githubToken)
            .header("Accept", "application/vnd.github+json")
            .build();

    try (Response response = okHttpClient.newCall(request).execute()) {
      if (!response.isSuccessful() || response.body() == null) {
        throw new IOException("Failed to fetch repository permissions: " + response.message());
      }
      String responseBody = response.body().string();

      GitHubPermissionsResponse permissionResponse =
          objectMapper.readValue(responseBody, GitHubPermissionsResponse.class);
      return new GitHubRepositoryRoleDto(
          RepoPermissionType.fromString(permissionResponse.getPermission()),
          permissionResponse.getRoleName());
    }
  }
}
