package de.tum.cit.aet.helios.deployment.environments;


import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import de.tum.cit.aet.helios.deployment.deployments.Deployment;
import de.tum.cit.aet.helios.deployment.installed_apps.InstalledApp;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;


@Entity
@Table(name = "environments")
@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class EnvironmentInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String description;

    private String url;

    private Long createdByUserId;

    private OffsetDateTime createdAt;

    private Status status;
    
    public enum Status {
        UP, DOWN
    }
    
    @ManyToMany
    @JoinTable(name = "environment_installed_applications", joinColumns = @JoinColumn(name = "environment_id"), inverseJoinColumns = @JoinColumn(name = "id"))
    @ToString.Exclude
    private Set<InstalledApp> installedApps = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "environment_deployments", joinColumns = @JoinColumn(name = "environment_id"), inverseJoinColumns = @JoinColumn(name = "id"))
    @ToString.Exclude
    private Set<Deployment> deployments = new HashSet<>();

    public EnvironmentInfo(EnvironmentInfoDTO environmentInfoDTO) {
        this.id = environmentInfoDTO.id();
        this.name = environmentInfoDTO.name();
        this.description = environmentInfoDTO.description();
        this.url = environmentInfoDTO.url();
        this.createdByUserId = environmentInfoDTO.createdByUserId();
        this.createdAt = environmentInfoDTO.createdAt();
        this.status = environmentInfoDTO.status();
        this.installedApps = environmentInfoDTO.installedApps().stream()
            .map(installedAppDTO -> new InstalledApp())
            .collect(Collectors.toSet());
    }
}
