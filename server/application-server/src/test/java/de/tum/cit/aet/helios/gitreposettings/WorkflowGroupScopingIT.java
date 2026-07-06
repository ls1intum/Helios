package de.tum.cit.aet.helios.gitreposettings;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.HeliosIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

/**
 * Cross-repository write guard for {@code PUT /api/settings/{repositoryId}/groups/update}
 * (maintainer-gated). Group memberships reference workflows by id, and those lookups previously
 * used unscoped {@code findAllById}/{@code findById}, so a maintainer could pull a workflow from
 * another repository into their group. Membership workflows must belong to the path repository: a
 * foreign workflow id is rejected (400) and no membership is created for it.
 */
class WorkflowGroupScopingIT extends HeliosIntegrationTest {

  private static final long REPO_A = 1L;
  private static final long REPO_B = 2L;
  private static final long WF_A = 51L;
  private static final long WF_B = 52L;

  @BeforeEach
  void seed() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("TRUNCATE TABLE repository CASCADE");
    insertRepo(jdbc, REPO_A, "ls1intum/repo-a");
    insertRepo(jdbc, REPO_B, "ls1intum/repo-b");
    insertWorkflow(jdbc, WF_A, REPO_A);
    insertWorkflow(jdbc, WF_B, REPO_B);
  }

  @Test
  void addingAWorkflowFromAnotherRepositoryToAGroupIsRejectedAndCreatesNoMembership()
      throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    long groupId = createGroup();

    // Group belongs to repo A; try to add repo B's workflow → rejected as non-existent (400).
    mockMvc
        .perform(
            put("/api/settings/{repositoryId}/groups/update", REPO_A)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(groupId, WF_B)))
        .andExpect(status().isBadRequest());

    assertMembershipCount(jdbc, WF_B, 0);
  }

  @Test
  void addingAWorkflowFromTheSameRepositoryToAGroupSucceeds() throws Exception {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    long groupId = createGroup();

    mockMvc
        .perform(
            put("/api/settings/{repositoryId}/groups/update", REPO_A)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateBody(groupId, WF_A)))
        .andExpect(status().isNoContent());

    assertMembershipCount(jdbc, WF_A, 1);
  }

  /** Creates a workflow group in repo A via the real endpoint and returns its generated id. */
  private long createGroup() throws Exception {
    mockMvc
        .perform(
            post("/api/settings/{repositoryId}/groups/create", REPO_A)
                .with(maintainer())
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .header(X_REPOSITORY_ID, String.valueOf(REPO_A))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Group A\",\"orderIndex\":0}"))
        .andExpect(status().isOk());
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    return jdbc.queryForObject(
        "SELECT id FROM workflow_group WHERE name = 'Group A'", Long.class);
  }

  private static String updateBody(long groupId, long workflowId) {
    return "[{\"id\":" + groupId + ",\"name\":\"Group A\",\"orderIndex\":0,"
        + "\"memberships\":[{\"workflowId\":" + workflowId + ",\"orderIndex\":0}]}]";
  }

  private static SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor maintainer() {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .authorities(new SimpleGrantedAuthority("ROLE_MAINTAINER"));
  }

  private static void assertMembershipCount(JdbcTemplate jdbc, long workflowId, int expected) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM workflow_group_membership WHERE workflow_id = ?",
            Integer.class,
            workflowId);
    if (count == null || count != expected) {
      throw new AssertionError(
          "expected " + expected + " memberships for workflow " + workflowId + " but found "
              + count);
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

  private static void insertWorkflow(JdbcTemplate jdbc, long id, long repositoryId) {
    jdbc.update(
        "INSERT INTO workflow (id, repository_id, state, label, name) "
            + "VALUES (?, ?, 'ACTIVE', 'NONE', ?)",
        id,
        repositoryId,
        "wf-" + id);
  }
}
