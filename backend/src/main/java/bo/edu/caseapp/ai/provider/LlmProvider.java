package bo.edu.caseapp.ai.provider;

import java.util.List;

public interface LlmProvider {
  String getProviderName();
  String getModelName();
  boolean isConfigured();
  LlmResponse chat(List<LlmMessage> messages, Integer maxTokens, Double temperature);
  LlmResponse analyzeImage(String prompt, String imageBase64, String mimeType, Integer maxTokens);
}
