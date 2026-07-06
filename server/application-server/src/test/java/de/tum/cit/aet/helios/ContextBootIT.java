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
    // Guards the "Large Objects may not be used in auto-commit mode" fix: pooled connections must
    // start with auto-commit off so reads outside an explicit @Transactional (under OSIV) can read
    // PostgreSQL Large Objects (the @Lob PR body, oid release bodies). Dropping this property
    // reintroduces the staging 500s. The zonky test datasource is auto-commit=true and ignores it,
    // so behaviour is validated on staging; this fails fast if the property is removed.
    assertEquals("false", environment.getProperty("spring.datasource.hikari.auto-commit"));
  }
}
