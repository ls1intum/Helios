package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.deployment.Deployment;
import de.tum.cit.aet.helios.filters.RepositoryFilterEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.OffsetDateTime;
import java.util.List;
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

  @Version
  private Integer version;

  @OneToMany(fetch = FetchType.EAGER, mappedBy = "environment")
  @OrderBy("createdAt ASC")
  private List<Deployment> deployments;

  private boolean locked;

  // user ID
  @Column(name = "locked_by")
  private String lockedBy;

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

  // Missing properties
  // nodeId --> GraphQl ID
  // ProtectionRule
  // DeploymentBranchPolicy
}
