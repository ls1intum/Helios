package de.tum.cit.aet.helios.workflow.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Single fired alert event. cleared_at NULL while open. See plan §F. */
@Entity
@Table(name = "queue_alert_event")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class QueueAlertEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "rule_id", nullable = false)
  private Long ruleId;

  @Column(name = "repository_id")
  private Long repositoryId;

  @Column(name = "label_set_hash", length = 64)
  private String labelSetHash;

  @Column(name = "fired_at", nullable = false)
  private OffsetDateTime firedAt;

  @Column(name = "cleared_at")
  private OffsetDateTime clearedAt;

  @Column(name = "measured_value")
  private Integer measuredValue;

  @Column(name = "details", columnDefinition = "text")
  private String details;

  @PrePersist
  void onCreate() {
    if (this.firedAt == null) {
      this.firedAt = OffsetDateTime.now();
    }
  }
}
