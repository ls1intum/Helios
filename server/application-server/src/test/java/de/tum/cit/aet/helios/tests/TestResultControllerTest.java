package de.tum.cit.aet.helios.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.filters.RepositoryContext;
import de.tum.cit.aet.helios.gitrepo.GitRepository;
import de.tum.cit.aet.helios.gitreposettings.GitRepoSettings;
import de.tum.cit.aet.helios.tests.pagination.FlakyTestsPageRequest;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = TestResultController.class)
@WebMvcTest(TestResultController.class)
class TestResultControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private TestResultService testResultService;

  @MockitoBean private TestCaseStatisticsService testCaseStatisticsService;

  @Autowired private ObjectMapper objectMapper;

  private GitRepoSettings repoSettings;
  private GitRepository gitRepository;

  @BeforeEach
  void setUp() {
    gitRepository = new GitRepository();
    gitRepository.setRepositoryId(1L);

    repoSettings = new GitRepoSettings();
    repoSettings.setRepository(gitRepository);

    RepositoryContext.setRepositoryId("1");
  }

  @AfterEach
  void tearDown() {
    RepositoryContext.clear();
  }

  @Test
  void getFlakinessScores_withValidRequest_returnsScores() throws Exception {
    var identifier = new TestFlakinessScoreRequest.TestCaseIdentifier("test1", "Class1", "Suite1");
    var request = new TestFlakinessScoreRequest(List.of(identifier));

    var expectedDto = new TestFlakinessScoreDto("test1", "Class1", "Suite1", 63.0, 0.05, 0.0);

    when(testCaseStatisticsService.getFlakinessScoresForTests(eq(1L), anyList()))
        .thenReturn(List.of(expectedDto));

    String response =
        mockMvc
            .perform(
                post("/api/tests/flakiness-scores")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
                    .requestAttr("repository", repoSettings))
            .andExpect(MockMvcResultMatchers.status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    var expectedJson = objectMapper.writeValueAsString(List.of(expectedDto));
    assertEquals(expectedJson, response);
  }

  @Test
  void getFlakinessScores_withMultipleTests_returnsAllScores() throws Exception {
    var identifiers =
        List.of(
            new TestFlakinessScoreRequest.TestCaseIdentifier("test1", "Class1", "Suite1"),
            new TestFlakinessScoreRequest.TestCaseIdentifier("test2", "Class2", "Suite1"));
    var request = new TestFlakinessScoreRequest(identifiers);

    var dto1 = new TestFlakinessScoreDto("test1", "Class1", "Suite1", 63.0, 0.05, 0.0);
    var dto2 = new TestFlakinessScoreDto("test2", "Class2", "Suite1", 0.0, 0.0, 0.0);

    when(testCaseStatisticsService.getFlakinessScoresForTests(eq(1L), anyList()))
        .thenReturn(List.of(dto1, dto2));

    mockMvc
        .perform(
            post("/api/tests/flakiness-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("repository", repoSettings))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
        .andExpect(MockMvcResultMatchers.jsonPath("$[0].testName").value("test1"))
        .andExpect(MockMvcResultMatchers.jsonPath("$[0].flakinessScore").value(63.0))
        .andExpect(MockMvcResultMatchers.jsonPath("$[1].testName").value("test2"))
        .andExpect(MockMvcResultMatchers.jsonPath("$[1].flakinessScore").value(0.0));
  }

  @Test
  void getFlakinessScores_withEmptyTestCases_returnsBadRequest() throws Exception {
    var request = new TestFlakinessScoreRequest(Collections.emptyList());

    mockMvc
        .perform(
            post("/api/tests/flakiness-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("repository", repoSettings))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  void getFlakinessScores_withBlankTestName_returnsBadRequest() throws Exception {
    String json = "{\"testCases\":[{\"testName\":\"\",\"className\":\"Class1\"}]}";

    mockMvc
        .perform(
            post("/api/tests/flakiness-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .requestAttr("repository", repoSettings))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  void getFlakinessScores_withBlankClassName_returnsBadRequest() throws Exception {
    String json = "{\"testCases\":[{\"testName\":\"test1\",\"className\":\"\"}]}";

    mockMvc
        .perform(
            post("/api/tests/flakiness-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .requestAttr("repository", repoSettings))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  void getFlakinessScores_withMissingBody_returnsBadRequest() throws Exception {
    mockMvc
        .perform(
            post("/api/tests/flakiness-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .requestAttr("repository", repoSettings))
        .andExpect(MockMvcResultMatchers.status().isBadRequest());
  }

  @Test
  void getFlakinessScores_withNoMatchingTests_returnsZeroScores() throws Exception {
    var identifier = new TestFlakinessScoreRequest.TestCaseIdentifier(
        "unknownTest", "Unknown", "Unknown");
    var request = new TestFlakinessScoreRequest(List.of(identifier));

    var zeroDto = new TestFlakinessScoreDto("unknownTest", "Unknown", "Unknown", 0.0, 0.0, 0.0);

    when(testCaseStatisticsService.getFlakinessScoresForTests(eq(1L), anyList()))
        .thenReturn(List.of(zeroDto));

    mockMvc
        .perform(
            post("/api/tests/flakiness-scores")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
                .requestAttr("repository", repoSettings))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$[0].testName").value("unknownTest"))
        .andExpect(MockMvcResultMatchers.jsonPath("$[0].flakinessScore").value(0.0))
        .andExpect(MockMvcResultMatchers.jsonPath("$[0].defaultBranchFailureRate").value(0.0))
        .andExpect(MockMvcResultMatchers.jsonPath("$[0].combinedFailureRate").value(0.0));
  }

  @Test
  void getFlakyTestsOverview_returnsOverview() throws Exception {
    var summary = new FlakyTestOverviewDto.FlakyTestSummary(10, 2, 1, 1, 0);
    var flakyTest =
        new FlakyTestOverviewDto.FlakyTestDto(
            "testFlaky",
            "FlakyTest",
            "UnitTests",
            85.0,
            0.03,
            0.05,
            OffsetDateTime.now());
    var expected = new FlakyTestOverviewDto(summary, List.of(flakyTest), 2);

    when(testCaseStatisticsService.getFlakyTestsOverview(
            eq(1L), any(FlakyTestsPageRequest.class)))
        .thenReturn(expected);

    mockMvc
        .perform(get("/api/tests/flaky"))
        .andExpect(MockMvcResultMatchers.status().isOk())
        .andExpect(MockMvcResultMatchers.jsonPath("$.summary.totalTrackedTests").value(10))
        .andExpect(MockMvcResultMatchers.jsonPath("$.summary.flakyTestCount").value(2))
        .andExpect(MockMvcResultMatchers.jsonPath("$.flakyTests.length()").value(1))
        .andExpect(MockMvcResultMatchers.jsonPath("$.flakyTests[0].testName").value("testFlaky"))
        .andExpect(MockMvcResultMatchers.jsonPath("$.flakyTests[0].flakinessScore").value(85.0));
  }
}
