package de.tum.cit.aet.helios.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.tum.cit.aet.helios.github.GitHubService;
import de.tum.cit.aet.helios.gitrepo.GitRepoRepository;
import de.tum.cit.aet.helios.tests.parsers.JunitParser;
import de.tum.cit.aet.helios.workflow.WorkflowRun;
import de.tum.cit.aet.helios.workflow.WorkflowRunRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kohsuke.github.GHArtifact;
import org.kohsuke.github.PagedIterable;
import org.kohsuke.github.PagedIterator;
import org.kohsuke.github.function.InputStreamFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Integration test for {@link TestResultProcessor} against an embedded PostgreSQL (zonky) with the
 * real Flyway schema, the real {@link JunitParser}, and the real
 * {@link TestCaseStatisticsService}; only {@link GitHubService} (the artifact source) is mocked.
 * This exercises what the mocked unit tests cannot:
 *
 * <ul>
 *   <li>that a no-match run does NOT wipe pre-existing {@code test_case_statistics}/
 *       {@code test_case_flakiness} rows (the safety claim of the throw-to-PROCESSED change),
 *   <li>that real JUnit XML (named per Artemis's conventions: {@code TEST-*.xml}, {@code
 *       results.xml}) actually parses and persists {@code test_suite}/{@code test_case} rows with
 *       the correct {@code test_type_id} linkage, and
 *   <li>that the {@code *}-glob aggregates two real phased artifacts under one test type.
 * </ul>
 */
@DataJpaTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureEmbeddedDatabase(
    type = AutoConfigureEmbeddedDatabase.DatabaseType.POSTGRES,
    provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.DOCKER)
@Import(TestResultProcessorIntegrationTest.CacheTestConfig.class)
class TestResultProcessorIntegrationTest {

  /** {@code @DataJpaTest} omits cache auto-config, but entities carry {@code @Cacheable}. */
  @TestConfiguration
  static class CacheTestConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }
  }

  private static final long REPO_ID = 1L;
  private static final long RUN_ID = 1L;

  private static final String VALID_JUNIT_XML =
      """
      <testsuite name="%s" tests="2" failures="1" errors="0" skipped="0" time="1.0"
          timestamp="2026-03-27T11:34:31Z">
        <testcase name="passes" classname="pkg.Foo" time="0.1"/>
        <testcase name="fails" classname="pkg.Foo" time="0.2">
          <failure message="boom" type="AssertionError">stack trace</failure>
        </testcase>
      </testsuite>
      """;

  @Autowired private WorkflowRunRepository workflowRunRepository;
  @Autowired private GitRepoRepository gitRepoRepository;
  @Autowired private TestCaseStatisticsRepository statisticsRepository;
  @Autowired private TestCaseFlakinessRepository flakinessRepository;
  @Autowired private DataSource dataSource;

  private JdbcTemplate jdbc;
  private GitHubService gitHubService;
  private TestResultProcessor processor;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);

    // Repository on default branch "main", a "CI" workflow, and a COMPLETED run on that branch.
    jdbc.update(
        "INSERT INTO repository (repository_id, has_issues, has_projects, has_wiki, is_archived, "
            + "is_disabled, is_private, stargazers_count, watchers_count, name_with_owner, "
            + "default_branch) VALUES (1, false, false, false, false, false, false, 0, 0, "
            + "'ls1intum/Artemis', 'main')");
    jdbc.update("INSERT INTO workflow (id, repository_id, name) VALUES (1, 1, 'CI')");
    jdbc.update(
        "INSERT INTO workflow_run (id, run_attempt, run_number, workflow_id, repository_id, "
            + "head_branch, created_at) VALUES (1, 1, 1, 1, 1, 'main', now())");

    gitHubService = mock(GitHubService.class);
    processor =
        new TestResultProcessor(
            gitHubService,
            workflowRunRepository,
            gitRepoRepository,
            new JunitParser(),
            new TestCaseStatisticsService(statisticsRepository, flakinessRepository));
  }

  @Test
  void noMatchRun_doesNotWipeExistingStatisticsOrFlakiness() throws IOException {
    seedTestType("Server Tests", "Server JUnit Test Results");
    // Pre-existing stats/flakiness rows from earlier runs that this no-match run must NOT touch.
    jdbc.update(
        "INSERT INTO test_case_statistics (repository_id, test_name, class_name, test_suite_name, "
            + "branch_name, total_runs, failed_runs, last_updated) "
            + "VALUES (1, 'oldTest', 'pkg.Old', 'OldSuite', 'main', 5, 1, now())");
    jdbc.update(
        "INSERT INTO test_case_flakiness (repository_id, test_name, class_name, test_suite_name, "
            + "flakiness_score, default_branch_failure_rate, combined_failure_rate, last_updated) "
            + "VALUES (1, 'oldTest', 'pkg.Old', 'OldSuite', 42.0, 0.2, 0.1, now())");

    // The run produced only a non-matching artifact (e.g. a coverage report), so nothing matches.
    stubArtifacts(nonMatchingArtifact("Coverage Report Server Tests"));

    WorkflowRun run = workflowRunRepository.findById(RUN_ID).orElseThrow();
    processor.processRun(run);

    // No-match is "no results", not a failure — and existing stats/flakiness are untouched.
    assertThat(run.getTestProcessingStatus()).isEqualTo(WorkflowRun.TestProcessingStatus.PROCESSED);
    assertThat(countTestSuitesForRun()).isZero();
    assertThat(statisticsRepository.count()).isEqualTo(1L);
    assertThat(flakinessRepository.count()).isEqualTo(1L);
  }

  @Test
  void matchingArtifact_parsesRealJunitXmlAndPersistsSuitesWithTestType() throws IOException {
    seedTestType("Server Tests", "Server JUnit Test Results");
    long testTypeId = testTypeId();

    stubArtifacts(
        artifact("Server JUnit Test Results", "TEST-pkg.Foo.xml", xml("ServerSuite")));

    WorkflowRun run = workflowRunRepository.findById(RUN_ID).orElseThrow();
    processor.processRun(run);

    assertThat(run.getTestProcessingStatus()).isEqualTo(WorkflowRun.TestProcessingStatus.PROCESSED);
    // The real JunitParser parsed the real TEST-*.xml; the suite/cases persisted and are linked
    // to the test type.
    assertThat(countTestSuitesForRun()).isEqualTo(1L);
    assertThat(
            jdbc.queryForObject(
                "SELECT test_type_id FROM test_suite WHERE workflow_run_id = 1", Long.class))
        .isEqualTo(testTypeId);
    assertThat(countTestCases()).isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM test_case WHERE status = 'FAILED'", Integer.class))
        .isEqualTo(1);
    // The real TestCaseStatisticsService ran on the default branch and created rows.
    assertThat(statisticsRepository.count()).isGreaterThan(0L);
  }

  @Test
  void globTestType_aggregatesTwoPhasedArtifactsUnderOneTestType() throws IOException {
    seedTestType("E2E Tests", "JUnit Test Results Phase *");
    long testTypeId = testTypeId();

    stubArtifacts(
        artifact("JUnit Test Results Phase 1", "results.xml", xml("PhaseOneSuite")),
        artifact("JUnit Test Results Phase 2", "results.xml", xml("PhaseTwoSuite")));

    WorkflowRun run = workflowRunRepository.findById(RUN_ID).orElseThrow();
    processor.processRun(run);

    assertThat(run.getTestProcessingStatus()).isEqualTo(WorkflowRun.TestProcessingStatus.PROCESSED);
    // Both phased artifacts matched the single glob test type and persisted under it.
    assertThat(countTestSuitesForRun()).isEqualTo(2L);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT test_type_id) FROM test_suite WHERE workflow_run_id = 1",
                Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT test_type_id FROM test_suite WHERE workflow_run_id = 1 LIMIT 1",
                Long.class))
        .isEqualTo(testTypeId);
  }

  // --- helpers ---

  private void seedTestType(String name, String artifactName) {
    jdbc.update(
        "INSERT INTO test_type (name, artifact_name, workflow_id, repository_id) "
            + "VALUES (?, ?, 1, 1)",
        name,
        artifactName);
  }

  private long testTypeId() {
    return jdbc.queryForObject("SELECT id FROM test_type WHERE repository_id = 1", Long.class);
  }

  private long countTestSuitesForRun() {
    return jdbc.queryForObject(
        "SELECT count(*) FROM test_suite WHERE workflow_run_id = 1", Long.class);
  }

  private long countTestCases() {
    return jdbc.queryForObject("SELECT count(*) FROM test_case", Long.class);
  }

  private static String xml(String suiteName) {
    return String.format(VALID_JUNIT_XML, suiteName);
  }

  /** A mocked artifact whose name matches nothing of interest; its content is never downloaded. */
  private static GHArtifact nonMatchingArtifact(String name) {
    GHArtifact artifact = mock(GHArtifact.class);
    when(artifact.getName()).thenReturn(name);
    return artifact;
  }

  /** A mocked artifact whose {@code download(...)} replays a real ZIP containing one XML entry. */
  private static GHArtifact artifact(String name, String entryName, String xmlContent)
      throws IOException {
    byte[] zip = zip(entryName, xmlContent);
    GHArtifact artifact = mock(GHArtifact.class);
    when(artifact.getName()).thenReturn(name);
    when(artifact.download(any()))
        .thenAnswer(
            invocation -> {
              InputStreamFunction<List<TestSuite>> fn = invocation.getArgument(0);
              return fn.apply(new ByteArrayInputStream(zip));
            });
    return artifact;
  }

  @SuppressWarnings("unchecked")
  private void stubArtifacts(GHArtifact... artifacts) throws IOException {
    PagedIterable<GHArtifact> iterable = mock(PagedIterable.class);
    // Return a fresh iterator on each call so the result is re-iterable.
    when(iterable.iterator())
        .thenAnswer(
            invocation -> {
              Iterator<GHArtifact> source = Arrays.asList(artifacts).iterator();
              PagedIterator<GHArtifact> it = mock(PagedIterator.class);
              when(it.hasNext()).thenAnswer(i -> source.hasNext());
              when(it.next()).thenAnswer(i -> source.next());
              return it;
            });
    when(gitHubService.getWorkflowRunArtifacts(anyLong(), anyLong())).thenReturn(iterable);
  }

  private static byte[] zip(String entryName, String content) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
      zos.putNextEntry(new ZipEntry(entryName));
      zos.write(content.getBytes());
      zos.closeEntry();
    }
    return baos.toByteArray();
  }
}
