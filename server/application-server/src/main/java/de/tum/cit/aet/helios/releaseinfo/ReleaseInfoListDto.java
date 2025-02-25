package de.tum.cit.aet.helios.releaseinfo;

import de.tum.cit.aet.helios.releaseinfo.releasecandidate.ReleaseCandidate;

public record ReleaseInfoListDto(
    String name, String commitSha, String branchName, boolean isPublished) {
  public static ReleaseInfoListDto fromReleaseCandidate(ReleaseCandidate releaseCandidate) {
    return new ReleaseInfoListDto(
        releaseCandidate.getName(),
        releaseCandidate.getCommit().getSha(),
        releaseCandidate.getBranch() != null ? releaseCandidate.getBranch().getName() : null,
        releaseCandidate.getRelease() != null ? true : false);
  }
}
