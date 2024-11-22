package de.tum.cit.aet.helios.deployment.environments;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.deployment.deployments.DeploymentDTO;
import de.tum.cit.aet.helios.deployment.environments.EnvironmentInfo.Status;
import de.tum.cit.aet.helios.deployment.installed_apps.InstalledAppDTO;


@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentInfoDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull String description,
        @NonNull String url,
        @NonNull Status status,
        @NonNull Long createdByUserId,
        OffsetDateTime createdAt,
        List<InstalledAppDTO> installedApps,
        List<DeploymentDTO> deployments
        )
        {

    public static EnvironmentInfoDTO fromEnvironmentInfo(EnvironmentInfo environment) {
        return new EnvironmentInfoDTO(
                environment.getId(),
                environment.getName(),
                environment.getDescription(),
                environment.getUrl(),
                environment.getStatus(),
                environment.getCreatedByUserId(),
                environment.getCreatedAt(),
                environment.getInstalledApps()
                        .stream()
                        .map(InstalledAppDTO::toInstalledAppDTO)
                        .sorted()
                        .toList(),
                environment.getDeployments()
                        .stream()
                        .map(DeploymentDTO::fromDeployment)
                        .sorted()
                        .toList()
        );
    }

}
