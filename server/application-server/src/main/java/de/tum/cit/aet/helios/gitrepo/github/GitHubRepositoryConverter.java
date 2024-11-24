package de.tum.cit.aet.helios.gitrepo.github;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHRepository.Visibility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.helios.github.BaseGitServiceEntityConverter;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.util.DateUtil;

@Component
public class GitHubRepositoryConverter extends BaseGitServiceEntityConverter<GHRepository, GitRepository> {

    private static final Logger logger = LoggerFactory.getLogger(GitHubRepositoryConverter.class);

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
                logger.error("Unknown repository visibility: {}", visibility);
                return GitRepository.Visibility.UNKNOWN;
        }
    }
}
