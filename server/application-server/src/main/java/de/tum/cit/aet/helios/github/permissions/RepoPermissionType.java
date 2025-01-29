package de.tum.cit.aet.helios.github.permissions;

public enum RepoPermissionType {
  ADMIN("admin"),
  WRITE("write"),
  READ("read"),
  NONE("none");

  private final String value;

  RepoPermissionType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static RepoPermissionType fromString(String text) {
    for (RepoPermissionType permission : RepoPermissionType.values()) {
      if (permission.value.equalsIgnoreCase(text)) {
        return permission;
      }
    }
    return NONE;
  }
}
