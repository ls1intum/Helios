package de.tum.cit.aet.helios.tag;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
public class TagController {
  private final TagService tagService;

  public TagController(TagService tagService) {
    this.tagService = tagService;
  }

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

  @PutMapping
  public ResponseEntity<TagInfoDto> createTag(@RequestBody TagCreateDto tag) {
    return ResponseEntity.ok(tagService.createTag(tag));
  }

  @PutMapping("{name}/markworking")
  public ResponseEntity<Void> markWorking(@PathVariable String name, @RequestBody String login) {
    tagService.markTagWorking(name, login.replaceAll("\"", ""));
    return ResponseEntity.ok().build();
  }

  @PutMapping("{name}/markbroken")
  public ResponseEntity<Void> markBroken(@PathVariable String name, @RequestBody String login) {
    tagService.markTagBroken(name, login.replaceAll("\"", ""));
    return ResponseEntity.ok().build();
  }
}
