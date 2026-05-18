package de.tum.cit.aet.helios.workflow.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** SLO config row. See plan §A, §F. */
@Entity
@Table(name = "queue_alert_rule")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class QueueAlertRule {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 32)
  private Kind kind;

  @Column(name = "threshold_seconds")
  private Integer thresholdSeconds;

  @Column(name = "window_minutes", nullable = false)
  private Integer windowMinutes = 5;

  @Column(name = "repository_id")
  private Long repositoryId;

  @Column(name = "label_set_hash", length = 40)
  private String labelSetHash;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "channels", columnDefinition = "text[]")
  private List<String> channels;

  @Column(name = "enabled", nullable = false)
  private boolean enabled = true;

  /** Cron expression for windows during which evaluation is skipped (e.g. nights). */
  @Column(name = "quiet_hours_cron", length = 64)
  private String quietHoursCron;

  @Column(name = "created_by_user_id")
  private Long createdByUserId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  void onCreate() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = OffsetDateTime.now();
  }

  public enum Kind {
    QUEUE_P95_OVER,
    RUNNER_OFFLINE_OVER,
    STUCK_JOBS_OVER
  }
}
