package de.tum.cit.aet.helios.deployment;

import de.tum.cit.aet.helios.environment.Environment;
import de.tum.cit.aet.helios.github.BaseGitServiceEntity;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private GitRepository repository;

    @ManyToOne
    @JoinColumn(name = "environment_id", nullable = false)
    private Environment environmentEntity;

    @Column(name = "node_id")
    private String nodeId;

    private String url;

    @Column(name = "statuses_url")
    private String statusesUrl;

    private String sha;

    private String ref;

    private String task;

    private String environment;

    @Column(name = "repository_url")
    private String repositoryUrl;

    // payload field is just empty JSON object
}
