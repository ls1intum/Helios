package de.tum.cit.aet.helios.releaseinfo.release.github;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.releaseinfo.release.Release;
import de.tum.cit.aet.helios.util.DateUtil;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRelease;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubReleaseConverter extends BaseGitServiceEntityConverter<GHRelease, Release> {

  @Override
  public Release convert(@NonNull GHRelease source) {
    return update(source, new Release());
  }

  @Override
  public Release update(@NonNull GHRelease source, @NonNull Release release) {
    release.setId(source.getId());
    release.setName(source.getName());
    release.setTagName(source.getTagName());
    release.setBody(source.getBody());
    release.setDraft(source.isDraft());
    release.setPrerelease(source.isPrerelease());
    try {
      release.setCreatedAt(DateUtil.convertToOffsetDateTime(source.getCreatedAt()));
      try {
        release.setPublishedAt(DateUtil.convertToOffsetDateTime(source.getPublished_at()));
      } catch (NullPointerException e) {
        log.warn("Skipping setting the published_at date for release {}", source.getName());
      }
    } catch (IOException e) {
      log.error("Failed to convert release created_at date", e);
    }
    release.setGithubUrl(source.getHtmlUrl().toString());
    return release;
  }
}
