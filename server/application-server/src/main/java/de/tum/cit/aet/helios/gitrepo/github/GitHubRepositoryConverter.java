package de.tum.cit.aet.helios.gitrepo.github;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.util.DateUtil;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubRepositoryConverter
    extends BaseGitServiceEntityConverter<GHRepository, GitRepository> {
  @Override
  public GitRepository convert(@NonNull GHRepository source) {
    return update(source, new GitRepository());
  }

  @Override
  public GitRepository update(@NonNull GHRepository source, @NonNull GitRepository repository) {
    convertBaseFields(source, repository);
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
