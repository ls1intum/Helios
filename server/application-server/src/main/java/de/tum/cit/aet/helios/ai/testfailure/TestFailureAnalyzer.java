package de.tum.cit.aet.helios.ai.testfailure;

import de.tum.cit.aet.helios.ai.AiProperties;
import de.tum.cit.aet.helios.ai.AiProvider;
import de.tum.cit.aet.helios.ai.AiProviderRegistry;
import de.tum.cit.aet.helios.ai.AiTextUtils;
import de.tum.cit.aet.helios.tests.TestCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TestFailureAnalyzer {
  private static final int ROOT_CAUSE_LIMIT = 3;
  private static final int EVIDENCE_LIMIT = 16;
  private static final int RECOMMENDED_FIX_LIMIT = 5;

  private final TestFailureContextAssembler contextAssembler;
  private final TestFailurePromptBuilder promptBuilder;
  private final AiProviderRegistry providerRegistry;

  public TestFailureAnalysisResultDto analyze(
      TestCase testCase, AiProperties properties, String providerId) {
    TestFailureContext context = contextAssembler.assemble(testCase, properties);

    String systemPrompt = promptBuilder.buildSystemPrompt();
    String userPrompt = promptBuilder.buildUserPrompt(context);

    AiProvider provider = providerRegistry.resolve(providerId);
    TestFailureAnalysisSchema schema =
        provider.call(systemPrompt, userPrompt, TestFailureAnalysisSchema.class);
    AiProperties.ProviderProperties providerProperties =
        properties.getProviders().get(provider.providerId());
    String model =
        providerProperties != null && StringUtils.hasText(providerProperties.getModel())
            ? providerProperties.getModel()
            : null;

    return new TestFailureAnalysisResultDto(
        schema.summary(),
        AiTextUtils.limitList(schema.rootCauseHypotheses(), ROOT_CAUSE_LIMIT),
        AiTextUtils.limitList(schema.evidence(), EVIDENCE_LIMIT),
        AiTextUtils.limitList(schema.recommendedFixes(), RECOMMENDED_FIX_LIMIT),
        schema.confidence(),
        provider.providerId(),
        model);
  }
}
