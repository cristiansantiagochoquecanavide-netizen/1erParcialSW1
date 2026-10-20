package bo.edu.caseapp.ai.provider;

public record LlmResponse(
    String content,
    int promptTokens,
    int completionTokens,
    String model,
    boolean success,
    String errorMessage,
    Integer httpStatusCode
) {
  public static LlmResponse success(String content, int promptTokens, int completionTokens, String model) {
    return new LlmResponse(content, promptTokens, completionTokens, model, true, null, 200);
  }

  public static LlmResponse failure(String errorMessage, Integer httpStatusCode) {
    return new LlmResponse(null, 0, 0, null, false, errorMessage, httpStatusCode);
  }
}
