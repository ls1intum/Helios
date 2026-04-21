package de.tum.cit.aet.helios.ai;

public interface AiProvider {

  String providerId();

  /**
   * Calls the AI provider and deserializes the response into the given type. Implementations are
   * expected to use structured output (e.g. Spring AI {@code .entity()}) so that callers receive a
   * fully parsed object rather than raw text.
   */
  <T> T call(String systemPrompt, String userPrompt, Class<T> responseType);
}
