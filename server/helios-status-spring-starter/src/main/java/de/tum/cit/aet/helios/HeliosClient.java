package de.tum.cit.aet.helios;

import de.tum.cit.aet.helios.status.LifecycleState;
import de.tum.cit.aet.helios.status.PushStatusPayload;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Sends lifecycle events from the Spring Boot service to every Helios endpoint
 * configured in {@link HeliosStatusProperties}.
 *
 * <p>Each call is <em>fire‑and‑forget</em>; you get a {@code Mono<Void>} in case
 * you want to hook in reactive error handling or wait for completion.</p>
 */
public class HeliosClient {

  private static final Logger log = LoggerFactory.getLogger(HeliosClient.class);

  private final List<WebClient> targets;
  private final String          environment;

  public HeliosClient(HeliosStatusProperties cfg) {
    this.environment = cfg.environment();

    this.targets = cfg.urls().stream()
        .map(uri -> WebClient.builder()
            .baseUrl(uri.toString())
            .defaultHeader("Authorization", "Secret " + cfg.secretKey())
            .build())
        .toList();
  }

  /* ------------------------------------------------------------------ */
  /*  Public API                                                        */
  /* ------------------------------------------------------------------ */

  /**
   * Push a lifecycle change without extra details.
   */
  public Mono<Void> push(LifecycleState state) {
    return push(state, Map.of());
  }

  /**
   * Push a lifecycle change with an optional details map (may be empty).
   */
  public Mono<Void> push(LifecycleState state, Map<String, Object> details) {
    var payload = PushStatusPayload.of(environment, state, details);
    return sendToAllTargets(payload);
  }

  /* ------------------------------------------------------------------ */
  /*  Internal helpers                                                  */
  /* ------------------------------------------------------------------ */

  private Mono<Void> sendToAllTargets(PushStatusPayload payload) {
    return Flux.fromIterable(targets)
        .flatMap(client -> client.post()
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(payload)
            .retrieve()
            .toBodilessEntity()
            .doOnSuccess(r -> log.debug("Sent {} to {}", payload.state(), client))
            .doOnError(e -> log.warn("Helios push failed for {}: {}", client, e.getMessage())))
        .then();                 // completes when all requests finish (or fail)
  }
}