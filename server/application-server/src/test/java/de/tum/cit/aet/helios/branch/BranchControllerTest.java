package de.tum.cit.aet.helios.branch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.gitrepo.RepositoryInfoDto;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@AutoConfigureMockMvc
@ContextConfiguration(classes = BranchController.class)
@WebMvcTest(BranchController.class)
public class BranchControllerTest {
  @Autowired private MockMvc mockMvc;

  @MockitoBean private BranchService branchService;

  @Autowired private ObjectMapper objectMapper;

  private final List<BranchInfoDto> branches =
      List.of(
          new BranchInfoDto(
              "branch1",
              "sha1",
              0,
              0,
              false,
              false,
              null,
              null,
              new RepositoryInfoDto(1L, "repo", "repo", null, "url")),
          new BranchInfoDto("branch2", "sha2", 0, 0, false, false, null, null, null));

  @Test
  void testRejectUnauthenticatedUser() throws Exception {
    this.mockMvc
        .perform(get("/api/branches").accept(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isUnauthorized());
  }

  @Test
  void testGetAllBranches() throws Exception {
    when(branchService.getAllBranches()).thenReturn(branches);
    ResultActions request =
        this.mockMvc
            .perform(get("/api/branches").with(user("user")))
            .andExpect(MockMvcResultMatchers.status().isOk());

    assertArrayEquals(
        objectMapper.readValue(
            request.andReturn().getResponse().getContentAsString(), BranchInfoDto[].class),
        this.branches.toArray());
  }

  @Test
  void testGetBranchByExistingId() throws Exception {
    Long id = this.branches.get(0).repository().id();
    String branchName = this.branches.get(0).name();

    when(branchService.getBranchInfo(id, branchName)).thenReturn(Optional.of(branches.get(0)));
    ResultActions request =
        this.mockMvc
            .perform(
                get("/api/branches/repository/{repoId}/branch", id)
                    .param("name", branchName)
                    .with(user("user"))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(MockMvcResultMatchers.status().isOk());

    assertEquals(
        objectMapper.readValue(
            request.andReturn().getResponse().getContentAsString(), BranchDetailsDto.class),
        this.branches.get(0));
  }

  @Test
  void testGetBranchByNonExistingRepoId() throws Exception {
    Long id = -1L;
    String branchName = this.branches.get(0).name();

    when(branchService.getBranchInfo(id, branchName)).thenReturn(Optional.empty());
    this.mockMvc
        .perform(
            get("/api/branches/repository/{repoId}/branch", id)
                .param("name", branchName)
                .with(user("user"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isNotFound());
  }

  @Test
  void testGetBranchByNonExistingBranchName() throws Exception {
    Long id = this.branches.get(0).repository().id();
    String branchName = "invalid";

    when(branchService.getBranchInfo(id, branchName)).thenReturn(Optional.empty());
    this.mockMvc
        .perform(
            get("/api/branches/repository/{repoId}/branch", id)
                .param("name", branchName)
                .with(user("user"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(MockMvcResultMatchers.status().isNotFound());
  }
}
