package de.tum.cit.aet.helios.ai;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AiProviderRegistry {

  private final List<AiProvider> providers;

  public AiProviderRegistry(List<AiProvider> providers) {
    this.providers = List.copyOf(providers);
  }

  public AiProvider resolve(String configuredProviderId) {
    String normalized = normalize(configuredProviderId);
    return providers.stream()
        .filter(p -> p.providerId().equalsIgnoreCase(normalized))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unsupported AI provider '%s'.".formatted(configuredProviderId)));
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value.trim().toLowerCase(Locale.ROOT);
  }
}
