package de.tum.cit.aet.helios.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.lang.NonNull;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record UserInfoDto(
    @NonNull Long id,
    @NonNull String login,
    @NonNull String avatarUrl,
    @NonNull String name,
    @NonNull String htmlUrl) {

  public static UserInfoDto fromUser(User user) {
    if (user == null) {
      return null;
    }
    return new UserInfoDto(
        user.getId(), user.getLogin(), user.getAvatarUrl(), user.getName(), user.getHtmlUrl());
  }
}
