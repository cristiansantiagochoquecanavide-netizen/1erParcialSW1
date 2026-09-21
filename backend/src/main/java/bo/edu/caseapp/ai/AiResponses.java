package bo.edu.caseapp.ai;

import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import java.time.Instant;
import java.util.UUID;

public final class AiResponses {
  private AiResponses() {}

  public record CommandResponse(
      boolean success,
      String actionType,
      String message,
      DiagramFullView updatedDiagram
  ) {}

  public record ChatResponse(
      String answer,
      String suggestion
  ) {}

  public record ImageReconstructionResponse(
      boolean success,
      String message,
      int classesDetected,
      int relationsDetected,
      DiagramFullView updatedDiagram
  ) {}

  public record InteractionHistoryItem(
      UUID id,
      String tipoInteraccion,
      String entradaUsuario,
      String respuestaAgente,
      Instant fecha
  ) {}
}
