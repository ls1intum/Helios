package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.label.Label;
import jakarta.validation.constraints.NotNull;

public record PullRequestFilterLabelOptionDto(
    @NotNull Long id,
    @NotNull String name,
    @NotNull String color) {

  public static PullRequestFilterLabelOptionDto fromLabel(Label label) {
    return new PullRequestFilterLabelOptionDto(label.getId(), label.getName(), label.getColor());
  }
}
