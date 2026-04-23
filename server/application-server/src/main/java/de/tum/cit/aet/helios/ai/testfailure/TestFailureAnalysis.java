package de.tum.cit.aet.helios.ai.testfailure;

import de.tum.cit.aet.helios.tests.TestCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "test_failure_analysis",
    indexes = {
      @Index(name = "idx_test_failure_analysis_analyzed_at", columnList = "analyzed_at")
    },
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_test_failure_analysis_cache_key",
          columnNames = {"test_case_id", "provider_id"})
    })
@Getter
@Setter
@NoArgsConstructor
public class TestFailureAnalysis {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "test_case_id", nullable = false)
  private TestCase testCase;

  @Column(name = "provider_id", nullable = false)
  private String providerId;

  @Column(name = "result_json", columnDefinition = "TEXT", nullable = false)
  private String resultJson;

  @Column(name = "analyzed_at", nullable = false)
  private OffsetDateTime analyzedAt;

  @Column(name = "duration_ms", nullable = false)
  private Long durationMs;
}
