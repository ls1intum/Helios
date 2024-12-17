package de.tum.cit.aet.helios.environment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.deployment.Deployment;
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

    @Version
    private Integer version;

    @OneToMany(fetch = FetchType.EAGER, mappedBy = "environment")
    private List<Deployment> deployments;

    private boolean deploying;

    @ManyToOne
    @JoinColumns({
            @JoinColumn(name = "locking_repository_id", referencedColumnName = "repository_id"),
            @JoinColumn(name = "locking_branch_name", referencedColumnName = "name")
    })
    private Branch lockingBranch;

    public boolean isLocked() {
        return lockingBranch != null;
    }
}
