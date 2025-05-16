package de.tum.cit.aet.helios;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Thin wrapper around OkHttp that asynchronously POSTs a JSON payload to
 * <em>every</em> configured Helios endpoint.
 *
 * <p>Concurrency model:</p>
 * <ul>
 *   <li>Single daemon thread (won’t block JVM shutdown).</li>
 *   <li>Bounded queue – if the service spams updates faster than the
 *       network can deliver, the oldest messages are dropped with a WARN.</li>
 *   <li>Caller never blocks; failures are only logged.</li>
 * </ul>
 */
public class HeliosClient {

  private static final Logger log = LoggerFactory.getLogger(HeliosClient.class);

  private static final MediaType JSON = MediaType.get("application/json");

  /* -------- okhttp dispatcher ------------------------------------------------ */
  private static final ExecutorService executor = new ThreadPoolExecutor(
      1, 1,
      0L, TimeUnit.MILLISECONDS,
      new LinkedBlockingQueue<>(10),
      r -> {
        Thread t = new Thread(r);
        t.setName("Helios-OkHttp");
        t.setDaemon(true);
        return t;
      },
      (r, ex) -> log.warn("Helios push queue is full. Dropping status update.")
  );

  private static final OkHttpClient client = new OkHttpClient.Builder()
      .dispatcher(new Dispatcher(executor))
      .build();

  private static final ObjectMapper mapper = new ObjectMapper();

  /* --------------------------------------------------------------------------- */

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
   * Push a lifecycle state without extra detail data.
   */
  public void push(LifecycleState state) {
    push(state, Map.of());
  }

  /**
   * Push a lifecycle state and optional details.
   */
  public void push(LifecycleState state, Map<String, Object> details) {
    PushStatusPayload payload = PushStatusPayload.of(environment, state, details);
    sendToAllTargets(payload);
  }

  /* ------------------------------------------------------------------ */
  /*  Internal helpers                                                  */
  /* ------------------------------------------------------------------ */

  /**
   * Fire-and-forget POST to every configured target.
   */
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
            log.warn("Helios push failed to {}, Error: {}, Payload: {}", ep.url(), e.getMessage(),
                payload);
          }

          @Override
          public void onResponse(@NotNull Call call, @NotNull Response response) {
            int code = response.code();
            boolean isSuccess = code >= 200 && code < 300;

            if (isSuccess) {
              log.debug("Helios push {} -> {} [{}]", payload.state(), ep.url(), code);
            } else {
              log.warn("Helios push to {} responded with error [{}]: {}, Payload: {}",
                  ep.url(), code, response.message(), payload);
            }

            response.close();
          }
        });

      } catch (Exception e) {
        log.error("Failed to serialize Helios payload: {}", e.getMessage());
      }
    }
  }
}