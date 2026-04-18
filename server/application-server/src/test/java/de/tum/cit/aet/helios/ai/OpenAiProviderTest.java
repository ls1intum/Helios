package de.tum.cit.aet.helios.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class OpenAiProviderTest {

  @Mock private ObjectProvider<ChatModel> chatModelProvider;

  @Test
  void callThrowsHelpfulErrorWhenChatModelIsUnavailable() {
    OpenAiProvider provider = new OpenAiProvider(chatModelProvider);
    when(chatModelProvider.getIfAvailable()).thenReturn(null);

    IllegalStateException exception =
        assertThrows(
            IllegalStateException.class,
            () -> provider.call("system", "user", Object.class));

    assertEquals("AI provider is not configured on the server.", exception.getMessage());
  }
}
