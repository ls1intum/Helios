package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.environment.status.EnvironmentStatus;
import de.tum.cit.aet.helios.environment.status.StatusCheckType;
import de.tum.cit.aet.helios.filters.RepositoryFilterEntity;
import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "environment")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Environment extends RepositoryFilterEntity {
  @Id private Long id;

  @Column(nullable = false)
  private String name;

  private String url;

  @Column(name = "html_url")
  private String htmlUrl;

  @Column(name = "created_at")
  private OffsetDateTime createdAt;

  @Column(name = "updated_at")
  private OffsetDateTime updatedAt;

  @Version private Integer version;

  @OneToMany(fetch = FetchType.EAGER, mappedBy = "environment")
  @OrderBy("createdAt ASC")
  private List<Deployment> deployments;

  /**
   * Whether the environment is enabled or not. It is set to false by default. Needs to be set to
   * true in Helios environment settings page.
   */
  private boolean enabled = false;

  private boolean locked;

  // user ID
  @ManyToOne
  @JoinColumn(name = "author_id")
  @ToString.Exclude
  private User lockedBy;

  @Column(name = "locked_at")
  private OffsetDateTime lockedAt;

  @Column(name = "deployed_at")
  private OffsetDateTime deployedAt;

  @ElementCollection
  @CollectionTable(name = "installed_apps", joinColumns = @JoinColumn(name = "environment_id"))
  @Column(name = "app_name")
  private List<String> installedApps;

  @Column(name = "description")
  private String description;

  @Column(name = "server_url")
  private String serverUrl;

  @OneToMany(mappedBy = "environment", fetch = FetchType.LAZY)
  private List<EnvironmentLockHistory> lockHistory;

  @Column(name = "status_url", nullable = true)
  private String statusUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "status_check_type", length = 20)
  private StatusCheckType statusCheckType;

  @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("checkTimestamp DESC")
  private List<EnvironmentStatus> statusHistory;

  public Optional<EnvironmentStatus> getLatestStatus() {
    return statusHistory.stream().findFirst();
  }

  public Optional<Deployment> getLatestDeployment() {
    return this.deployments.reversed().stream().findFirst();
  }

  @Column(name = "type")
  @NonNull
  @Enumerated(EnumType.STRING)
  private Type type = Type.TEST;

  public enum Type {
    TEST,
    STAGING,
    PRODUCTION
  }

  // Missing properties
  // nodeId --> GraphQl ID
  // ProtectionRule
  // DeploymentBranchPolicy
}
