package de.tum.cit.aet.helios;

import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
public class HibernateInterceptor implements HandlerInterceptor {

  @Autowired private EntityManager entityManager;

  @Override
  @Transactional
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    Session session = entityManager.unwrap(Session.class);
    String repositoryId = request.getHeader("X-Repository-Id");

    if (repositoryId != null) {
      session.enableFilter("gitRepositoryFilter").setParameter("repository_id", repositoryId);
    }

    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView)
      throws Exception {
    Session session = entityManager.unwrap(Session.class);
    session.disableFilter("gitRepositoryFilter");
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
      throws Exception {
    // This method is called after the response has been sent to the client
  }
}
