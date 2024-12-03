package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.environment.Environment;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.OffsetDateTime;

@Entity
@Table(name = "deployment")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Deployment {

    @Id
    private Long id;

    @Column(name = "node_id")
    private String nodeId;

    private String url;

    private String sha;

    private String ref;

    private String task;

    // JSON string
    @Lob
    private String payload;

    @Column(name = "original_environment")
    private String originalEnvironment;

    private String environment;

    private String description;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "statuses_url")
    private String statusesUrl;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @Column(name = "transient_environment")
    private Boolean transientEnvironment;

    @Column(name = "production_environment")
    private Boolean productionEnvironment;

    // JSON string
    @Lob
    @Column(name = "performed_via_github_app")
    private String performedViaGitHubApp;

    @ManyToOne
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environmentEntity;
}
