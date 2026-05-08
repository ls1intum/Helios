package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.user.User;
import jakarta.validation.constraints.NotNull;

public record PullRequestFilterUserOptionDto(
    @NotNull Long id,
    @NotNull String login,
    @NotNull String avatarUrl,
    @NotNull String name) {

  public static PullRequestFilterUserOptionDto fromUser(User user) {
    return new PullRequestFilterUserOptionDto(
        user.getId(), user.getLogin(), user.getAvatarUrl(), user.getName());
  }
}
