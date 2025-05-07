package de.tum.cit.aet.helios.environment.status;

import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/environments/status")
@RequiredArgsConstructor
public class EnvironmentStatusController {
  private final StatusCheckService statusCheckService;

  @PostMapping
  public ResponseEntity<Void> update(
      // populated by filter
      @RequestAttribute("repository") GitRepoSettings repo,
      @Valid @RequestBody PushStatusPayload body) {

    statusCheckService.processPush(repo, body);
    return ResponseEntity.accepted().build();
  }
}
