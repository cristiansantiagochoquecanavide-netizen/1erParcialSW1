package bo.edu.caseapp.uml;

import bo.edu.caseapp.uml.UmlRequests.*;
import bo.edu.caseapp.uml.UmlResponses.*;
import bo.edu.caseapp.uml.relational.RelationalSchema;
import bo.edu.caseapp.uml.relational.SqlDdlParserService;
import bo.edu.caseapp.uml.validation.ValidationReport;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/diagrams")
public class UmlController {
  private final UmlService service;
  private final SqlDdlParserService sqlDdlParserService;

  public UmlController(UmlService service, SqlDdlParserService sqlDdlParserService) {
    this.service = service;
    this.sqlDdlParserService = sqlDdlParserService;
  }

  public record ImportSqlRequest(String sql) {}

  @PostMapping("/{diagramId}/import/sql")
  @ResponseStatus(HttpStatus.OK)
  public DiagramFullView importSql(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @RequestBody ImportSqlRequest request
  ) {
    return sqlDdlParserService.parseAndImportSql(projectId, diagramId, auth.getName(), request.sql());
  }

  @GetMapping
  public List<DiagramSummary> list(@PathVariable UUID projectId, Authentication auth) {
    return service.listDiagrams(projectId, auth.getName());
  }

  @GetMapping("/default")
  public DiagramSummary getOrCreateDefault(@PathVariable UUID projectId, Authentication auth) {
    return service.getOrCreateDefaultDiagram(projectId, auth.getName());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DiagramSummary create(
      @PathVariable UUID projectId,
      Authentication auth,
      @Valid @RequestBody CreateDiagram request
  ) {
    return service.createDiagram(projectId, auth.getName(), request);
  }

  @GetMapping("/{diagramId}")
  public DiagramFullView get(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    return service.getDiagram(projectId, diagramId, auth.getName());
  }

  @GetMapping("/{diagramId}/validate")
  public ValidationReport validate(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    return service.validateDiagram(projectId, diagramId, auth.getName());
  }

  @GetMapping("/{diagramId}/relational")
  public RelationalSchema getRelational(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    return service.generateRelationalSchema(projectId, diagramId, auth.getName());
  }

  @PutMapping("/{diagramId}")
  public DiagramSummary update(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody UpdateDiagram request
  ) {
    return service.updateDiagram(projectId, diagramId, auth.getName(), request);
  }

  @DeleteMapping("/{diagramId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth
  ) {
    service.deleteDiagram(projectId, diagramId, auth.getName());
  }

  @PostMapping("/{diagramId}/elements")
  @ResponseStatus(HttpStatus.OK)
  public ElementDetail saveElement(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody SaveElement request
  ) {
    return service.createElement(projectId, diagramId, auth.getName(), request);
  }

  @PutMapping("/{diagramId}/elements/{elementId}")
  public ElementDetail updateElement(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      @PathVariable UUID elementId,
      Authentication auth,
      @Valid @RequestBody SaveElement request
  ) {
    return service.updateElement(projectId, diagramId, auth.getName(), elementId, request);
  }

  @PostMapping("/{diagramId}/elements/positions")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void syncPositions(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody SyncPositions request
  ) {
    service.syncPositions(projectId, diagramId, auth.getName(), request);
  }

  @DeleteMapping("/{diagramId}/elements/{elementId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteElement(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      @PathVariable UUID elementId,
      Authentication auth
  ) {
    service.deleteElement(projectId, diagramId, auth.getName(), elementId);
  }

  @PostMapping("/{diagramId}/relations")
  @ResponseStatus(HttpStatus.OK)
  public RelationDetail saveRelation(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      Authentication auth,
      @Valid @RequestBody SaveRelation request
  ) {
    return service.createRelation(projectId, diagramId, auth.getName(), request);
  }

  @PutMapping("/{diagramId}/relations/{relationId}")
  public RelationDetail updateRelation(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      @PathVariable UUID relationId,
      Authentication auth,
      @Valid @RequestBody SaveRelation request
  ) {
    return service.updateRelation(projectId, diagramId, auth.getName(), relationId, request);
  }

  @DeleteMapping("/{diagramId}/relations/{relationId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRelation(
      @PathVariable UUID projectId,
      @PathVariable UUID diagramId,
      @PathVariable UUID relationId,
      Authentication auth
  ) {
    service.deleteRelation(projectId, diagramId, auth.getName(), relationId);
  }
}
