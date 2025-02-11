package de.tum.cit.aet.helios.releasecandidate;

public record ReleaseCandidateInfoDto(String name, String commitSha, String branchName) {
  public static ReleaseCandidateInfoDto fromReleaseCandidate(ReleaseCandidate releaseCandidate) {
    return new ReleaseCandidateInfoDto(
        releaseCandidate.getName(),
        releaseCandidate.getCommit().getSha(),
        releaseCandidate.getBranch().getName());
  }
}
