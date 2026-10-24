package de.tum.cit.aet.helios.deployment.approval;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.tum.cit.aet.helios.auth.AuthService;
import de.tum.cit.aet.helios.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies that {@link GlobalExceptionHandler} lets a deliberate {@link ResponseStatusException}
 * keep its status and reason on the way to the client, instead of the generic {@code Exception}
 * handler flattening it to a 500 "internal server error". This is what makes an approval that
 * GitHub rejects (e.g. an expired token → 502) show the reviewer a real, actionable message.
 */
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
@ContextConfiguration(classes = DeploymentApprovalController.class)
@WebMvcTest(DeploymentApprovalController.class)
class DeploymentApprovalControllerErrorHandlingTest {

  private static final String EXPIRED_AUTH_REASON =
      "GitHub rejected the request because your GitHub authorization has expired. "
          + "Please sign out of Helios and sign in again, then retry.";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DeploymentReviewActionService reviewActionService;
  @MockitoBean private DeploymentApprovalRequestRepository approvalRequestRepository;
  @MockitoBean private AuthService authService;

  @Test
  void badGatewayFromServiceReachesClientWithStatusAndReason() throws Exception {
    when(reviewActionService.approveAsCurrentUser(anyLong(), any()))
        .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, EXPIRED_AUTH_REASON));

    mockMvc
        .perform(post("/api/deployments/{deploymentId}/approve", 9436L))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.status").value(502))
        .andExpect(jsonPath("$.message").value(EXPIRED_AUTH_REASON));
  }

  @Test
  void unexpectedExceptionStillFallsBackToInternalServerError() throws Exception {
    when(reviewActionService.approveAsCurrentUser(anyLong(), any()))
        .thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(post("/api/deployments/{deploymentId}/approve", 9436L))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.message").value("Error: An internal server error occurred"));
  }
}
