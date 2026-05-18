package de.tum.cit.aet.helios.workflow.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Self-hosted runner inventory row. See plan §A. */
@Entity
@Table(name = "runner")
@Getter
@Setter
@NoArgsConstructor
@ToString
public class Runner {

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "name")
  private String name;

  @Column(name = "os", length = 32)
  private String os;

  @Column(name = "runner_group_id")
  private Long runnerGroupId;

  @Column(name = "runner_group_name")
  private String runnerGroupName;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private Status status = Status.OFFLINE;

  @Column(name = "busy", nullable = false)
  private boolean busy;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "labels", columnDefinition = "text[]")
  private List<String> labels;

  @Column(name = "current_job_id")
  private Long currentJobId;

  @Column(name = "last_seen_at")
  private OffsetDateTime lastSeenAt;

  @Column(name = "first_registered_at")
  private OffsetDateTime firstRegisteredAt;

  @Column(name = "offline_since")
  private OffsetDateTime offlineSince;

  public enum Status {
    ONLINE,
    OFFLINE
  }
}
