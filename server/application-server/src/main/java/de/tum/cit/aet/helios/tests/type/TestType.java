package de.tum.cit.aet.helios.tests.type;

import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.tests.TestSuite;
import de.tum.cit.aet.helios.workflow.Workflow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TestType {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @NotNull private String name;

  @NotNull private String artifactName;

  @ManyToOne
  @JoinColumn(name = "workflow_id")
  private Workflow workflow;

  @ManyToOne
  @JoinColumn(name = "repository_id")
  private GitRepository repository;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @OneToMany(mappedBy = "testType")
  private Set<TestSuite> testSuites = new HashSet<>();

  @PrePersist
  protected void onCreate() {
    createdAt = Instant.now();
    updatedAt = Instant.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = Instant.now();
  }
}
