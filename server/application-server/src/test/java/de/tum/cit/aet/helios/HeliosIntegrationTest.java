package de.tum.cit.aet.helios;

import de.tum.cit.aet.helios.github.GitHubClientManager;
import de.tum.cit.aet.helios.github.GitHubFacade;
import de.tum.cit.aet.helios.github.GitHubService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base class for full-context integration tests. Boots the whole application against a real embedded
 * PostgreSQL (zonky, via Docker) with the real Flyway schema, and exposes {@link MockMvc} so tests
 * drive the actual servlet stack — interceptors, Open-Session-In-View, and the repository layer.
 *
 * <p>This is the harness for the tenant-isolation guard tests: it exercises the same request path that
 * production uses, so a scoping test written here passes both with the legacy Hibernate
 * {@code gitRepositoryFilter} and with explicit per-query filtering after the migration.
 *
 * <p>External integrations that would otherwise reach out on startup are neutralised: NATS is disabled
 * ({@code nats.enabled=false}), the schedulers/reconciliation/notifications/AI are turned off, and the
 * GitHub clients are mocked so no credentials or network are required.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER)
public abstract class HeliosIntegrationTest {

  public static final String X_REPOSITORY_ID = "X-REPOSITORY-ID";

  @Autowired protected MockMvc mockMvc;
  @Autowired protected DataSource dataSource;

  // GitHub clients reach out to GitHub on construction/first use; mock them so the context boots
  // without credentials or network. Read (GET) scoping tests do not exercise GitHub.
  @MockitoBean protected GitHubClientManager gitHubClientManager;
  @MockitoBean protected GitHubFacade gitHubFacade;
  @MockitoBean protected GitHubService gitHubService;
}
