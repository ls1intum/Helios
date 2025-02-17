package de.tum.cit.aet.helios.github.sync;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.NonNull;

@Entity
@Table(name = "data_sync_status")
@Getter
@Setter
@NoArgsConstructor
public class DataSyncStatus {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  protected Long id;

  @NonNull private OffsetDateTime startTime;

  @NonNull private OffsetDateTime endTime;

  /**
   * Stores the repository name directly instead of a relationship.
   * In the first sync, the repository is not present in the database.
   * Also, CASCADE delete is not required for this entity.
   */
  private String repositoryNameWithOwner;

  @Enumerated(EnumType.STRING)
  private Status status;

  public enum Status {
    SUCCESS,
    FAILED,
    IN_PROGRESS
  }
}
