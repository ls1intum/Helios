package de.tum.cit.aet.helios;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.cit.aet.helios.status.LifecycleState;
import de.tum.cit.aet.helios.status.PushStatusPayload;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.StringUtils;

/**
 * The {@code HeliosClient} is a Spring-managed component that provides asynchronous
 * and synchronous delivery of lifecycle status updates to configured Helios endpoints.
 * It is part of the Helios status reporting plugin and is automatically initialized
 * at application startup.
 *
 * <p>Helios integrates with the Spring Boot lifecycle and automatically emits status
 * events (e.g., {@code STARTING_UP}, {@code RUNNING}, {@code SHUTTING_DOWN}) based on
 * application context changes. In addition, this client can be {@code @Autowired}
 * into any Spring component and used manually to push custom lifecycle events or metadata
 * to the Helios backend.</p>
 *
 * <p><strong>Core Capabilities:</strong></p>
 * <ul>
 *   <li>Supports one or more remote Helios endpoints with per-endpoint secret keys</li>
 *   <li>Non-blocking, fire-and-forget delivery model using OkHttp with a single daemon thread</li>
 *   <li>Bounded queue (size 10) to prevent overload – oldest messages are dropped with a warning</li>
 *   <li>Synchronous delivery for critical events like shutdown or failure</li>
 *   <li>Logs failures without interrupting caller code</li>
 *   <li>Flushes pending updates on Spring shutdown or JVM exit via a shutdown hook</li>
 * </ul>
 *
 * <p><strong>Configuration:</strong></p>
 * <ul>
 *   <li>Enabled via {@link HeliosStatusProperties}</li>
 *   <li>Requires a non-empty list of endpoints and a defined environment name</li>
 * </ul>
 *
 * <p><strong>Manual Usage Example:</strong></p>
 * <pre>{@code
 * @Autowired
 * private HeliosClient heliosClient;
 *
 * public void onCustomHealthCheck() {
 *     heliosClient.pushStatusUpdate(LifecycleState.DEGRADED, Map.of("latency", 750));
 * }
 * }</pre>
 *
 * <p>This class is safe to use in production code paths and does not throw
 * exceptions on delivery failure. All networking is handled internally with proper logging.</p>
 *
 * @see LifecycleState
 * @see PushStatusPayload
 * @see HeliosStatusProperties
 */
public class HeliosClient implements DisposableBean {

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
      .connectTimeout(5, TimeUnit.SECONDS)    // fail fast if you can’t connect in 5s
      .writeTimeout(5, TimeUnit.SECONDS)      // max 5s to send request body
      .readTimeout(5, TimeUnit.SECONDS)       // max 5s to read response
      .callTimeout(10, TimeUnit.SECONDS)      // max 20s for the entire call
      .build();

  private static final ObjectMapper mapper = new ObjectMapper();

  /* --------------------------------------------------------------------------- */

  private final HeliosStatusProperties cfg;
  private final List<HeliosEndpoint> endpoints;
  private final String environment;

  /**
   * Constructs a new {@code HeliosClient} using the provided configuration.
   *
   * <p>Initializes the list of Helios endpoints and the environment name
   * from the configuration properties.
   * The client uses a shared static OkHttp instance configured
   * with a bounded, single-threaded executor
   * to ensure lightweight, non-blocking delivery.</p>
   *
   * @param cfg the Helios configuration properties
   *     containing environment name and target endpoints;
   *     must not be null.
   */
  public HeliosClient(HeliosStatusProperties cfg) {
    this.cfg = cfg;
    this.environment = cfg.environmentName();
    this.endpoints = cfg.endpoints();
  }

  /* ------------------------------------------------------------------ */
  /*  Public API                                                        */
  /* ------------------------------------------------------------------ */

  /**
   * Pushes a lifecycle state update indicating that the application is starting up.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#STARTING_UP}.</p>
   */
  public void pushStartingUp() {
    pushStatusUpdate(LifecycleState.STARTING_UP);
  }

  /**
   * Pushes a lifecycle state update indicating that the application is healthy.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#RUNNING}.</p>
   */
  public void pushRunning() {
    pushStatusUpdate(LifecycleState.RUNNING);
  }

  /**
   * Pushes a lifecycle state update indicating that the application is stopping.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#SHUTTING_DOWN}.</p>
   */
  public void pushShuttingDown() {
    pushStatusUpdate(LifecycleState.SHUTTING_DOWN);
  }

  /**
   * Pushes a lifecycle state update indicating that the application has failed.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#FAILED}.</p>
   */
  public void pushFailed() {
    pushStatusUpdate(LifecycleState.FAILED);
  }

  /**
   * Pushes a lifecycle state update indicating that a database migration has started.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#DB_MIGRATION_STARTED}.</p>
   */
  public void pushDbMigrationStarted() {
    pushStatusUpdate(LifecycleState.DB_MIGRATION_STARTED);
  }

  /**
   * Pushes a lifecycle state update indicating that a database migration has finished successfully.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#DB_MIGRATION_FINISHED}.</p>
   */
  public void pushDbMigrationFinished() {
    pushStatusUpdate(LifecycleState.DB_MIGRATION_FINISHED);
  }

  /**
   * Pushes a lifecycle state update indicating that a database migration has failed.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#DB_MIGRATION_FAILED}.</p>
   */
  public void pushDbMigrationFailed() {
    pushStatusUpdate(LifecycleState.DB_MIGRATION_FAILED);
  }

  /**
   * Pushes a lifecycle state update indicating that the application is degraded.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#DEGRADED}.</p>
   */
  public void pushDegraded() {
    pushStatusUpdate(LifecycleState.DEGRADED);
  }

  /**
   * Pushes a lifecycle state update indicating that the application has stopped.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState)} with the
   * state set to {@link LifecycleState#STOPPED}.</p>
   */
  public void pushStopped() {
    pushStatusUpdate(LifecycleState.STOPPED);
  }

  /**
   * Pushes a lifecycle state update to all configured Helios endpoints
   * without any additional details.
   *
   * <p>This is a convenience method that delegates to
   * {@link #pushStatusUpdate(LifecycleState, Map)} with an empty details map.</p>
   *
   * <p>All updates are dispatched asynchronously using a single-threaded
   * executor with a bounded queue.
   * If the queue is full, the oldest updates are dropped and a warning is logged.</p>
   *
   * @param state the lifecycle state to push
   *     (e.g., STARTING_UP, RUNNING, STOPPING); must not be null.
   * @see #pushStatusUpdate(LifecycleState, Map)
   */
  public void pushStatusUpdate(LifecycleState state) {
    pushStatusUpdate(state, Map.of());
  }

  /**
   * Pushes a lifecycle state update with additional optional details
   * to all configured Helios endpoints.
   *
   * <p>The payload is serialized as JSON and POSTed asynchronously
   * to each endpoint using their configured
   * URL and secret key. If the queue is full or if any endpoint is misconfigured,
   * a warning is logged.
   * This method never blocks and always fails silently (with logging).</p>
   *
   * @param state the lifecycle state to push (e.g., STARTING, HEALTHY, STOPPING);
   *     must not be null.
   * @param details a map of additional detail data
   *     (optional metadata such as version, region, etc.);
   *     must not be null (use {@link java.util.Collections#emptyMap()} if not needed).
   */
  public void pushStatusUpdate(LifecycleState state, Map<String, Object> details) {
    // Check if push is enabled
    if (!cfg.enabled()) {
      log.debug("Helios push is disabled. Skipping push.");
      return;
    }

    // Check if endpoints are configured
    if (endpoints.isEmpty()) {
      log.warn("No Helios endpoints configured. Skipping push.");
      return;
    }

    if (environment == null || environment.isBlank()) {
      log.warn("Helios payload is missing environment. Skipping push.");
      return;
    }

    PushStatusPayload p = PushStatusPayload.of(environment, state, details);

    // DB_MIGRATION_FAILED, FAILED, SHUTTING_DOWN are "must deliver" states
    if (state == LifecycleState.DB_MIGRATION_FAILED
        || state == LifecycleState.FAILED
        || state == LifecycleState.SHUTTING_DOWN) {
      pushStatusUpdateSync(p);
    } else {
      pushStatusUpdateAsync(p);
    }
  }

  /* ------------------------------------------------------------------ */
  /*  Internal helpers                                                  */
  /* ------------------------------------------------------------------ */

  /**
   * Sends a given status payload to all configured Helios endpoints
   * via asynchronous HTTP POST requests.
   *
   * <p>This is a fire-and-forget method: all requests are dispatched
   * asynchronously using OkHttp’s dispatcher
   * backed by a single daemon thread and a bounded queue.
   * If the queue fills up, oldest entries are dropped.</p>
   *
   * <p>The following checks are performed before attempting delivery:
   * <ul>
   *   <li>The endpoint list must not be empty</li>
   *   <li>The payload must not be null</li>
   *   <li>The environment field in the payload must be non-blank</li>
   *   <li>Each endpoint must have a valid URL and a non-empty secret key</li>
   * </ul>
   * </p>
   *
   * <p>If a request fails or the endpoint responds with a non-2xx status,
   * a warning is logged. No retries are attempted.</p>
   *
   * @param payload the fully constructed payload to push;
   *     must not be null and must contain a valid environment field.
   */
  private void pushStatusUpdateAsync(PushStatusPayload payload) {
    // Check if payload is valid
    if (payload == null) {
      log.warn("Helios payload is null. Skipping push.");
      return;
    }

    for (int i = 0; i < endpoints.size(); i++) {
      final int idx = i;
      try {
        HeliosEndpoint ep = endpoints.get(i);

        // Validate the url and secretKey
        String url = Objects.toString(ep.url(), "");
        String secret = Objects.toString(ep.secretKey(), "");

        if (!StringUtils.hasText(url) || !StringUtils.hasText(secret)) {
          log.warn(
              "Helios endpoint #{} is missing URL or secret (url='{}', secretPresent={}). "
                  + "Skipping.",
              i,
              url,
              StringUtils.hasText(secret));
          continue;
        }

        String json = mapper.writeValueAsString(payload);
        Request request = new Request.Builder()
            .url(ep.url().toString())
            .header("Authorization", "Secret " + ep.secretKey())
            .post(RequestBody.create(json, JSON))
            .build();

        client.newCall(request).enqueue(new Callback() {
          @Override
          public void onFailure(@NotNull Call call, @NotNull IOException e) {
            log.warn("Helios push #{} to '{}' failed: {}. Payload={}",
                idx, url, e.getMessage(), payload, e);
          }

          @Override
          public void onResponse(@NotNull Call call, @NotNull Response response) {
            int code = response.code();
            boolean isSuccess = code >= 200 && code < 300;

            if (isSuccess) {
              log.debug("Helios push #{} '{}' [{}] – OK", idx, url, code);
            } else {
              log.warn("Helios push #{} '{}' responded [{} {}]. Payload={}",
                  idx, url, code, response.message(), payload);
            }

            response.close();
          }
        });

      } catch (Exception e) {
        log.error("Failed to serialize Helios payload: {}", e.getMessage());
      }
    }
  }

  private void pushStatusUpdateSync(PushStatusPayload payload) {
    // send every target *synchronously*
    for (HeliosEndpoint ep : endpoints) {
      try {
        if (!StringUtils.hasText(String.valueOf(ep.url()))
            || !StringUtils.hasText(ep.secretKey())) {
          log.warn("Helios endpoint '{}' missing URL/secret – skipping", ep.url());
          continue;
        }
        Request req = new Request.Builder()
            .url(ep.url().toString())
            .header("Authorization", "Secret " + ep.secretKey())
            .post(RequestBody.create(mapper.writeValueAsBytes(payload), JSON))
            .build();
        try (Response resp = client.newCall(req).execute()) {
          if (resp.isSuccessful()) {
            log.info("Helios sync push to '{}' [{}] – OK",
                ep.url(), resp.code());
          } else {
            log.warn("Helios sync push to '{}' [{} {}] FAILED",
                ep.url(), resp.code(), resp.message());
          }
        } catch (Exception ioe) {
          log.warn("Helios sync push to '{}' failed: {}", ep.url(), ioe.getMessage());
        }
      } catch (Exception e) {
        log.error("Failed to serialize Helios payload: {}", e.getMessage());
      }
    }
  }

  /**
   * Flush the queue and wait a bounded time.  Safe to call twice.
   */
  private static void flush() {
    Dispatcher dispatcher = client.dispatcher();
    dispatcher.executorService().shutdown();           // idempotent
    try {
      dispatcher.executorService().awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /* ------------------------------------------------------------------ */
  /* DisposableBean                                                     */
  /* ------------------------------------------------------------------ */
  @Override
  public void destroy() {
    log.info("Destroying HeliosClient, flushing pending pushes.");
    flush();
  }

  /* ------------------------------------------------------------------ */
  /* JVM shutdown-hook                                                  */
  /* ------------------------------------------------------------------ */
  static {
    Runtime.getRuntime().addShutdownHook(
        new Thread(() -> {
          LoggerFactory.getLogger(HeliosClient.class)
              .info("Helios shutdown-hook: flushing pending pushes");
          flush();
        }, "Helios-ShutdownHook"));
  }
}
