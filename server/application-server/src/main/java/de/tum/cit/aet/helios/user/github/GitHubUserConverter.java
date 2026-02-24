package de.tum.cit.aet.helios.user.github;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.User.Type;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Locale;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHUser;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubUserConverter extends BaseGitServiceEntityConverter<GHUser, User> {

  @Override
  public User convert(@NonNull GHUser source) {
    return update(source, new User());
  }

  public User convertToAnonymous() {
    User user = new User();
    user.setId(Long.parseLong("-1"));
    user.setName("Anonymous");
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    user.setLogin("anonymous");
    user.setAvatarUrl(
        "https://github.githubassets.com/images/gravatars/gravatar-user-420.png?size=40");
    user.setHtmlUrl("https://helios.aet.cit.tum.de/not-found");
    user.setType(Type.USER);
    return user;
  }

  public User convertToExpirationPicker() {
    User user = new User();
    user.setId(Long.parseLong("-2"));
    user.setName("Auto Release Lock");
    user.setCreatedAt(OffsetDateTime.now());
    user.setUpdatedAt(OffsetDateTime.now());
    user.setLogin("ls1intum/Helios");
    user.setAvatarUrl("https://helios.aet.cit.tum.de/favicon.png");
    user.setHtmlUrl("https://helios.aet.cit.tum.de/not-found");
    user.setType(Type.USER);
    return user;
  }

  @Override
  public User update(@NonNull GHUser source, @NonNull User user) {
    if (isCopilotActorLogin(source.getLogin())) {
      return updateFromCopilotActor(source, user);
    }

    convertBaseFields(source, user);
    user.setLogin(source.getLogin());
    user.setAvatarUrl(source.getAvatarUrl());
    user.setDescription(source.getBio());
    user.setHtmlUrl(source.getHtmlUrl().toString());
    user.setName(
        readUserField(
            source,
            "name",
            () -> source.getName() != null ? source.getName() : source.getLogin(),
            source.getLogin()));
    user.setCompany(readUserField(source, "company", source::getCompany, null));
    user.setBlog(readUserField(source, "blog", source::getBlog, null));
    user.setLocation(readUserField(source, "location", source::getLocation, null));
    user.setEmail(readUserField(source, "email", source::getEmail, null));
    user.setType(readUserField(source, "type", () -> convertUserType(source.getType()), Type.USER));
    user.setFollowers(readUserField(source, "followers", source::getFollowersCount, 0));
    user.setFollowing(readUserField(source, "following", source::getFollowingCount, 0));
    return user;
  }

  private User updateFromCopilotActor(@NonNull GHUser source, @NonNull User user) {
    var now = OffsetDateTime.now();

    user.setId(source.getId());
    user.setCreatedAt(user.getCreatedAt() != null ? user.getCreatedAt() : now);
    user.setUpdatedAt(now);
    user.setLogin(source.getLogin());
    user.setAvatarUrl(source.getAvatarUrl());
    user.setDescription("Copilot bot actor from GitHub metadata.");
    user.setName("GitHub Copilot");
    user.setCompany(null);
    user.setBlog(null);
    user.setLocation(null);
    user.setEmail(null);
    user.setHtmlUrl("https://github.com/apps/copilot");
    user.setType(Type.BOT);
    user.setFollowers(0);
    user.setFollowing(0);

    log.warn(
        "Resolved Copilot actor '{}' without fetching /users endpoint.",
        source.getLogin());
    return user;
  }

  public boolean isCopilotActorLogin(String login) {
    if (login == null) {
      return false;
    }

    return "copilot".equals(login.toLowerCase(Locale.ROOT));
  }

  private <T> T readUserField(
      @NonNull GHUser source, String fieldName, IoFieldReader<T> reader, T fallback) {
    try {
      return reader.read();
    } catch (IOException e) {
      log.error(
          "Failed to convert user {} field for source {}: {}",
          fieldName,
          source.getId(),
          e.getMessage());
      return fallback;
    }
  }

  @FunctionalInterface
  private interface IoFieldReader<T> {
    T read() throws IOException;
  }

  private User.Type convertUserType(String type) {
    switch (type) {
      case "User":
        return User.Type.USER;
      case "Organization":
        return User.Type.ORGANIZATION;
      case "Bot":
        return User.Type.BOT;
      default:
        return User.Type.USER;
    }
  }
}
