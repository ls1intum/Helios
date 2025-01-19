package de.tum.cit.aet.helios.github;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registry for custom message handlers.
 * The registry is used to get the handler for a specific event type.
 */
@Component
public class GitHubCustomMessageHandlerRegistry {

  private final Map<String, GitHubCustomMessageHandler<?>> handlerMap = new HashMap<>();

  public GitHubCustomMessageHandlerRegistry(GitHubCustomMessageHandler<?>[] customHandlers) {
    for (GitHubCustomMessageHandler<?> handler : customHandlers) {
      handlerMap.put(handler.getEventType().toLowerCase(), handler);
    }
  }

  public GitHubCustomMessageHandler<?> getHandler(String eventType) {
    return handlerMap.get(eventType.toLowerCase());
  }

  public List<String> getSupportedEvents() {
    return new ArrayList<>(handlerMap.keySet());
  }
}
