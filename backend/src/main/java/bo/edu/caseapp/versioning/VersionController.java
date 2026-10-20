package bo.edu.caseapp.versioning;

import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import bo.edu.caseapp.versioning.VersionRequests.CreateVersion;
import bo.edu.caseapp.versioning.VersionResponses.VersionSummary;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/diagrams/{diagramId}/versions")
public class VersionController {
  private final VersionService versionService;

  public VersionController(VersionService versionService) {
    this.versionService = versionService;
  }

  @GetMapping
  public List<VersionSummary> list(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    return versionService.listVersions(projectId, diagramId, auth.getName());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public VersionSummary create(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody(required = false) CreateVersion request
  ) {
    CreateVersion req = request != null ? request : new CreateVersion(null);
    return versionService.createVersion(projectId, diagramId, auth.getName(), req);
  }

  @PostMapping("/{versionId}/restore")
  public DiagramFullView restore(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      @PathVariable UUID versionId,
      Authentication auth,
      @RequestHeader(value = "X-Diagram-Revision", required = false) Long revisionHeader,
      @RequestHeader(value = "If-Match", required = false) String ifMatch,
      jakarta.servlet.http.HttpServletResponse response
  ) {
    Long expectedRev = requireExpectedRevision(revisionHeader, ifMatch);
    DiagramFullView view = versionService.restoreVersion(projectId, diagramId, versionId, auth.getName(), expectedRev);
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
