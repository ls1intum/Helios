package de.tum.cit.aet.helios.filters;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;

/**
 * Publishes the current repository id (from the {@code X-REPOSITORY-ID} header) into
 * {@link RepositoryContext} for the duration of the request. Repository-scoped services read it and
 * filter their queries explicitly (there is no longer any ambient Hibernate filter).
 */
@Component
public class RepositoryInterceptor implements WebRequestInterceptor {

  public static final String X_REPOSITORY_ID = "X-REPOSITORY-ID";

  @Override
  public void preHandle(@NonNull WebRequest request) {
    final String tenantId = request.getHeader(X_REPOSITORY_ID);
    if (tenantId != null) {
      RepositoryContext.setRepositoryId(tenantId);
    } else {
      // No repository header → no repository context (also defends against a stale ThreadLocal
      // left on a reused worker thread).
      RepositoryContext.clear();
    }
  }

  @Override
  public void postHandle(@NonNull WebRequest request, @Nullable ModelMap model) {
    // Intentionally empty — clearing happens in afterCompletion so the ThreadLocal is wiped even
    // when the handler threw (postHandle is skipped on exception, afterCompletion runs
    // unconditionally). Otherwise a Tomcat worker thread keeps a stale repositoryId between
    // requests.
  }

  @Override
  public void afterCompletion(@NonNull WebRequest request, @Nullable Exception ex) {
    RepositoryContext.clear();
  }
}
