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
      Authentication auth
  ) {
    return versionService.restoreVersion(projectId, diagramId, versionId, auth.getName());
  }
}
