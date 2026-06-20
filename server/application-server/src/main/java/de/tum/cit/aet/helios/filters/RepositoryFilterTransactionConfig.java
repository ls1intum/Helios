package de.tum.cit.aet.helios.filters;

import org.hibernate.Session;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;

/**
 * Enables Hibernate's {@code gitRepositoryFilter} on the transactional {@link
 * jakarta.persistence.EntityManager} when a request-scoped repository ID is present in
 * {@link RepositoryContext}, scoping all queries against tenant-filtered entities (those
 * annotated with {@code @Filter(name = "gitRepositoryFilter")}) to that repository.
 *
 * <p>Wires up via {@link JpaTransactionManager#setEntityManagerInitializer} — Spring's
 * purpose-built hook for "do X to each new transactional {@link
 * jakarta.persistence.EntityManager}", added specifically for the Hibernate-filter use
 * case. It fires inside {@code createEntityManagerForTransaction()} with a guaranteed
 * non-null EM, before the JDBC connection is acquired and before the dialect begins the
 * transaction. We customise Spring Boot's auto-configured {@link JpaTransactionManager}
 * via a {@link BeanPostProcessor} rather than declaring a {@code @Primary} replacement,
 * so we keep all of Boot's other tx-manager wiring intact.
 *
 * <p>Replaces the legacy {@code RepositoryFilterAspect} + {@code @EnableLoadTimeWeaving}
 * setup, which targeted a Hibernate-internal class ({@code SessionBuilder.openSession})
 * and therefore required AspectJ load-time weaving plus the {@code aspectjweaver} and
 * {@code spring-instrument} javaagents at JVM startup. The new mechanism is agent-free.
 *
 * <p>Semantic note vs the old aspect: the filter is enabled at transaction begin (per
 * new transactional EM), not at every Hibernate session open. Tenant-filtered entity
 * access outside a {@code @Transactional} boundary is therefore not filtered — the
 * codebase consistently wraps such access in {@code @Transactional}, so this matches
 * existing call patterns.
 */
@Configuration
public class RepositoryFilterTransactionConfig {

  /**
   * Bean post-processor that catches Spring Boot's auto-configured {@link
   * JpaTransactionManager} and installs the entity-manager initializer on it. Declared
   * {@code static} so it can be created before regular singletons (the BPP contract).
   */
  @Bean
  public static BeanPostProcessor gitRepositoryFilterEnabler() {
    return new BeanPostProcessor() {
      @Override
      public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JpaTransactionManager jpaTxManager) {
          jpaTxManager.setEntityManagerInitializer(
              em -> {
                Long repositoryId = RepositoryContext.getRepositoryId();
                if (repositoryId == null) {
                  return;
                }
                em.unwrap(Session.class)
                    .enableFilter("gitRepositoryFilter")
                    .setParameter("repository_id", repositoryId);
              });
        }
        return bean;
      }
    };
  }
}
