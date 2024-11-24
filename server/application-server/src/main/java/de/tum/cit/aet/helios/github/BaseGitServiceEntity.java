package de.tum.cit.aet.helios.github;

import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.*;

import java.time.OffsetDateTime;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public abstract class BaseGitServiceEntity {
    @Id
    protected Long id;

    protected OffsetDateTime createdAt;

    protected OffsetDateTime updatedAt;
}
