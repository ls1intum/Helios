package de.tum.cit.aet.helios.filters;

import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.hibernate.Session;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.ui.ModelMap;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.WebRequestInterceptor;

@Component
@RequiredArgsConstructor
@Log4j2
public class RepositoryInterceptor implements WebRequestInterceptor {

  public static final String X_REPOSITORY_ID = "X-REPOSITORY-ID";

  private final EntityManagerFactory entityManagerFactory;

  @Override
  public void preHandle(@NonNull WebRequest request) {
    final String tenantId = request.getHeader(X_REPOSITORY_ID);
    if (tenantId == null) {
      return;
    }
    RepositoryContext.setRepositoryId(tenantId);
    final Long repositoryId = RepositoryContext.getRepositoryId();
    if (repositoryId == null) {
      return;
    }
    // Enable Hibernate's gitRepositoryFilter on this request's EntityManager so tenant-filtered
    // entities are scoped to the current repository. RepositoryFilterTransactionConfig only
    // enables the filter on EntityManagers created by the transaction manager, which does NOT
    // happen under Open-Session-In-View (the request's transaction reuses the EM opened by OSIV).
    // Without this, web reads (pull requests, environments, ...) leak across all repositories.
    // This interceptor is ordered after OSIV (see SecurityConfig#corsConfigurer) so the request's
    // EntityManager is already bound here.
    final EntityManagerHolder holder =
        (EntityManagerHolder) TransactionSynchronizationManager.getResource(entityManagerFactory);
    if (holder != null) {
      holder
          .getEntityManager()
          .unwrap(Session.class)
          .enableFilter("gitRepositoryFilter")
          .setParameter("repository_id", repositoryId);
    } else {
      log.warn(
          "gitRepositoryFilter not enabled for repository {}: no EntityManager bound at preHandle",
          repositoryId);
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
