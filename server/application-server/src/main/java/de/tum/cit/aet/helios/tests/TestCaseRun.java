package de.tum.cit.aet.helios.tests;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Per-execution record of a test case within a test suite run. Links to the canonical {@link
 * TestCase} definition for identity and to {@link TestSuiteRun} for run context.
 */
@Entity
@Table(name = "test_case_run")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class TestCaseRun {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "test_suite_run_id")
  private TestSuiteRun testSuiteRun;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "test_case_id")
  private TestCase testCase;

  @Column(nullable = false)
  private Double time;

  @Enumerated(EnumType.STRING)
  private TestStatus status;

  @Column(columnDefinition = "TEXT")
  private String message;

  @Column(name = "stack_trace", columnDefinition = "TEXT")
  private String stackTrace;

  @Column(name = "error_type")
  private String errorType;

  @Column(name = "system_out")
  private String systemOut;

  /**
   * Transient field indicating the previous status of this test case. Used for change detection
   * purposes.
   */
  @Transient TestStatus previousStatus;

  /** Transient field indicating whether this test also fails in the default branch. */
  @Transient private boolean failsInDefaultBranch;

  public enum TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
  }

}
