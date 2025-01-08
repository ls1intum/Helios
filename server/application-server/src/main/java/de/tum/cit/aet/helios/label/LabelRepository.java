package de.tum.cit.aet.helios.label;

import java.util.Optional;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {

  Optional<Label> findByName(@NonNull String name);

  Optional<Label> findByRepositoryIdAndName(@Param("repositoryId") Long repositoryId,
                                            @Param("name") String name);

}