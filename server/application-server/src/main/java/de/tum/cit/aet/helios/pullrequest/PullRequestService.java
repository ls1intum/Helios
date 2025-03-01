package de.tum.cit.aet.helios.pullrequest;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.issue.Issue;
import de.tum.cit.aet.helios.pagination.PageResponse;
import de.tum.cit.aet.helios.pullrequest.pagination.PullRequestPageRequest;
import de.tum.cit.aet.helios.user.User;
import de.tum.cit.aet.helios.userpreference.UserPreference;
import de.tum.cit.aet.helios.userpreference.UserPreferenceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class PullRequestService {

  private final PullRequestRepository pullRequestRepository;
  private final UserPreferenceRepository userPreferenceRepository;
  private final AuthService authService;

  public List<PullRequestBaseInfoDto> getAllPullRequests() {
    final Optional<UserPreference> userPreference =
        authService.isLoggedIn()
            ? userPreferenceRepository.findByUser(authService.getUserFromGithubId())
            : Optional.empty();
    return pullRequestRepository.findAllByOrderByUpdatedAtDesc().stream()
        .map((pr) -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, userPreference))
        .sorted(
            (pr1, pr2) -> {
              if (pr1.isPinned() && !pr2.isPinned()) {
                return -1;
              } else if (!pr1.isPinned() && pr2.isPinned()) {
                return 1;
              } else {
                return 0;
              }
            })
        .collect(Collectors.toList());
  }

  public PageResponse<PullRequestBaseInfoDto> getPaginatedPullRequests(
      PullRequestPageRequest pageRequest) {
    log.debug("Starting pagination process");
    log.debug("Input parameters - Filter: {}, Page: {}, Size: {}",
        pageRequest.getFilterType(), pageRequest.getPage(), pageRequest.getSize());

    final String currentUserId = authService.isLoggedIn()
        ? authService.getGithubId()
        : null;
    log.debug("Current User ID: {}", currentUserId);

    final Optional<UserPreference> userPreference =
        authService.isLoggedIn()
            ? userPreferenceRepository.findByUser(authService.getUserFromGithubId())
            : Optional.empty();
    log.debug("User Preference Present: {}", userPreference.isPresent());

    // Fetch pinned pull requests
    Specification<PullRequest> pinnedSpec = buildSpecification(pageRequest, currentUserId, true);
    List<PullRequest> pinnedPullRequests = pullRequestRepository.findAll(pinnedSpec);
    log.debug("Total Pinned PRs Found: {}", pinnedPullRequests.size());

    // Convert pinned PRs to DTOs
    List<PullRequestBaseInfoDto> pinnedDtos = pinnedPullRequests.stream()
        .map(pr -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, userPreference))
        .collect(Collectors.toList());

    if (log.isDebugEnabled()) {
      log.debug("Pinned PR IDs: {}",
          pinnedDtos.stream().map(PullRequestBaseInfoDto::id).collect(Collectors.toList()));
    }

    // Fetch non-pinned pull requests specification
    Specification<PullRequest> nonPinnedSpec = buildSpecification(pageRequest, currentUserId, false);

    // Calculate total elements
    long totalPinnedCount = pinnedDtos.size();
    long totalNonPinnedCount = pullRequestRepository.count(nonPinnedSpec);
    long totalElements = totalPinnedCount + totalNonPinnedCount;
    log.debug("Total Elements Breakdown - Pinned: {}, Non-Pinned: {}, Total: {}",
        totalPinnedCount, totalNonPinnedCount, totalElements);

    // Calculate pagination details
    int pageSize = pageRequest.getSize();
    int totalPages = (int) Math.ceil((double) totalElements / pageSize);
    log.debug("Pagination Details - Page Size: {}, Total Pages: {}", pageSize, totalPages);

    // Prepare the final list of PRs for the current page
    List<PullRequestBaseInfoDto> pageContent = new ArrayList<>();

    // Determine the start and end indices for the current page
    int startIndex = pageRequest.getPage() * pageSize;
    int endIndex = Math.min(startIndex + pageSize, (int) totalElements);
    log.debug("Page Indexing - Start Index: {}, End Index: {}", startIndex, endIndex);

    // Calculate pinned items to add
    int pinnedItemsToAdd = Math.min(
        Math.max(0, pageSize - Math.max(0, startIndex - (int) totalPinnedCount)),
        pinnedDtos.size()
    );
    int pinnedStartIndex = Math.max(0, startIndex - (int) totalPinnedCount);
    log.debug("Pinned Items - To Add: {}, Start Index: {}", pinnedItemsToAdd, pinnedStartIndex);

    // Add pinned PRs that fall within this page
    List<PullRequestBaseInfoDto> pinnedItemsForPage = pinnedDtos.subList(
        pinnedStartIndex,
        Math.min(pinnedStartIndex + pinnedItemsToAdd, pinnedDtos.size())
    );
    pageContent.addAll(pinnedItemsForPage);
    log.debug("Added Pinned Items - Count: {}", pinnedItemsForPage.size());

    if (log.isDebugEnabled()) {
      log.debug("Pinned Items Added - IDs: {}",
          pinnedItemsForPage.stream().map(PullRequestBaseInfoDto::id).collect(Collectors.toList()));
    }

    // If we need more items, fetch non-pinned PRs
    int remainingItemsNeeded = pageSize - pageContent.size();
    log.debug("Remaining Items Needed: {}", remainingItemsNeeded);

    if (remainingItemsNeeded > 0) {
      // Calculate the page and offset for non-pinned PRs
      int nonPinnedPageNumber = Math.max(0,
          (startIndex >= totalPinnedCount)
              ? (startIndex - (int) totalPinnedCount) / pageSize
              : 0
      );
      log.debug("Non-Pinned Page Number: {}", nonPinnedPageNumber);

      // Calculate offset for non-pinned PRs
      int nonPinnedOffset = Math.max(0,
          (startIndex >= totalPinnedCount)
              ? startIndex - (int) totalPinnedCount
              : 0
      );
      log.debug("Non-Pinned Offset: {}", nonPinnedOffset);

      // Create pageable for non-pinned PRs
      Pageable pageable = PageRequest.of(
          nonPinnedPageNumber,
          remainingItemsNeeded,
          Sort.by(Sort.Direction.DESC, "updatedAt")
      );
      log.debug("Non-Pinned Pageable - Page: {}, Size: {}, Sort: updatedAt DESC",
          nonPinnedPageNumber, remainingItemsNeeded);

      // Fetch non-pinned PRs
      Page<PullRequest> nonPinnedPage = pullRequestRepository.findAll(nonPinnedSpec, pageable);
      log.debug("Non-Pinned Page - Total Elements: {}, Content Size: {}",
          nonPinnedPage.getTotalElements(), nonPinnedPage.getContent().size());

      List<PullRequestBaseInfoDto> nonPinnedDtos = nonPinnedPage.getContent().stream()
          .map(pr -> PullRequestBaseInfoDto.fromPullRequestAndUserPreference(pr, userPreference))
          .collect(Collectors.toList());

      // Add non-pinned PRs to page content
      if (!nonPinnedDtos.isEmpty()) {
        // Determine the slice of non-pinned PRs to add
        int fromIndex = 0;
        int toIndex = Math.min(remainingItemsNeeded, nonPinnedDtos.size());

        List<PullRequestBaseInfoDto> slicedNonPinnedDtos = nonPinnedDtos.subList(fromIndex, toIndex);
        pageContent.addAll(slicedNonPinnedDtos);

        log.debug("Added Non-Pinned Items - Count: {}", slicedNonPinnedDtos.size());
        if (log.isDebugEnabled()) {
          log.debug("Non-Pinned Items Added - IDs: {}",
              slicedNonPinnedDtos.stream().map(PullRequestBaseInfoDto::id).collect(Collectors.toList()));
        }
      }
    }

    // Create and return the page response
    PageResponse<PullRequestBaseInfoDto> response = new PageResponse<>();
    response.setContent(pageContent);
    response.setPage(pageRequest.getPage());
    response.setSize(pageSize);
    response.setTotalElements(totalElements);
    response.setTotalPages(totalPages);

    log.debug("Final Page Response Details:");
    log.debug("- Content Size: {}", pageContent.size());
    log.debug("- Current Page: {}", response.getPage());
    log.debug("- Page Size: {}", response.getSize());
    log.debug("- Total Elements: {}", response.getTotalElements());
    log.debug("- Total Pages: {}", response.getTotalPages());

    if (log.isDebugEnabled()) {
      log.debug("Final Page Content IDs: {}",
          pageContent.stream().map(PullRequestBaseInfoDto::id).collect(Collectors.toList()));
    }

    return response;
  }

  private Specification<PullRequest> buildSpecification(PullRequestPageRequest pageRequest,
                                                        String currentUserId,
                                                        boolean isPinned) {
    return (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();

      // Add predicate for pinned status
      if (currentUserId != null) {
        // Create a subquery to find pinned PRs
        Subquery<Long> pinnedSubquery = query.subquery(Long.class);
        Root<UserPreference> userPrefRoot = pinnedSubquery.from(UserPreference.class);
        Join<UserPreference, User> userJoin = userPrefRoot.join("user");
        Join<UserPreference, PullRequest> prJoin = userPrefRoot.join("favouritePullRequests");

        pinnedSubquery.select(prJoin.get("id"))
            .where(cb.equal(userJoin.get("id"), Long.valueOf(currentUserId)));

        if (isPinned) {
          // For pinned PRs, require that they ARE in the favourites
          predicates.add(root.get("id").in(pinnedSubquery));
        } else {
          // For non-pinned, require that they are NOT in the favourites
          predicates.add(cb.not(root.get("id").in(pinnedSubquery)));
        }
      } else if (isPinned) {
        // If no user is logged in, there can't be any pinned PRs
        // Add an impossible condition to ensure empty result
        predicates.add(cb.equal(cb.literal(1), 0));
      }

      // Add search term predicate if provided
      if (pageRequest.getSearchTerm() != null && !pageRequest.getSearchTerm().trim().isEmpty()) {
        String searchTerm = "%" + pageRequest.getSearchTerm().toLowerCase() + "%";
        predicates.add(cb.or(
            cb.like(cb.lower(root.get("title")), searchTerm),
            cb.like(cb.toString(root.get("number")), searchTerm)
        ));
      }

      // Add filter type predicate
      switch (pageRequest.getFilterType()) {
        case OPEN:
          predicates.add(cb.equal(root.get("state"), Issue.State.OPEN));
          log.debug("Added OPEN filter");
          break;
        case OPEN_READY_FOR_REVIEW:
          predicates.add(cb.and(
              cb.equal(root.get("state"), Issue.State.OPEN),
              cb.equal(root.get("isDraft"), false)
          ));
          log.debug("Added OPEN_READY_FOR_REVIEW filter");
          break;
        case DRAFT:
          predicates.add(cb.and(
              cb.equal(root.get("state"), Issue.State.OPEN),
              cb.equal(root.get("isDraft"), true)
          ));
          log.debug("Added DRAFT filter");
          break;
        case MERGED:
          predicates.add(cb.and(
              cb.equal(root.get("state"), Issue.State.CLOSED),
              cb.equal(root.get("isMerged"), true)
          ));
          log.debug("Added MERGED filter");
          break;
        case CLOSED:
          predicates.add(cb.equal(root.get("state"), Issue.State.CLOSED));
          log.debug("Added CLOSED filter");
          break;
        case USER_AUTHORED:
          if (currentUserId != null) {
            Join<PullRequest, User> authorJoin = root.join("author", JoinType.INNER);
            predicates.add(cb.equal(authorJoin.get("id"), Long.valueOf(currentUserId)));
            log.debug("Added USER_AUTHORED filter with user ID: {}", currentUserId);
          }
          break;
        case ASSIGNED_TO_USER:
          if (currentUserId != null) {
            Join<PullRequest, User> assigneeJoin = root.join("assignees", JoinType.INNER);
            predicates.add(cb.equal(assigneeJoin.get("id"), Long.valueOf(currentUserId)));
            log.debug("Added ASSIGNED_TO_USER filter with user ID: {}", currentUserId);
          }
          break;
        case REVIEW_REQUESTED:
          if (currentUserId != null) {
            Join<PullRequest, User> reviewerJoin = root.join("requestedReviewers", JoinType.INNER);
            predicates.add(cb.equal(reviewerJoin.get("id"), Long.valueOf(currentUserId)));
            log.debug("Added REVIEW_REQUESTED filter with user ID: {}", currentUserId);
          }
          break;
        case ALL:
        default:
          // No additional predicates needed
          log.debug("Using ALL filter (no restrictions)");
          break;
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };
  }

  @SuppressWarnings("checkstyle:AbbreviationAsWordInName")
  private long countNonPinnedMatchingPRs(PullRequestPageRequest pageRequest,
                                         String currentUserId) {
    Specification<PullRequest> spec = buildSpecification(pageRequest, currentUserId, false);
    return pullRequestRepository.count(spec);
  }

  public Optional<PullRequestInfoDto> getPullRequestById(Long id) {
    return pullRequestRepository.findById(id).map(PullRequestInfoDto::fromPullRequest);
  }

  public List<PullRequestInfoDto> getPullRequestByRepositoryId(Long repositoryId) {
    return pullRequestRepository
        .findByRepositoryRepositoryIdOrderByUpdatedAtDesc(repositoryId)
        .stream()
        .map(PullRequestInfoDto::fromPullRequest)
        .collect(Collectors.toList());
  }

  public void setPrPinnedByNumberAndUserId(Long prId, Boolean isPinned) {
    final UserPreference userPreference =
        userPreferenceRepository
            .findByUser(authService.getUserFromGithubId())
            .orElseGet(
                () -> {
                  final UserPreference pref = new UserPreference();
                  pref.setUser(authService.getUserFromGithubId());
                  pref.setFavouriteBranches(new HashSet<>());
                  pref.setFavouritePullRequests(new HashSet<>());
                  return userPreferenceRepository.saveAndFlush(pref);
                });

    if (!isPinned) {
      userPreference.getFavouritePullRequests().removeIf(pr -> pr.getId().equals(prId));
    } else {
      final PullRequest pullRequest =
          pullRequestRepository
              .findById(prId)
              .orElseThrow(() -> new EntityNotFoundException("PR " + prId + " not found"));
      userPreference.getFavouritePullRequests().add(pullRequest);
    }
    userPreferenceRepository.save(userPreference);
  }

  public Optional<PullRequestInfoDto> getPullRequestByRepositoryIdAndNumber(
      Long repoId, Integer number) {
    return pullRequestRepository
        .findByRepositoryRepositoryIdAndNumber(repoId, number)
        .map(PullRequestInfoDto::fromPullRequest);
  }
}
