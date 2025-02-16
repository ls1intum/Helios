package de.tum.cit.aet.helios.label.github;


import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.label.LabelRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.kohsuke.github.GHLabel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GitHubLabelSyncService {

  private final LabelRepository labelRepository;
  private final GitRepoRepository gitRepoRepository;
  private final GitHubLabelConverter labelConverter;


  /**
   * Processes a GitHub label and ensures it is synchronized with the local
   * repository.
   *
   * @param ghLabel the GitHub label to process
   */
  @Transactional
  public void processLabel(GHLabel ghLabel) {
    var result = labelRepository
        .findById(ghLabel.getId())
        .map(label -> {
          return labelConverter.update(ghLabel, label);
        })
        .orElseGet(() -> labelConverter.convert(ghLabel));

    if (result == null) {
      return;
    }

    // Link with existing repository if not already linked
    if (result.getRepository() == null) {
      // Extract name with owner from the repository URL
      // Example: https://api.github.com/repos/ls1intum/Artemis/labels/core
      var nameWithOwner = ghLabel.getUrl().split("/repos/")[1].split("/label")[0];
      result.setRepository(gitRepoRepository.findByNameWithOwner(nameWithOwner));
    }

    labelRepository.save(result);
  }
}