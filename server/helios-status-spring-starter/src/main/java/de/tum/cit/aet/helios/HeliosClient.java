package de.tum.cit.aet.helios;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.tum.cit.aet.helios.status.LifecycleState;
import de.tum.cit.aet.helios.status.PushStatusPayload;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends lifecycle events from the Spring Boot service to every Helios endpoint
 * configured in {@link HeliosStatusProperties}.
 *
 * <p>Each call is <em>fire‑and‑forget</em>; you get a {@code Mono<Void>} in case
 * you want to hook in reactive error handling or wait for completion.</p>
 */
public class HeliosClient {

  private static final Logger log = LoggerFactory.getLogger(HeliosClient.class);

  private static final MediaType JSON = MediaType.get("application/json");

  // Custom thread pool: 1 thread, 5 task queue, named thread, drops on overflow
  private static final ExecutorService executor = new ThreadPoolExecutor(
      1, 1,
      0L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(5),
      r -> {
        Thread t = new Thread(r);
        t.setName("Helios-OkHttp");
        t.setDaemon(true);
        return t;
      },
      (r, ex) -> log.warn("❌ Helios push queue is full. Dropping status update.")
  );

  private static final OkHttpClient client = new OkHttpClient.Builder()
      .dispatcher(new Dispatcher(executor))
      .build();

  private static final ObjectMapper mapper = new ObjectMapper()
      .registerModule(new JavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private final List<HeliosEndpoint> endpoints;
  private final String environment;

  public HeliosClient(HeliosStatusProperties cfg) {
    this.environment = cfg.environmentName();
    this.endpoints = cfg.endpoints();
  }

  /* ------------------------------------------------------------------ */
  /*  Public API                                                        */
  /* ------------------------------------------------------------------ */

  /**
   * Push a lifecycle change without extra details.
   */
  public void push(LifecycleState state) {
    push(state, Map.of());
  }

  /**
   * Push a lifecycle change with an optional details map (may be empty).
   */
  public void push(LifecycleState state, Map<String, Object> details) {
    PushStatusPayload payload = PushStatusPayload.of(environment, state, details);
    sendToAllTargets(payload);
  }

  /* ------------------------------------------------------------------ */
  /*  Internal helpers                                                  */
  /* ------------------------------------------------------------------ */

  private void sendToAllTargets(PushStatusPayload payload) {
    for (HeliosEndpoint ep : endpoints) {
      try {
        String json = mapper.writeValueAsString(payload);
        Request request = new Request.Builder()
            .url(ep.url().toString())
            .header("Authorization", "Secret " + ep.secretKey())
            .post(RequestBody.create(json, JSON))
            .build();

        client.newCall(request).enqueue(new Callback() {
          @Override
          public void onFailure(@NotNull Call call, @NotNull IOException e) {
            log.warn("Helios push failed to {}: {}", ep.url(), e.getMessage());
          }

          @Override
          public void onResponse(@NotNull Call call, @NotNull Response response) {
            log.debug("Helios push {} -> {} [{}]", payload.state().toString(), ep.url(),
                response.code());
            response.close();
          }
        });

      } catch (Exception e) {
        log.error("Failed to serialize Helios payload: {}", e.getMessage());
      }
    }
  }
}