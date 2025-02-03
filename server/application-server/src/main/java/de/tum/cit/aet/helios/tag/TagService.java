package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.auth.AuthService;
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
import de.tum.cit.aet.helios.tag.TagDetailsDto.TagEvaluationDto;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.user.UserInfoDto;
import de.tum.cit.aet.helios.user.UserRepository;
import de.tum.cit.aet.helios.user.github.GitHubUserSyncService;
import jakarta.transaction.Transactional;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHCompare;
import org.kohsuke.github.GHRepository;
import org.springframework.stereotype.Service;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor
public class TagService {
  private final TagRepository tagRepository;
  private final CommitRepository commitRepository;
  private final DeploymentRepository deploymentRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubService gitHubService;
  private final BranchRepository branchRepository;
  private final UserRepository userRepository;
  private final GitHubUserSyncService userSyncService;
  private final TagEvaluationRepository tagEvaluationRepository;
  private final AuthService authService;

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
                  deploymentRepository
                      .findByRepositoryRepositoryIdAndSha(repositoryId, tag.getCommit().getSha())
                      .stream()
                      .map(DeploymentDto::fromDeployment)
                      .toList();
              return new TagDetailsDto(
                  tag.getName(),
                  CommitInfoDto.fromCommit(tag.getCommit()),
                  BranchInfoDto.fromBranch(tag.getBranch()),
                  deployments,
                  tag.getEvaluations().stream().map(TagEvaluationDto::fromEvaluation).toList(),
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
    final String login = authService.getPreferredUsername();

    if (tagRepository.existsByRepositoryRepositoryIdAndName(repositoryId, tag.name()) == true) {
      throw new TagException("Tag already exists");
    }

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
            .findByLoginIgnoreCase(login)
            .orElseGet(() -> userSyncService.syncUser(login)));
    newTag.setCreatedAt(OffsetDateTime.now());
    return TagInfoDto.fromTag(tagRepository.save(newTag));
  }

  public void evaluateTag(String name, boolean isWorking) {
    final Long repositoryId = RepositoryContext.getRepositoryId();

    final Tag tag = tagRepository
        .findByRepositoryRepositoryIdAndName(repositoryId, name)
        .orElseThrow(() -> new TagException("Tag not found"));

    final User user =  authService.getUserFromGithubId();
    
    if (user == null) {
      throw new TagException("User not found");
    }

    final TagEvaluation evaluation = tagEvaluationRepository.findByTagAndEvaluatedById(
        tag,
        user.getId()
    ).orElseGet(() -> {
      TagEvaluation newEvaluation = new TagEvaluation();
      newEvaluation.setTag(tag);
      newEvaluation.setEvaluatedBy(user);
      return newEvaluation;
    });

    evaluation.setWorking(isWorking);
    tagEvaluationRepository.save(evaluation);
  }
}
