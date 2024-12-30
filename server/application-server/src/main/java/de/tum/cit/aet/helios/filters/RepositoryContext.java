package de.tum.cit.aet.helios.filters;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RepositoryContext {

  private RepositoryContext() {}

  private static final InheritableThreadLocal<Long> currentRepository =
      new InheritableThreadLocal<>();

  public static void setRepositoryId(String repositoryId) {
    try {
      currentRepository.set(Long.parseLong(repositoryId));
    } catch (NumberFormatException e) {
      currentRepository.set(null);
      log.warn("Warning: Repository id could not be formatted - skipping filter activation");
    }
  }

  public static Long getRepositoryId() {
    return currentRepository.get();
  }

  public static void clear() {
    currentRepository.remove();
  }
}
