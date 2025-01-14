package de.tum.cit.aet.helios.label;

import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.NonNull;

public record LabelInfoDto(
    @Schema(description = "The unique identifier of the label")
    @NonNull Long id,

    @Schema(description = "The name of the label", example = "bug")
    @NonNull String name,

    @Schema(description = "The color of the label as a 6-character hex code (without #)",
        example = "ff0000")
    @NonNull String color,

    @Schema(description = "The repository associated with this label")
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