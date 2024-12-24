package de.tum.cit.aet.helios.filters;

public class RepositoryContext {

  private RepositoryContext() {}

  private static final InheritableThreadLocal<String> currentRepository =
      new InheritableThreadLocal<>();

  public static void setRepositoryId(String repositoryId) {
    System.out.println("Setting tenantId to " + repositoryId);
    currentRepository.set(repositoryId);
  }

  public static Long getRepositoryId() {
    try {
      return Long.parseLong(currentRepository.get());
    } catch (NumberFormatException e) {
      System.out.println("Error formatting repository id - skipping filter");
    }
    return null;
  }

  public static void clear() {
    currentRepository.remove();
  }
}
