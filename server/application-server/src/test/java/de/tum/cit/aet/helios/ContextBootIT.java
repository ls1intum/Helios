package de.tum.cit.aet.helios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

/** Smoke test: the full application context boots and the servlet stack answers a public GET. */
class ContextBootIT extends HeliosIntegrationTest {

  @Autowired private Environment environment;

  @Test
  void contextLoadsAndApiResponds() throws Exception {
    mockMvc.perform(get("/api/repository")).andExpect(status().isOk());
  }

  @Test
  void hikariAutoCommitIsDisabled() {
    // Pooled connections start with auto-commit off: a Hibernate-recommended optimization (fewer
    // per-transaction round-trips) and defense-in-depth against a future
    // accidental @Lob (PostgreSQL Large Objects can't be read in auto-commit mode). The Issue.body
    // Large Object that originally forced this has since been migrated to a plain text column.
    assertEquals("false", environment.getProperty("spring.datasource.hikari.auto-commit"));
  }
}
