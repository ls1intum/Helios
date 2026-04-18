package de.tum.cit.aet.helios.ai;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "helios.ai")
public class AiProperties {

  private boolean enabled;
  private String defaultProvider;
  private Map<String, ProviderProperties> providers = new LinkedHashMap<>();
  private TestFailureProperties testFailure = new TestFailureProperties();

  @Getter
  @Setter
  public static class ProviderProperties {
    private boolean enabled;
    private String model;
  }

  @Getter
  @Setter
  public static class TestFailureProperties {
    private int maxSectionChars;
  }
}
