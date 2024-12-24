package de.tum.cit.aet.helios.branch.github;

import de.tum.cit.aet.helios.branch.Branch;
import lombok.extern.log4j.Log4j2;
import org.kohsuke.github.GHBranch;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class GitHubBranchConverter implements Converter<GHBranch, Branch> {
  @Override
  public Branch convert(@NonNull GHBranch source) {
    return update(source, new Branch());
  }

  public Branch update(@NonNull GHBranch source, @NonNull Branch branch) {
    try {
      if (source.getName() != null) {
        branch.setName(source.getName());
      } else {
        throw new IllegalArgumentException("Branch name cannot be null");
      }
      branch.setCommitSha(source.getSHA1());
      branch.setProtection(source.isProtected());
    } catch (Exception e) {
      log.error("Failed to convert fields for source {}: {}", source.getName(), e.getMessage());
    }
    return branch;
  }
}
