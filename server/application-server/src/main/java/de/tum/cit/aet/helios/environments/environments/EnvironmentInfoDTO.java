package de.tum.cit.aet.helios.environments.environments;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.lang.NonNull;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.helios.environments.environments.EnvironmentInfo.Status;
import de.tum.cit.aet.helios.environments.installed_applications.InstalledApplicationDTO;
import de.tum.cit.aet.helios.gitprovider.user.User;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record EnvironmentInfoDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull String description,
        @NonNull String url,
        @NonNull Status status,
        @NonNull User createdBy,
        OffsetDateTime createdAt,
        List<InstalledApplicationDTO> installedApplications)
        {

    public static EnvironmentInfoDTO fromEnvironment(EnvironmentInfo environment) {
        return new EnvironmentInfoDTO(
                environment.getId(),
                environment.getName(),
                environment.getDescription(),
                environment.getUrl(),
                environment.getStatus(),
                environment.getCreatedBy(),
                environment.getCreatedAt(),
                environment.getInstalledApplications()
                        .stream()
                        .map(InstalledApplicationDTO::fromInstalledApplication)
                        .sorted()
                        .toList());
    }

}
