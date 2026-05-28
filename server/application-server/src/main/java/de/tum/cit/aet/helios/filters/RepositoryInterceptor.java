package de.tum.cit.aet.helios.filters;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;

@Component
public class RepositoryInterceptor implements WebRequestInterceptor {

  public static final String X_REPOSITORY_ID = "X-REPOSITORY-ID";

  @Override
  public void preHandle(@NonNull WebRequest request) {
    if (request.getHeader(X_REPOSITORY_ID) != null) {
      String tenantId = request.getHeader(X_REPOSITORY_ID);
      RepositoryContext.setRepositoryId(tenantId);
    }
  }

  @Override
  public void postHandle(@NonNull WebRequest request, @Nullable ModelMap model) {
    // Intentionally empty — clearing happens in afterCompletion so the ThreadLocal is wiped
    // even when the handler threw (postHandle is skipped on exception, afterCompletion runs
    // unconditionally). Otherwise a Tomcat worker thread keeps a stale repositoryId across
    // requests, which would silently apply the previous user's tenant filter to the next.
  }

  @Override
  public void afterCompletion(@NonNull WebRequest request, @Nullable Exception ex) {
    RepositoryContext.clear();
  }
}
