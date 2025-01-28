package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import de.tum.cit.aet.helios.tag.Tag;
import de.tum.cit.aet.helios.tag.TagRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentDto(
    RepositoryInfoDto repository,
    @NonNull Long id,
    @NonNull String name,
    boolean locked,
    String url,
    String htmlUrl,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    boolean enabled,
    List<String> installedApps,
    String description,
    String serverUrl,
    EnvironmentDeployment latestDeployment,
    String lockedBy,
    OffsetDateTime lockedAt) {

  public static record EnvironmentDeployment(
      @NonNull Long id,
      @NonNull String url,
      Deployment.State state,
      @NonNull String statusesUrl,
      @NonNull String sha,
      @NonNull String ref,
      @NonNull String task,
      String tagName,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt) {

    public static EnvironmentDeployment fromDeployment(Deployment deployment, String tagName) {
      return new EnvironmentDeployment(
          deployment.getId(),
          deployment.getUrl(),
          deployment.getState(),
          deployment.getStatusesUrl(),
          deployment.getSha(),
          deployment.getRef(),
          deployment.getTask(),
          tagName,
          deployment.getCreatedAt(),
          deployment.getUpdatedAt());
    }
  }

  public static EnvironmentDto fromEnvironment(
      Environment environment, Optional<Deployment> latestDeployment, TagRepository tagRepository) {
    return new EnvironmentDto(
        RepositoryInfoDto.fromRepository(environment.getRepository()),
        environment.getId(),
        environment.getName(),
        environment.isLocked(),
        environment.getUrl(),
        environment.getHtmlUrl(),
        environment.getCreatedAt(),
        environment.getUpdatedAt(),
        environment.isEnabled(),
        environment.getInstalledApps(),
        environment.getDescription(),
        environment.getServerUrl(),
        latestDeployment
            .map(
                deployment ->
                    EnvironmentDeployment.fromDeployment(
                        deployment,
                        tagRepository
                            .findByRepositoryRepositoryIdAndCommitSha(
                                deployment.getRepository().getRepositoryId(), deployment.getSha())
                            .map(Tag::getName)
                            .orElse(null)))
            .orElse(null),
        environment.getLockedBy(),
        environment.getLockedAt());
  }

  public static EnvironmentDto fromEnvironment(Environment environment) {
    return EnvironmentDto.fromEnvironment(environment, Optional.empty(), null);
  }
}
