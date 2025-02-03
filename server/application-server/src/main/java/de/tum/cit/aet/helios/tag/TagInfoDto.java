package de.tum.cit.aet.helios.tag;


public record TagInfoDto(String name, String commitSha, String branchName) {
  public static TagInfoDto fromTag(Tag tag) {
    return new TagInfoDto(tag.getName(), tag.getCommit().getSha(), tag.getBranch().getName());
  }
}
