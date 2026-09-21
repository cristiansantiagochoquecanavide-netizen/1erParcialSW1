package bo.edu.caseapp.ai;

import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class AiRequests {
  private AiRequests() {}

  public record CommandRequest(@NotBlank String command, String source) {
    public String resolvedSource() {
      return source != null && !source.isBlank() ? source.trim().toUpperCase() : "TEXT";
    }
  }

  public record ChatRequest(@NotBlank String question) {}
}
