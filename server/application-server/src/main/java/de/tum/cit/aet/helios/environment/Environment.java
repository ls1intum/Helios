package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.environment.status.EnvironmentStatus;
import de.tum.cit.aet.helios.environment.status.StatusCheckType;
import de.tum.cit.aet.helios.filters.RepositoryFilterEntity;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.workflow.Workflow;
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
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
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

  @Column(name = "status_changed_at", nullable = true)
  private Instant statusChangedAt;

  @OneToMany(mappedBy = "environment", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("checkTimestamp DESC")
  private List<EnvironmentStatus> statusHistory = new ArrayList<>();

  // Once the threshold is reached, the lock automatically expires and the environment becomes
  // available again.
  // This field has presedence over the one in GitRepoSettings.
  @Column(name = "lock_expiration_threshold")
  private Long lockExpirationThreshold;

  // After this period, any user can unlock the environment.
  // This field has presedence over the one in GitRepoSettings.
  @Column(name = "lock_reservation_threshold")
  private Long lockReservationThreshold;

  // The time when the lock will expire calculated via the lockExpirationThreshold above or the one
  // in GitRepoSettings.
  @Column(name = "lock_will_expire_at")
  private OffsetDateTime lockWillExpireAt;

  // The time when the lock reservation will expire calculated via the lockReservationThreshold
  // above or the one in GitRepoSettings.
  @Column(name = "lock_reservation_expires_at")
  private OffsetDateTime lockReservationExpiresAt;

  @Column(name = "deployment_workflow_branch")
  private String deploymentWorkflowBranch;

  public Optional<EnvironmentStatus> getLatestStatus() {
    return statusHistory.stream().findFirst();
  }

  public Optional<Deployment> getLatestDeployment() {
    return this.deployments.reversed().stream().findFirst();
  }

  @Column(name = "type")
  @Enumerated(EnumType.STRING)
  private Type type;

  public enum Type {
    TEST,
    STAGING,
    PRODUCTION
  }

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "deployment_workflow_id")
  private Workflow deploymentWorkflow;

  // Missing properties
  // nodeId --> GraphQl ID
  // ProtectionRule
  // DeploymentBranchPolicy
}
