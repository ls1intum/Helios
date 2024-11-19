package de.tum.cit.aet.helios.environments.installed_applications;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.micrometer.common.lang.NonNull;


@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record InstalledApplicationDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull Long environmentId)
    {

    public static InstalledApplicationDTO fromInstalledApplication(InstalledApplication installedApplication) {
        return new InstalledApplicationDTO(
                installedApplication.getId(),
                installedApplication.getName(),
                installedApplication.getEnvironmentId());
    }

    
}
