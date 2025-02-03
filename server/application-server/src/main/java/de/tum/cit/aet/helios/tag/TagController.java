package de.tum.cit.aet.helios.tag;

import de.tum.cit.aet.helios.config.security.annotations.EnforceAtLeastMaintainer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {
  private final TagService tagService;

  @GetMapping
  public ResponseEntity<List<TagInfoDto>> getAllTags() {
    return ResponseEntity.ok(tagService.getAllTags());
  }

  @GetMapping("/{name}")
  public ResponseEntity<TagDetailsDto> getTagByName(@PathVariable String name) {
    return ResponseEntity.ok(tagService.getTagByName(name));
  }

  @GetMapping("/newcommits/{name}")
  public ResponseEntity<CommitsSinceTagDto> getCommitsSinceLastTag(@PathVariable String name) {
    return ResponseEntity.ok(tagService.getCommitsFromBranchSinceLastTag(name));
  }

  @PostMapping
  @EnforceAtLeastMaintainer
  public ResponseEntity<TagInfoDto> createTag(@RequestBody TagCreateDto tag) {
    return ResponseEntity.ok(tagService.createTag(tag));
  }

  @PostMapping("{name}/evaluate/{isWorking}")
  public ResponseEntity<Void> evaluate(@PathVariable String name, @PathVariable boolean isWorking) {
    tagService.evaluateTag(name, isWorking);
    return ResponseEntity.ok().build();
  }
}
