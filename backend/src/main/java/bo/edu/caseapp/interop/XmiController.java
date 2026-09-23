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
      @RequestBody String xmlContent
  ) {
    return xmiService.importXmi(projectId, diagramId, auth.getName(), xmlContent);
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
      @RequestBody String jsonContent
  ) {
    return xmiService.importJson(projectId, diagramId, auth.getName(), jsonContent);
  }
}
