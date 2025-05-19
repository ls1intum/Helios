package de.tum.cit.aet.helios.status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable payload DTO used to send status updates to Helios.
 *
 * <p>This record represents the expected schema of a status update JSON object sent
 * to the Helios ingestion API. The payload includes environment metadata, a lifecycle
 * state, a timestamp, and optional extra details.</p>
 *
 * @param environment name of the GitHub Actions environment
 * @param state lifecycle state of the application
 * @param timestamp UNIX epoch timestamp in seconds (as a string)
 * @param details optional key-value map with metadata (e.g., version, region)
 */
public record PushStatusPayload(
    @NotBlank String environment,
    @NotNull LifecycleState state,
    @NotBlank String timestamp,
    Map<String, Object> details) {
  /**
   * Factory method that creates a new {@code PushStatusPayload} with the current timestamp.
   *
   * @param env environment name (must not be null or blank)
   * @param state current application lifecycle state
   * @param details optional metadata
   * @return a new payload instance ready to be sent
   */
  public static PushStatusPayload of(String env,
                                     LifecycleState state,
                                     Map<String, Object> details) {
    return new PushStatusPayload(env, state, String.valueOf(Instant.now().getEpochSecond()),
        details);
  }
}
