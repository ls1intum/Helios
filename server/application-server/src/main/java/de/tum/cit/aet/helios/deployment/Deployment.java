package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "deployment")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Deployment extends BaseGitServiceEntity {

    @Column(name = "node_id")
    private String nodeId;

    private String url;

    private String sha;

    private String ref;

    private String task;

    // JSON string
    @Lob
    private String payload;

    private String environment;

    @Column(name = "statuses_url")
    private String statusesUrl;

    @Column(name = "repository_url")
    private String repositoryUrl;

    @ManyToOne
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environmentEntity;
}
