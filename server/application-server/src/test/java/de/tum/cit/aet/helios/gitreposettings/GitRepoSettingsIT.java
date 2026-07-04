package de.tum.cit.aet.helios.gitreposettings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Guards the write-on-GET fix: {@code GET /api/settings/{id}/settings} must be a pure read. For a
 * repository with no settings row it returns transient defaults and persists nothing. Previously a
 * GET INSERTed a row as a side effect, and a plain GET must never write.
 */
class GitRepoSettingsIT extends HeliosIntegrationTest {

  private static final long REPO = 1L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO, "ls1intum/repo-a");
  }

  @Test
  void getSettingsReturnsDefaultsAndDoesNotPersistARow() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    assertSettingsRowCount(jdbc, 0);

    mockMvc
        .perform(get("/api/settings/{id}/settings", REPO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockExpirationThreshold").value(60))
        .andExpect(jsonPath("$.lockReservationThreshold").value(30));

    // The GET must not have created a settings row (write-on-GET regression).
    assertSettingsRowCount(jdbc, 0);

    // A second GET is still a pure read.
    mockMvc.perform(get("/api/settings/{id}/settings", REPO)).andExpect(status().isOk());
    assertSettingsRowCount(jdbc, 0);
  }

  private static void assertSettingsRowCount(JdbcTemplate jdbc, int expected) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM repository_settings WHERE repository_id = ?",
            Integer.class,
            REPO);
    if (count == null || count != expected) {
      throw new AssertionError(
          "expected " + expected + " repository_settings rows but found " + count);
    }
  }

  private static void insertRepo(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }
}
