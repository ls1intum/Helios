package de.tum.cit.aet.helios.tests;

import de.tum.cit.aet.helios.workflow.WorkflowRun;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TestResult {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private WorkflowRun workflowRun;

  @Min(0)
  @Column(nullable = false)
  private int total;

  @Min(0)
  @Column(nullable = false)
  private int passed;

  @Min(0)
  @Column(nullable = false)
  private int failures;

  @Min(0)
  @Column(nullable = false)
  private int errors;

  @Min(0)
  @Column(nullable = false)
  private int skipped;
}
