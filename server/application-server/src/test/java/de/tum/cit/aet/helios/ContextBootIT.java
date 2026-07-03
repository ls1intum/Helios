package de.tum.cit.aet.helios;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Smoke test: the full application context boots and the servlet stack answers a public GET. */
class ContextBootIT extends HeliosIntegrationTest {

  @Test
  void contextLoadsAndApiResponds() throws Exception {
    mockMvc.perform(get("/api/repository")).andExpect(status().isOk());
  }
}
