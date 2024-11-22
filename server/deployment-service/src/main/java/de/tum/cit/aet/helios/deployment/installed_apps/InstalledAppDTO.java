package de.tum.cit.aet.helios.deployment.installed_apps;

import io.micrometer.common.lang.NonNull;

public record  InstalledAppDTO(
        @NonNull Long id,
        @NonNull String name,
        @NonNull String version)
    {

    public static InstalledAppDTO toInstalledAppDTO(InstalledApp installedApp) {
        return new InstalledAppDTO(
            installedApp.getId(),
            installedApp.getName(),
            installedApp.getVersion());
    }

}
