package bo.edu.caseapp.ai;

import bo.edu.caseapp.ai.AiRequests.ChatRequest;
import bo.edu.caseapp.ai.AiRequests.CommandRequest;
import bo.edu.caseapp.ai.AiResponses.ChatResponse;
import bo.edu.caseapp.ai.AiResponses.CommandResponse;
import bo.edu.caseapp.ai.AiResponses.InteractionHistoryItem;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/diagrams/{diagramId}/ai")
public class AiController {
  private final AiAssistantService aiService;
  private final InteraccionIaRepository iaRepo;

  public AiController(AiAssistantService aiService, InteraccionIaRepository iaRepo) {
    this.aiService = aiService;
    this.iaRepo = iaRepo;
  }

  @PostMapping("/command")
  public CommandResponse command(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody CommandRequest request
  ) {
    return aiService.executeCommand(projectId, diagramId, auth.getName(), request);
  }

  @PostMapping("/chat")
  public ChatResponse chat(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody ChatRequest request
  ) {
    return aiService.chat(projectId, diagramId, auth.getName(), request);
  }

  @PostMapping("/reconstruct-image")
  public AiResponses.ImageReconstructionResponse reconstructFromImage(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody AiRequests.ReconstructImageRequest request
  ) {
    return aiService.reconstructFromImage(projectId, diagramId, auth.getName(), request);
  }

  @GetMapping("/history")
  public List<InteractionHistoryItem> history(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    return iaRepo.findByDiagramaIdOrderByFechaDesc(diagramId)
        .stream()
        .map(i -> new InteractionHistoryItem(i.getId(), i.getTipoInteraccion(), i.getEntradaUsuario(), i.getRespuestaAgente(), i.getFecha()))
        .toList();
  }
}
