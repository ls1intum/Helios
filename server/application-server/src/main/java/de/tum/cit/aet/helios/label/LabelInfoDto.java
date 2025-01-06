package de.tum.cit.aet.helios.label;

import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import org.springframework.lang.NonNull;

public record LabelInfoDto(
    @NonNull Long id,
    @NonNull String name,
    @NonNull String color,
    RepositoryInfoDto repository) {
  public static LabelInfoDto fromLabel(Label label) {
    return new LabelInfoDto(
        label.getId(),
        label.getName(),
        label.getColor(),
        RepositoryInfoDto.fromRepository(label.getRepository())
    );
  }
}