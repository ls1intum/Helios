package de.tum.cit.aet.helios.workflow.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Hourly pre-aggregated queue/run percentiles. See plan §A. */
@Entity
@Table(name = "queue_wait_stat")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class QueueWaitStat {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "repository_id", nullable = false)
  private Long repositoryId;

  @Column(name = "workflow_name", length = 512, nullable = false)
  private String workflowName = "";

  @Column(name = "job_name", length = 512, nullable = false)
  private String jobName = "";

  @Column(name = "head_branch", length = 512, nullable = false)
  private String headBranch = "";

  @Column(name = "label_set_hash", length = 64, nullable = false)
  private String labelSetHash = "";

  @Column(name = "bucket_start", nullable = false)
  private OffsetDateTime bucketStart;

  @Column(name = "samples", nullable = false)
  private Integer samples;

  @Column(name = "queue_p50")
  private Integer queueP50;

  @Column(name = "queue_p90")
  private Integer queueP90;

  @Column(name = "queue_p95")
  private Integer queueP95;

  @Column(name = "run_p50")
  private Integer runP50;

  @Column(name = "run_p90")
  private Integer runP90;

  @Column(name = "run_p95")
  private Integer runP95;
}
