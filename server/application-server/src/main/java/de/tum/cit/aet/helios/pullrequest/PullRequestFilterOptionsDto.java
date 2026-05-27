package de.tum.cit.aet.helios.pullrequest;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record PullRequestFilterOptionsDto(
    @NotNull List<PullRequestFilterUserOptionDto> authors,
    @NotNull List<PullRequestFilterUserOptionDto> assignees,
    @NotNull List<PullRequestFilterUserOptionDto> reviewers,
    @NotNull List<PullRequestFilterLabelOptionDto> labels) {}
