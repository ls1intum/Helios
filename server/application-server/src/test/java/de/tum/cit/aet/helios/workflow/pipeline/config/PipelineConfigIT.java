package de.tum.cit.aet.helios.workflow.pipeline.config;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * Guards the per-repository pipeline-config endpoints: an unconfigured repo's GET returns the
 * auto-detected default; a maintainer PUT persists (and drives {@code /api/pipeline}); the
 * suggestions endpoint returns detected nodes.
 */
class PipelineConfigIT extends HeliosIntegrationTest {

  private static final long REPO = 1L;
  private static final long WF = 51L;
  private static final long RUN = 61L;
  private static final String BRANCH = "main";
  private static final String SHA = "deadbeef";

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO, "ls1intum/repo-a");
    insertBranch(jdbc, REPO, BRANCH, SHA);
    insertWorkflow(jdbc, WF, REPO);
    insertRun(jdbc, RUN, REPO, WF, BRANCH, SHA);
    insertJob(jdbc, 101, REPO, RUN, "Build / Build .war artifact", "COMPLETED", "SUCCESS");
  }

  @Test
  void getConfigReturnsAutoDetectedDefaultWhenUnconfigured() throws Exception {
    mockMvc
        .perform(get("/api/settings/{id}/pipeline-config", REPO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[*].name", hasItem("Build")))
        .andExpect(jsonPath("$.categories[*].name", hasItem("Test")))
        .andExpect(jsonPath("$.categories[*].name", hasItem("Quality")))
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Build')].nodes[?(@.label=='Build .war artifact')].key",
                hasItem("build-build-war-artifact")));
  }

  @Test
  void maintainerPutPersistsConfigAndItDrivesThePipeline() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    String body =
        "{\"categories\":[{\"name\":\"Build\",\"nodes\":[{\"key\":\"native\",\"label\":\"Native\","
            + "\"jobNameMatchers\":[\"Build / Build .war artifact\"],"
            + "\"workflowNameMatcher\":null}]}]}";

    mockMvc
        .perform(
            put("/api/settings/{id}/pipeline-config", REPO)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].name").value("Build"))
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("native"));

    // Persisted a category row.
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM pipeline_category pc JOIN repository_settings rs "
                + "ON pc.repository_settings_id = rs.id WHERE rs.repository_id = ?",
            Integer.class,
            REPO);
    if (count == null || count != 1) {
      throw new AssertionError("expected 1 pipeline_category row but found " + count);
    }

    // GET returns the persisted config (not the detected default).
    mockMvc
        .perform(get("/api/settings/{id}/pipeline-config", REPO))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(1))
        .andExpect(jsonPath("$.categories[0].nodes[0].label").value("Native"));

    // The persisted node drives /api/pipeline: it matches the seeded job → COMPLETED/SUCCESS.
    mockMvc
        .perform(
            get("/api/pipeline/branch")
                .param("branch", BRANCH)
                .header(X_REPOSITORY_ID, String.valueOf(REPO)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(1))
        .andExpect(jsonPath("$.categories[0].nodes[0].key").value("native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$.categories[0].nodes[0].conclusion").value("SUCCESS"));
  }

  @Test
  void suggestionsEndpointReturnsDetectedNodes() throws Exception {
    mockMvc
        .perform(get("/api/settings/{id}/pipeline-config/suggestions", REPO).with(maintainer()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.categories[?(@.name=='Build')].nodes[?(@.label=='Build .war artifact')].key",
                hasItem("build-build-war-artifact")));
  }

  private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor maintainer() {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .authorities(new SimpleGrantedAuthority("ROLE_MAINTAINER"));
  }

  private static void insertRepo(JdbcTemplate jdbc, long id, String nameWithOwner) {
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner) "
            + "VALUES (?, false, false, false, false, false, false, 0, 0, ?)",
        id,
        nameWithOwner);
  }

  private static void insertBranch(JdbcTemplate jdbc, long repositoryId, String name, String sha) {
    jdbc.update(
        "INSERT INTO branch (repository_id, name, ahead_by, behind_by, is_default, protection, "
            + "commit_sha) VALUES (?, ?, 0, 0, true, false, ?)",
        repositoryId,
        name,
        sha);
  }

  private static void insertWorkflow(JdbcTemplate jdbc, long id, long repositoryId) {
    jdbc.update(
        "INSERT INTO workflow (id, repository_id, state, label, name) "
            + "VALUES (?, ?, 'ACTIVE', 'NONE', ?)",
        id,
        repositoryId,
        "wf-" + id);
  }

  private static void insertRun(
      JdbcTemplate jdbc, long id, long repositoryId, long workflowId, String branch, String sha) {
    jdbc.update(
        "INSERT INTO workflow_run (id, repository_id, workflow_id, run_attempt, run_number, name, "
            + "status, head_branch, head_sha, created_at, updated_at) "
            + "VALUES (?, ?, ?, 1, ?, 'CI', 'COMPLETED', ?, ?, now(), now())",
        id,
        repositoryId,
        workflowId,
        id,
        branch,
        sha);
  }

  private static void insertJob(
      JdbcTemplate jdbc,
      long id,
      long repositoryId,
      long runId,
      String name,
      String statusValue,
      String conclusionValue) {
    jdbc.update(
        "INSERT INTO workflow_job (id, repository_id, workflow_run_id, name, workflow_name, "
            + "status, conclusion, html_url) VALUES (?, ?, ?, ?, 'CI', ?, ?, ?)",
        id,
        repositoryId,
        runId,
        name,
        statusValue,
        conclusionValue,
        "https://example/job/" + id);
  }
}
