package de.tum.cit.aet.helios.environment;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

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

    private String lockedBy;
    private OffsetDateTime lockedAt;
    private OffsetDateTime unlockedAt;
}