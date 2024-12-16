package de.tum.cit.aet.helios.environment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import de.tum.cit.aet.helios.gitrepo.GitRepository;

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "environment")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Environment {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    private String url;

    @Column(name = "html_url")
    private String htmlUrl;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @ManyToOne
    @JoinColumn(name = "repository_id", nullable = false)
    private GitRepository repository;

    @ElementCollection
    @CollectionTable(name = "installed_apps", joinColumns = @JoinColumn(name = "environment_id"))
    @Column(name = "app_name")
    private List<String> installedApps;

    @Column(name = "description")
    private String description;

    @Column(name = "server_url")
    private String serverUrl;

    // Missing properties
    // nodeId --> GraphQl ID
    // ProtectionRule
    // DeploymentBranchPolicy
}
