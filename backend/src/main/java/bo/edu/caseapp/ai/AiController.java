package bo.edu.caseapp.ai;

import bo.edu.caseapp.ai.AiRequests.ChatRequest;
import bo.edu.caseapp.ai.AiRequests.CommandRequest;
import bo.edu.caseapp.ai.AiResponses.ChatResponse;
import bo.edu.caseapp.ai.AiResponses.CommandResponse;
import bo.edu.caseapp.ai.AiResponses.InteractionHistoryItem;
import bo.edu.caseapp.uml.UmlService;
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
  private final UmlService umlService;

  public AiController(AiAssistantService aiService, InteraccionIaRepository iaRepo, UmlService umlService) {
    this.aiService = aiService;
    this.iaRepo = iaRepo;
    this.umlService = umlService;
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

  @PostMapping({"/apply-image-model", "/reconstruct-image"})
  public AiResponses.ImageReconstructionResponse applyImageModel(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody AiRequests.ApplyImageModelRequest request
  ) {
    return aiService.applyImageModel(projectId, diagramId, auth.getName(), request);
  }

  @PostMapping("/analyze-image")
  public AiResponses.ImageAnalysisResponse analyzeImage(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody AiRequests.AnalyzeImageRequest request
  ) {
    return aiService.analyzeImage(projectId, diagramId, auth.getName(), request);
  }

  @GetMapping("/history")
  public List<InteractionHistoryItem> history(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    String email = auth.getName();
    umlService.requireDiagramAccess(projectId, diagramId, email);
    return iaRepo.findByDiagramaIdAndUsuarioEmailIgnoreCaseOrderByFechaDesc(diagramId, email)
        .stream()
        .map(i -> new InteractionHistoryItem(i.getId(), i.getUsuario().getId(), i.getUsuario().getName(),
            i.getTipoInteraccion(), i.getEntradaUsuario(), i.getRespuestaAgente(), i.getFecha()))
        .toList();
  }
}
