package bo.edu.caseapp.interop;

import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/diagrams/{diagramId}/xmi")
public class XmiController {
  private final XmiService xmiService;

  public XmiController(XmiService xmiService) {
    this.xmiService = xmiService;
  }

  @GetMapping("/export")
  public ResponseEntity<String> exportXmi(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    String xml = xmiService.exportXmi(projectId, diagramId, auth.getName());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diagrama.xmi\"")
        .contentType(MediaType.APPLICATION_XML)
        .body(xml);
  }

  @PostMapping(value = "/import", consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_PLAIN_VALUE, MediaType.TEXT_XML_VALUE})
  public DiagramFullView importXmi(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @RequestBody String xmlContent,
      @RequestHeader(value = "X-Diagram-Revision", required = false) Long revisionHeader,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      jakarta.servlet.http.HttpServletResponse response
  ) {
    Long expectedRev = requireExpectedRevision(revisionHeader, ifMatch);
    DiagramFullView view = xmiService.importXmi(projectId, diagramId, auth.getName(), xmlContent, expectedRev);
    if (view.diagram() != null) {
      response.setHeader("X-Diagram-Revision", String.valueOf(view.diagram().revision()));
    }
    return view;
  }

  @GetMapping("/export-json")
  public ResponseEntity<String> exportJson(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    String json = xmiService.exportJson(projectId, diagramId, auth.getName());
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diagrama.json\"")
        .contentType(MediaType.APPLICATION_JSON)
        .body(json);
  }

  @PostMapping(value = "/import-json", consumes = MediaType.APPLICATION_JSON_VALUE)
  public DiagramFullView importJson(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @RequestBody String jsonContent,
      @RequestHeader(value = "X-Diagram-Revision", required = false) Long revisionHeader,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      jakarta.servlet.http.HttpServletResponse response
  ) {
    Long expectedRev = requireExpectedRevision(revisionHeader, ifMatch);
    DiagramFullView view = xmiService.importJson(projectId, diagramId, auth.getName(), jsonContent, expectedRev);
    if (view.diagram() != null) {
      response.setHeader("X-Diagram-Revision", String.valueOf(view.diagram().revision()));
    }
    return view;
  }

  private Long requireExpectedRevision(Long headerRevision, String ifMatch) {
    if (headerRevision != null) return headerRevision;
    if (ifMatch != null && !ifMatch.isBlank()) {
      try {
        return Long.parseLong(ifMatch.replace("\"", "").trim());
      } catch (NumberFormatException ignored) {}
    }
    throw new bo.edu.caseapp.error.BadRequestException(
        "Se requiere la revisión esperada del diagrama ('X-Diagram-Revision' o 'If-Match') para verificar la concurrencia optimista.");
  }
}
