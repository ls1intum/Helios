package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.branch.Branch;
import de.tum.cit.aet.helios.branch.BranchInfoDto;
import de.tum.cit.aet.helios.branch.BranchRepository;
import de.tum.cit.aet.helios.commit.CommitInfoDto;
import de.tum.cit.aet.helios.commit.CommitRepository;
import de.tum.cit.aet.helios.deployment.DeploymentDto;
import de.tum.cit.aet.helios.deployment.DeploymentRepository;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.user.UserInfoDto;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Log4j2
public class TagService {
  private final TagRepository tagRepository;
  private final CommitRepository commitRepository;
  private final DeploymentRepository deploymentRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubService gitHubService;
  private final BranchRepository branchRepository;
  private final UserRepository userRepository;
  private final GitHubUserSyncService userSyncService;

  public TagService(
      TagRepository tagRepository,
      CommitRepository commitRepository,
      DeploymentRepository deploymentRepository,
      GitHubService gitHubService,
      GitRepoRepository gitRepoRepository,
      BranchRepository branchRepository,
      UserRepository userRepository,
      GitHubUserSyncService userSyncService) {
    this.tagRepository = tagRepository;
    this.commitRepository = commitRepository;
    this.deploymentRepository = deploymentRepository;
    this.gitHubService = gitHubService;
    this.gitRepoRepository = gitRepoRepository;
    this.branchRepository = branchRepository;
    this.userRepository = userRepository;
    this.userSyncService = userSyncService;
  }

  public List<TagInfoDto> getAllTags() {
    return tagRepository.findAllByOrderByNameAsc().stream().map(TagInfoDto::fromTag).toList();
  }

  public TagDetailsDto getTagByName(String name) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    return tagRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            tag -> {
              final List<DeploymentDto> deployments =
                  deploymentRepository.findBySha(tag.getCommit().getSha()).stream()
                      .map(DeploymentDto::fromDeployment)
                      .toList();
              return new TagDetailsDto(
                  tag.getName(),
                  CommitInfoDto.fromCommit(tag.getCommit()),
                  BranchInfoDto.fromBranch(tag.getBranch()),
                  deployments,
                  tag.getMarkedWorkingBy().stream().map(UserInfoDto::fromUser).toList(),
                  tag.getMarkedBrokenBy().stream().map(UserInfoDto::fromUser).toList(),
                  UserInfoDto.fromUser(tag.getCreatedBy()),
                  tag.getCreatedAt());
            })
        .orElseThrow(() -> new TagException("Tag not found"));
  }

  public CommitsSinceTagDto getCommitsFromBranchSinceLastTag(String branchName) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    try {
      final GHRepository repository =
          gitHubService.getRepository(
              gitRepoRepository
                  .findById(repositoryId)
                  .map(GitRepository::getNameWithOwner)
                  .orElseThrow(() -> new TagException("Repository not found")));
      final Branch branch =
          branchRepository
              .findByRepositoryRepositoryIdAndName(repositoryId, branchName)
              .orElseThrow(() -> new TagException("Branch not found"));

      final Tag lastTag =
          tagRepository.findAll().stream()
              .sorted(Tag::compareToByDate)
              .findFirst()
              .orElseGet(() -> null);

      if (lastTag == null) {
        return new CommitsSinceTagDto(-1, new ArrayList<>());
      }

      final GHCompare compare =
          repository.getCompare(lastTag.getCommit().getSha(), branch.getCommitSha());

      return new CommitsSinceTagDto(compare.getTotalCommits(), new ArrayList<>());
      // Add this snippet later when showing commit info
      // Arrays.stream(compare.getCommits())
      //     .map(commitConverter::convert)
      //     .map(CommitInfoDto::fromCommit)
      //     .toList()));
    } catch (IOException e) {
      log.error("Failed to compare commits for branch {}: {}", branchName, e.getMessage());
      throw new TagException("Failed to fetch compare commit data from GitHub");
    }
  }

  public TagInfoDto createTag(TagCreateDto tag) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    Tag newTag = new Tag();
    newTag.setName(tag.name());
    newTag.setCommit(
        commitRepository
            .findByShaAndRepositoryRepositoryId(tag.commitSha(), repositoryId)
            .orElseThrow(() -> new TagException("Commit not found")));
    newTag.setBranch(
        branchRepository
            .findByNameAndRepositoryRepositoryId(tag.branchName(), repositoryId)
            .orElseThrow(() -> new TagException("Branch not found")));
    newTag.setRepository(
        gitRepoRepository
            .findById(repositoryId)
            .orElseThrow(() -> new TagException("Repository not found")));
    newTag.setCreatedBy(
        userRepository
            .findByLogin(tag.createdByLogin())
            .orElseGet(() -> userSyncService.syncUser(tag.createdByLogin())));
    newTag.setCreatedAt(OffsetDateTime.now());
    return TagInfoDto.fromTag(tagRepository.save(newTag));
  }

  public Optional<TagInfoDto> editTag(String name, TagInfoDto tagInfoDto) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    return tagRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            tag -> {
              tagRepository.save(tag);
              return TagInfoDto.fromTag(tag);
            });
  }

  public void markTagWorking(String name, String login) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    tagRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            tag -> {
              tag.getMarkedBrokenBy().removeIf(user -> user.getLogin().equals(login));
              tag.getMarkedWorkingBy()
                  .add(
                      userRepository
                          .findByLogin(login)
                          .orElseGet(() -> userSyncService.syncUser(login)));
              return TagInfoDto.fromTag(tagRepository.save(tag));
            });
  }

  public void markTagBroken(String name, String login) {
    final Long repositoryId = RepositoryContext.getRepositoryId();
    tagRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .map(
            tag -> {
              tag.getMarkedWorkingBy().removeIf(user -> user.getLogin().equals(login));
              tag.getMarkedBrokenBy()
                  .add(
                      userRepository
                          .findByLogin(login)
                          .orElseGet(() -> userSyncService.syncUser(login)));
              return TagInfoDto.fromTag(tagRepository.save(tag));
            });
  }
}
