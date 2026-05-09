package de.tum.cit.aet.helios.ai.testfailure;

import de.tum.cit.aet.helios.tests.TestCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "test_failure_analysis",
    indexes = {
      @Index(
          name = "idx_test_failure_analysis_requester_created",
          columnList = "requester_user_id,created_at"),
      @Index(
          name = "idx_test_failure_analysis_cache_lookup",
          columnList = "test_case_id,provider_id,status,updated_at,id")
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

  @Column(name = "requester_user_id")
  private String requesterUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TestFailureAnalysisStatus status;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "result_json", columnDefinition = "TEXT")
  private String resultJson;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @Column(name = "duration_ms")
  private Long durationMs;
}
