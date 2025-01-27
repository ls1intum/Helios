package de.tum.cit.aet.helios.environment;

import de.tum.cit.aet.helios.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Entity
@Getter
@Setter
public class EnvironmentLockHistory {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne
  @JoinColumn(name = "environment_id")
  private Environment environment;
  
  @ManyToOne
  @JoinColumn(name = "author_id")
  @ToString.Exclude
  private User lockedBy;
  private OffsetDateTime lockedAt;
  private OffsetDateTime unlockedAt;
}