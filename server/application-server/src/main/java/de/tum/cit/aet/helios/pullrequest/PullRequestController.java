package de.tum.cit.aet.helios.pullrequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@RestController
@RequestMapping("/api/pullrequests")
public class PullRequestController {

    private final PullRequestService pullRequestService;

    public PullRequestController(PullRequestService pullRequestService) {
        this.pullRequestService = pullRequestService;
    }

    @GetMapping
    public ResponseEntity<List<PullRequestInfoDTO>> getAllPullRequests() {
        List<PullRequestInfoDTO> pullRequests = pullRequestService.getAllPullRequests();
        return ResponseEntity.ok(pullRequests);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PullRequestInfoDTO> getPullRequestById(@PathVariable Long id) {
        return pullRequestService.getPullRequestById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // TODO: Generalize filtering with repositoryId
    @GetMapping("/repository/{id}")
    public ResponseEntity<List<PullRequestInfoDTO>> getPullRequestByRepositoryId(@PathVariable Long id) {
        List<PullRequestInfoDTO> pullRequests = pullRequestService.getPullRequestByRepositoryId(id);
        return ResponseEntity.ok(pullRequests);
    }

}