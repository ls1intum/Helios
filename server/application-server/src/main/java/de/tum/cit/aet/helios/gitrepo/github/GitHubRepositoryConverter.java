package de.tum.cit.aet.helios.gitrepo.github;

import de.tum.cit.aet.helios.common.util.DateUtil;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import java.io.IOException;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubRepositoryConverter implements Converter<GHRepository, GitRepository> {
  @Override
  public GitRepository convert(@NonNull GHRepository source) {
    return update(source, new GitRepository());
  }

  public GitRepository update(@NonNull GHRepository source, @NonNull GitRepository repository) {
    repository.setRepositoryId(source.getId());
    try {
      repository.setCreatedAt(DateUtil.convertToOffsetDateTime(source.getCreatedAt()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    try {
      repository.setUpdatedAt(DateUtil.convertToOffsetDateTime(source.getUpdatedAt()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    repository.setName(source.getName());
    repository.setNameWithOwner(source.getFullName());
    repository.setPrivate(source.isPrivate());
    repository.setHtmlUrl(source.getHtmlUrl().toString());
    repository.setDescription(source.getDescription());
    repository.setHomepage(source.getHomepage());
    repository.setPushedAt(DateUtil.convertToOffsetDateTime(source.getPushedAt()));
    repository.setArchived(source.isArchived());
    repository.setDisabled(source.isDisabled());
    repository.setVisibility(convertVisibility(source.getVisibility()));
    repository.setStargazersCount(source.getStargazersCount());
    repository.setWatchersCount(source.getWatchersCount());
    repository.setDefaultBranch(source.getDefaultBranch());
    repository.setHasIssues(source.hasIssues());
    repository.setHasProjects(source.hasProjects());
    repository.setHasWiki(source.hasWiki());
    return repository;
  }

  private GitRepository.Visibility convertVisibility(Visibility visibility) {
    switch (visibility) {
      case PRIVATE:
        return GitRepository.Visibility.PRIVATE;
      case PUBLIC:
        return GitRepository.Visibility.PUBLIC;
      case INTERNAL:
        return GitRepository.Visibility.INTERNAL;
      default:
        log.error("Unknown repository visibility: {}", visibility);
        return GitRepository.Visibility.UNKNOWN;
    }
  }
}
