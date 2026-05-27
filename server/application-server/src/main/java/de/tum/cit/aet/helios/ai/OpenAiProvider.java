package de.tum.cit.aet.helios.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "helios.ai", name = "enabled", havingValue = "true")
@ConditionalOnProperty(
    prefix = "helios.ai.providers.openai",
    name = "enabled",
    havingValue = "true")
public class OpenAiProvider implements AiProvider {

  private final ObjectProvider<ChatModel> chatModelProvider;

  public OpenAiProvider(@Qualifier("openAiChatModel") ObjectProvider<ChatModel> chatModelProvider) {
    this.chatModelProvider = chatModelProvider;
  }

  @Override
  public String providerId() {
    return "openai";
  }

  @Override
  public <T> T call(String systemPrompt, String userPrompt, Class<T> responseType) {
    ChatModel chatModel = chatModelProvider.getIfAvailable();
    if (chatModel == null) {
      throw new IllegalStateException("AI provider is not configured on the server.");
    }

    T result =
        ChatClient.create(chatModel)
            .prompt()
            .system(systemPrompt)
            .user(userPrompt)
            .call()
            .entity(responseType);

    if (result == null) {
      throw new IllegalStateException("AI provider returned null after structured output parsing.");
    }
    return result;
  }
}
