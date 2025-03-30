package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.tests.type.TestType;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TestSuite {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "workflow_run_id")
  private WorkflowRun workflowRun;

  @Min(0)
  @Column(nullable = false)
  private String name;

  @Min(0)
  @Column(nullable = false)
  private LocalDateTime timestamp;

  @Min(0)
  @Column(nullable = false)
  private int tests;

  @Min(0)
  @Column(nullable = false)
  private int failures;

  @Min(0)
  @Column(nullable = false)
  private int errors;

  @Min(0)
  @Column(nullable = false)
  private int skipped;

  @Column(nullable = false)
  private Double time;

  @OneToMany(mappedBy = "testSuite", cascade = CascadeType.ALL)
  private List<TestCase> testCases = new ArrayList<>();

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "test_type_id")
  private TestType testType;

  @Column(name = "system_out")
  private String systemOut;
}
