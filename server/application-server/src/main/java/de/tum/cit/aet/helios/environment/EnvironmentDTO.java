package de.tum.cit.aet.helios.environment;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.branch.BranchInfoDTO;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDTO;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentDTO(
        RepositoryInfoDTO repository,
        @NonNull Long id,
        @NonNull String name,
        boolean locked,
        String url,
        String htmlUrl,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        BranchInfoDTO lockingBranch,
        boolean deploying) {

    public static EnvironmentDTO fromEnvironment(Environment environment) {
        return new EnvironmentDTO(
                RepositoryInfoDTO.fromRepository(environment.getRepository()),
                environment.getId(),
                environment.getName(),
                environment.isLocked(),
                environment.getUrl(),
                environment.getHtmlUrl(),
                environment.getCreatedAt(),
                environment.getUpdatedAt(),
                (environment.getLockingBranch() != null ? BranchInfoDTO.fromBranch(environment.getLockingBranch()) : null),
                environment.isDeploying()
        );
    }
}
