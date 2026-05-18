package de.tum.cit.aet.helios.workflow.queue.web;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.workflow.queue.Runner;
import de.tum.cit.aet.helios.workflow.queue.RunnerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@ContextConfiguration(classes = RunnerController.class)
@WebMvcTest(RunnerController.class)
class RunnerControllerTest {

  @Autowired MockMvc mockMvc;
  @MockitoBean RunnerRepository runnerRepository;

  private Runner runner(Long id, Runner.Status status) {
    Runner r = new Runner();
    r.setId(id);
    r.setName("runner-" + id);
    r.setStatus(status);
    r.setLabels(List.of("self-hosted", "linux"));
    return r;
  }

  @Test
  void listReturnsAllRunnersForAuthenticatedUser() throws Exception {
    when(runnerRepository.findAll())
        .thenReturn(List.of(runner(1L, Runner.Status.ONLINE), runner(2L, Runner.Status.OFFLINE)));

    mockMvc.perform(get("/api/runners").with(user("alice")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
  }

  @Test
  void byIdReturns404WhenUnknown() throws Exception {
    when(runnerRepository.findById(999L)).thenReturn(Optional.empty());
    mockMvc.perform(get("/api/runners/999").with(user("alice")))
        .andExpect(status().isNotFound());
  }

  @Test
  void poolsAggregatesByLabelSet() throws Exception {
    Runner a = runner(1L, Runner.Status.ONLINE);
    Runner b = runner(2L, Runner.Status.ONLINE);
    b.setBusy(true);
    Runner c = runner(3L, Runner.Status.OFFLINE);
    when(runnerRepository.findAll()).thenReturn(List.of(a, b, c));

    mockMvc.perform(get("/api/runners/pools").with(user("alice")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].online").value(2))
        .andExpect(jsonPath("$[0].busy").value(1))
        .andExpect(jsonPath("$[0].idle").value(1))
        .andExpect(jsonPath("$[0].offline").value(1));
  }
}
