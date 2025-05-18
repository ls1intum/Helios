package de.tum.cit.aet.helios.status;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Map;

/**
 * Immutable DTO sent to Helios. The record aligns with the public JSON schema
 * accepted by the Helios ingestion API.
 *
 * @param environment GitHub environment name (e.g. artemis-test3.artemis.cit.tum.de)
 * @param state lifecycle state, see {@link LifecycleState}
 * @param timestamp epoch-seconds in string format
 * @param details optional, additional String:String JSON data map
 */
public record PushStatusPayload(
    @NotBlank String environment,
    @NotNull LifecycleState state,
    @NotBlank String timestamp,
    Map<String, Object> details) {
  /**
   * Convenience factory that stamps {@code Instant.now()}.
   */
  public static PushStatusPayload of(String env,
                                     LifecycleState state,
                                     Map<String, Object> details) {
    return new PushStatusPayload(env, state, String.valueOf(Instant.now().getEpochSecond()),
        details);
  }
}
