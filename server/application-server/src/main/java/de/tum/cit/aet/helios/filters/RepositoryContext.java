package de.tum.cit.aet.helios.filters;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class RepositoryContext {

  private RepositoryContext() {}

  // Plain ThreadLocal — no consumer in this codebase relies on parent→child inheritance, and
  // inheritance is a footgun under virtual threads (the value propagates at thread creation,
  // so any virtual thread started inside a request would silently inherit that request's tenant).
  // Async / scheduled / NATS-handler paths do their own repo resolution.
  private static final ThreadLocal<Long> currentRepository = new ThreadLocal<>();

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
