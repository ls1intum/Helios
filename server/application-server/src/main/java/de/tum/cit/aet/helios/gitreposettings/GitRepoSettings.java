package de.tum.cit.aet.helios.gitreposettings;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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
public class GitRepoSettings {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "repository_id", unique = true)
  private GitRepository repository;

  @OneToMany(mappedBy = "gitRepoSettings", cascade = CascadeType.ALL, orphanRemoval = true)
  // Automatically orders groups by orderIndex defined in WorkflowGroup
  @OrderBy("orderIndex ASC")
  private List<WorkflowGroup> groups;
}
