package de.tum.cit.aet.helios.commit.github;

import de.tum.cit.aet.helios.commit.Commit;
import de.tum.cit.aet.helios.common.util.DateUtil;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHCommit;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubCommitConverter implements Converter<GHCommit, Commit> {
  @Override
  public Commit convert(@NonNull GHCommit source) {
    return update(source, new Commit());
  }

  public Commit update(@NonNull GHCommit source, @NonNull Commit commit) {
    try {
      if (source.getSHA1() != null) {
        commit.setSha(source.getSHA1());
      } else {
        throw new IllegalArgumentException("Commit hash cannot be null");
      }
      if (source.getCommitShortInfo().getMessage().length() > 255) {
        commit.setMessage(source.getCommitShortInfo().getMessage().substring(0, 255));
      } else {
        commit.setMessage(source.getCommitShortInfo().getMessage());
      }
      commit.setAuthoredAt(DateUtil.convertToOffsetDateTime(source.getAuthoredDate()));

    } catch (Exception e) {
      log.error("Failed to convert fields for source {}: {}", source.getSHA1(), e.getMessage());
    }
    return commit;
  }
}
