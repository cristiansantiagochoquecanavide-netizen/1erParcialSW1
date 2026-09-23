package bo.edu.caseapp.ai;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class AiRequests {
  private AiRequests() {}

  public record CommandRequest(@NotBlank String command, String source) {
    public String resolvedSource() {
      return source != null && !source.isBlank() ? source.trim().toUpperCase() : "TEXT";
    }
  }

  public record ChatRequest(@NotBlank String question) {}

  public record ReconstructImageRequest(
      @NotBlank String imageBase64,
      String filename,
      String promptHint,
      String geminiApiKey,
      List<ClientDetectedClass> clientDetectedClasses,
      List<ClientDetectedRelation> clientDetectedRelations
  ) {}

  public record ClientDetectedClass(
      String name,
      Double posX,
      Double posY,
      List<ClientDetectedAttr> attributes,
      List<ClientDetectedMethod> methods
  ) {}

  public record ClientDetectedAttr(
      String visibility,
      String name,
      String type
  ) {}

  public record ClientDetectedMethod(
      String visibility,
      String name,
      String returnType
  ) {}

  public record ClientDetectedRelation(
      String source,
      String target,
      String name,
      String tipo,
      String multSource,
      String multTarget
  ) {}
}
