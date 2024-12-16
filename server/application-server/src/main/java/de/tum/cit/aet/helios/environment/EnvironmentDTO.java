package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentDTO(
        RepositoryInfoDTO repository,
        @NonNull Long id,
        @NonNull String name,
        String url,
        String htmlUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<String> installedApps) {

    public static EnvironmentDTO fromEnvironment(Environment environment) {
        return new EnvironmentDTO(
                RepositoryInfoDTO.fromRepository(environment.getRepository()),
                environment.getId(),
                environment.getName(),
                environment.getUrl(),
                environment.getHtmlUrl(),
                environment.getCreatedAt(),
                environment.getUpdatedAt(),
                environment.getInstalledApps()
        );
    }
}
