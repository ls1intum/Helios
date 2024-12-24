package de.tum.cit.aet.helios.filters;

import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.hibernate.Filter;
import org.hibernate.Session;

@Aspect
public class RepositoryFilterAspect {
  @Pointcut(
      "execution (* org.hibernate.internal.SessionFactoryImpl.SessionBuilderImpl.openSession(..))")
  public void openSession() {
    System.out.println("openSession");
  }

  @AfterReturning(pointcut = "openSession()", returning = "session")
  public void afterOpenSession(Object session) {
    if (session != null && Session.class.isInstance(session)) {
      final Long repositoryId = RepositoryContext.getRepositoryId();
      if (repositoryId != null) {
        Filter filter = ((Session) session).enableFilter("gitRepositoryFilter");
        filter.setParameter("repository_id", repositoryId);
      }
    }
  }
}
