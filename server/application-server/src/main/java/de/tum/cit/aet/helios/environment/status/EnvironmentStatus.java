package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.environment.Environment;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Getter
@Setter
public class EnvironmentStatus {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "environment_id")
  private Environment environment;

  @Column(nullable = false)
  private boolean success;

  // null for HTTP_STATUS / ARTEMIS_INFO
  @Enumerated(EnumType.STRING)
  private LifecycleState state;

  @Column(name = "http_status_code", nullable = false)
  private Integer httpStatusCode;

  @Enumerated(EnumType.STRING)
  @Column(name = "check_type", length = 20, nullable = false)
  private StatusCheckType checkType;

  @Column(name = "check_timestamp", nullable = false)
  private Instant checkTimestamp;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> metadata = new HashMap<>();
}
