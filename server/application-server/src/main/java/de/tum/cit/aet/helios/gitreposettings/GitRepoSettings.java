package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.filters.RepositoryFilterEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "repository_settings")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class GitRepoSettings extends RepositoryFilterEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToMany(mappedBy = "gitRepoSettings", cascade = CascadeType.ALL, orphanRemoval = true)
  // Automatically orders groups by orderIndex defined in WorkflowGroup
  @OrderBy("orderIndex ASC")
  private List<WorkflowGroup> groups;

  // Once the threshold is reached, the lock automatically expires and the environment becomes
  // available again.
  private Long lockExpirationThreshold = 60L; // Default value: 1 minute

  // After this period, any user can unlock the environment.
  private Long lockReservationThreshold = 30L; // Default value: 30 seconds
}
