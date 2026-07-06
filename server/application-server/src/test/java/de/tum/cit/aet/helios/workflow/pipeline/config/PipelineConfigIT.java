package de.tum.cit.aet.helios.workflow.pipeline.config;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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

  @Test
  void configEndpointsAreScopedPerRepository() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    final long repoB = 2L;
    insertRepo(jdbc, repoB, "ls1intum/repo-b");

    // A maintainer saves a config on repo A only.
    putConfig(REPO, """
        {"categories":[{"name":"Build","nodes":[
          {"key":"native","label":"Native","jobNameMatchers":["Build / X"],\
        "workflowNameMatcher":null}]}]}""");

    // The write touched only repo A's rows — repo B has none.
    assertCategoryCount(jdbc, REPO, 1);
    assertCategoryCount(jdbc, repoB, 0);

    // Repo B's GET returns its own (detected, empty) default — never repo A's "native" node.
    mockMvc
        .perform(get("/api/settings/{id}/pipeline-config", repoB).with(maintainer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[*].nodes[*].key", not(hasItem("native"))));
  }

  @Test
  void updateReplacesAndReordersExistingConfig() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    // First save: Build (2 nodes) then Tests (1 node).
    putConfig(REPO, """
        {"categories":[
          {"name":"Build","nodes":[
            {"key":"native","label":"Native","jobNameMatchers":["Build / A"],\
        "workflowNameMatcher":null},
            {"key":"docker","label":"Docker","jobNameMatchers":["Build / B","Build / C"],\
        "workflowNameMatcher":"CI"}]},
          {"name":"Tests","nodes":[
            {"key":"client","label":"Client","jobNameMatchers":["Test / X"],\
        "workflowNameMatcher":null}]}]}""");
    // Replace: Tests first (reordered), Build dropped to a single node.
    putConfig(REPO, """
        {"categories":[
          {"name":"Tests","nodes":[
            {"key":"client","label":"Client","jobNameMatchers":["Test / X"],\
        "workflowNameMatcher":null}]},
          {"name":"Build","nodes":[
            {"key":"native","label":"Native","jobNameMatchers":["Build / A"],\
        "workflowNameMatcher":null}]}]}""");

    mockMvc
        .perform(get("/api/settings/{id}/pipeline-config", REPO).with(maintainer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories.length()").value(2))
        .andExpect(jsonPath("$.categories[0].name").value("Tests"))
        .andExpect(jsonPath("$.categories[1].name").value("Build"))
        .andExpect(jsonPath("$.categories[1].nodes.length()").value(1))
        .andExpect(jsonPath("$.categories[1].nodes[0].key").value("native"));
    // The replace left no orphaned rows from the larger first config.
    assertCategoryCount(jdbc, REPO, 2);
    assertNodeCount(jdbc, REPO, 2);
    assertMatcherCount(jdbc, REPO, 2);
  }

  @Test
  void putWithMismatchedRepositoryContextIsForbidden() throws Exception {
    // Path repo != X-Repository-Id header repo → the controller's context guard rejects the write,
    // so a maintainer of one repo can't edit another's config by swapping the path id.
    mockMvc
        .perform(
            put("/api/settings/{id}/pipeline-config", REPO)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, "999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categories\":[]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void nonMaintainerCannotUpdateConfig() throws Exception {
    mockMvc
        .perform(
            put("/api/settings/{id}/pipeline-config", REPO)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_USER")))
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"categories\":[]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void blankAndNullMatchersAndNodesAreSanitizedOnWrite() throws Exception {
    // The "native" node keeps only its real matcher; the blank-labelled node is dropped entirely.
    putConfig(REPO, """
        {"categories":[{"name":"Build","nodes":[
          {"key":"native","label":"Native","jobNameMatchers":["Build / A","",null,"  "],\
        "workflowNameMatcher":null},
          {"key":"blank","label":"   ","jobNameMatchers":["Build / B"],\
        "workflowNameMatcher":null}]}]}""");

    mockMvc
        .perform(get("/api/settings/{id}/pipeline-config", REPO).with(maintainer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.categories[0].nodes.length()").value(1))
        .andExpect(jsonPath("$.categories[0].nodes[0].label").value("Native"))
        .andExpect(jsonPath("$.categories[0].nodes[0].jobNameMatchers.length()").value(1))
        .andExpect(jsonPath("$.categories[0].nodes[0].jobNameMatchers[0]").value("Build / A"));
  }

  private void putConfig(long repositoryId, String body) throws Exception {
    mockMvc
        .perform(
            put("/api/settings/{id}/pipeline-config", repositoryId)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk());
  }

  private void assertCategoryCount(JdbcTemplate jdbc, long repositoryId, int expected) {
    assertCount(
        jdbc,
        expected,
        "SELECT count(*) FROM pipeline_category pc "
            + "JOIN repository_settings rs ON pc.repository_settings_id = rs.id "
            + "WHERE rs.repository_id = ?",
        repositoryId);
  }

  private void assertNodeCount(JdbcTemplate jdbc, long repositoryId, int expected) {
    assertCount(
        jdbc,
        expected,
        "SELECT count(*) FROM pipeline_node pn "
            + "JOIN pipeline_category pc ON pn.pipeline_category_id = pc.id "
            + "JOIN repository_settings rs ON pc.repository_settings_id = rs.id "
            + "WHERE rs.repository_id = ?",
        repositoryId);
  }

  private void assertMatcherCount(JdbcTemplate jdbc, long repositoryId, int expected) {
    assertCount(
        jdbc,
        expected,
        "SELECT count(*) FROM pipeline_node_job_matcher m "
            + "JOIN pipeline_node pn ON m.pipeline_node_id = pn.id "
            + "JOIN pipeline_category pc ON pn.pipeline_category_id = pc.id "
            + "JOIN repository_settings rs ON pc.repository_settings_id = rs.id "
            + "WHERE rs.repository_id = ?",
        repositoryId);
  }

  private static void assertCount(JdbcTemplate jdbc, int expected, String sql, Object... args) {
    final Integer actual = jdbc.queryForObject(sql, Integer.class, args);
    if (actual == null || actual != expected) {
      throw new AssertionError("expected count " + expected + " but was " + actual);
    }
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
