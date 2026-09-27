package bo.edu.caseapp.uml;

import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.error.NotFoundException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectMember;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.UmlRequests.*;
import bo.edu.caseapp.uml.UmlResponses.*;
import bo.edu.caseapp.uml.relational.RelationalModelService;
import bo.edu.caseapp.uml.relational.RelationalSchema;
import bo.edu.caseapp.uml.validation.UmlValidatorService;
import bo.edu.caseapp.uml.validation.ValidationReport;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UmlService {
  private final DiagramaClaseRepository diagrams;
  private final ElementoUMLRepository elements;
  private final RelacionUMLRepository relations;
  private final ProjectService projectService;
  private final UmlValidatorService validatorService;
  private final RelationalModelService relationalModelService;
  private final bo.edu.caseapp.uml.collab.UmlWebSocketHandler collabHandler;

  public UmlService(
      DiagramaClaseRepository diagrams,
      ElementoUMLRepository elements,
      RelacionUMLRepository relations,
      ProjectService projectService,
      UmlValidatorService validatorService,
      RelationalModelService relationalModelService
  ) {
    this(diagrams, elements, relations, projectService, validatorService, relationalModelService, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public UmlService(
      DiagramaClaseRepository diagrams,
      ElementoUMLRepository elements,
      RelacionUMLRepository relations,
      ProjectService projectService,
      UmlValidatorService validatorService,
      RelationalModelService relationalModelService,
      @org.springframework.beans.factory.annotation.Autowired(required = false) bo.edu.caseapp.uml.collab.UmlWebSocketHandler collabHandler
  ) {
    this.diagrams = diagrams;
    this.elements = elements;
    this.relations = relations;
    this.projectService = projectService;
    this.validatorService = validatorService;
    this.relationalModelService = relationalModelService;
    this.collabHandler = collabHandler;
  }

  private void notifyCollab(UUID diagramId, String eventType) {
    if (collabHandler != null && diagramId != null) {
      collabHandler.broadcastDiagramChanged(diagramId, eventType);
    }
  }

  @Transactional(readOnly = true)
  public List<DiagramSummary> listDiagrams(UUID projectId, String email) {
    projectService.requireAccess(projectId, email);
    List<DiagramaClase> list = diagrams.findByProjectIdOrderByFechaCreacionAsc(projectId);
    return list.stream().map(this::summary).toList();
  }

  public DiagramSummary getOrCreateDefaultDiagram(UUID projectId, String email) {
    ProjectMember member = projectService.requireAccess(projectId, email);
    List<DiagramaClase> list = diagrams.findByProjectIdOrderByFechaCreacionAsc(projectId);
    if (!list.isEmpty()) {
      return summary(list.get(0));
    }
    // Si no hay diagramas y el usuario puede editar, creamos el diagrama inicial
    projectService.requireCanEdit(projectId, email);
    DiagramaClase nuevo = diagrams.save(new DiagramaClase(member.getProject(), "Diagrama Principal", "Diagrama de clases inicial"));
    return summary(nuevo);
  }

  public DiagramSummary createDiagram(UUID projectId, String email, CreateDiagram request) {
    ProjectMember member = projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = diagrams.save(new DiagramaClase(member.getProject(), request.nombre().trim(), clean(request.descripcion())));
    return summary(diagram);
  }

  @Transactional(readOnly = true)
  public DiagramFullView getDiagram(UUID projectId, UUID diagramId, String email) {
    projectService.requireAccess(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    List<ElementDetail> elementDetails = elements.findByDiagramaId(diagramId).stream().map(this::elementDetail).toList();
    List<RelationDetail> relationDetails = relations.findByDiagramaId(diagramId).stream().map(this::relationDetail).toList();
    return new DiagramFullView(summary(diagram), elementDetails, relationDetails);
  }

  @Transactional(readOnly = true)
  public ValidationReport validateDiagram(UUID projectId, UUID diagramId, String email) {
    projectService.requireAccess(projectId, email);
    findDiagram(projectId, diagramId);
    List<ElementoUML> elList = elements.findByDiagramaId(diagramId);
    List<RelacionUML> relList = relations.findByDiagramaId(diagramId);
    return validatorService.validate(elList, relList);
  }

  @Transactional(readOnly = true)
  public RelationalSchema generateRelationalSchema(UUID projectId, UUID diagramId, String email) {
    projectService.requireAccess(projectId, email);
    findDiagram(projectId, diagramId);
    List<ElementoUML> elList = elements.findByDiagramaId(diagramId);
    List<RelacionUML> relList = relations.findByDiagramaId(diagramId);
    return relationalModelService.generateSchema(elList, relList);
  }

  public void requireNotLocked(DiagramaClase diagram, String email) {
    if (diagram.isBloqueado() && (diagram.getBloqueadoPor() == null || !diagram.getBloqueadoPor().equalsIgnoreCase(email))) {
      throw new ForbiddenException("El diagrama está bloqueado por " + diagram.getBloqueadoPor() + " durante una operación de importación/exportación EA.");
    }
  }

  public void acquireLock(UUID projectId, UUID diagramId, String email) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    if (diagram.isBloqueado() && !email.equalsIgnoreCase(diagram.getBloqueadoPor())) {
      throw new ForbiddenException("El diagrama ya está bloqueado por " + diagram.getBloqueadoPor());
    }
    diagram.bloquear(email);
    diagrams.save(diagram);
    notifyCollab(diagramId, "DIAGRAM_LOCKED");
  }

  public void releaseLock(UUID projectId, UUID diagramId, String email) {
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    if (diagram.isBloqueado()) {
      diagram.desbloquear();
      diagrams.save(diagram);
      notifyCollab(diagramId, "DIAGRAM_UNLOCKED");
    }
  }

  public DiagramSummary updateDiagram(UUID projectId, UUID diagramId, String email, UpdateDiagram request) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    requireNotLocked(diagram, email);
    diagram.update(request.nombre().trim(), clean(request.descripcion()));
    notifyCollab(diagramId, "DIAGRAM_UPDATED");
    return summary(diagram);
  }

  public void deleteDiagram(UUID projectId, UUID diagramId, String email) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    requireNotLocked(diagram, email);
    diagrams.delete(diagram);
    notifyCollab(diagramId, "DIAGRAM_DELETED");
  }

  public ElementDetail saveElement(UUID projectId, UUID diagramId, String email, SaveElement request) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    requireNotLocked(diagram, email);

    ElementoUML element;
    if (request.id() != null) {
      java.util.Optional<ElementoUML> existing = elements.findById(request.id());
      if (existing.isPresent()) {
        element = existing.get();
        if (!element.getDiagrama().getId().equals(diagramId)) {
          throw new BadRequestException("El elemento no pertenece a este diagrama.");
        }
        element.update(
            request.tipoElemento(),
            request.nombre().trim(),
            request.posicionX(),
            request.posicionY(),
            request.propiedadesJson()
        );
      } else {
        element = new ElementoUML(
            diagram,
            request.tipoElemento(),
            request.nombre().trim(),
            request.posicionX(),
            request.posicionY(),
            request.propiedadesJson()
        );
      }
    } else {
      element = new ElementoUML(
          diagram,
          request.tipoElemento(),
          request.nombre().trim(),
          request.posicionX(),
          request.posicionY(),
          request.propiedadesJson()
      );
    }
    ElementDetail saved = elementDetail(elements.save(element));
    notifyCollab(diagramId, "ELEMENT_SAVED");
    return saved;
  }

  public void syncPositions(UUID projectId, UUID diagramId, String email, SyncPositions request) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    requireNotLocked(diagram, email);

    if (request.positions() == null) return;
    for (PositionUpdate pos : request.positions()) {
      elements.findById(pos.id()).ifPresent(el -> {
        if (el.getDiagrama().getId().equals(diagramId)) {
          el.updatePosition(pos.posicionX(), pos.posicionY());
          elements.save(el);
        }
      });
    }
    notifyCollab(diagramId, "POSITIONS_SYNCED");
  }

  public void deleteElement(UUID projectId, UUID diagramId, String email, UUID elementId) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    requireNotLocked(diagram, email);
    ElementoUML element = elements.findById(elementId)
        .orElseThrow(() -> new NotFoundException("Elemento UML no encontrado."));
    if (!element.getDiagrama().getId().equals(diagramId)) {
      throw new BadRequestException("El elemento no pertenece a este diagrama.");
    }
    // Eliminar relaciones conectadas
    relations.deleteByElementoOrigenIdOrElementoDestinoId(elementId, elementId);
    elements.delete(element);
    notifyCollab(diagramId, "ELEMENT_DELETED");
  }

  public RelationDetail saveRelation(UUID projectId, UUID diagramId, String email, SaveRelation request) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    requireNotLocked(diagram, email);

    ElementoUML origen = elements.findById(request.elementoOrigen())
        .orElseThrow(() -> new NotFoundException("Elemento origen no encontrado."));
    ElementoUML destino = elements.findById(request.elementoDestino())
        .orElseThrow(() -> new NotFoundException("Elemento destino no encontrado."));

    if (!origen.getDiagrama().getId().equals(diagramId) || !destino.getDiagrama().getId().equals(diagramId)) {
      throw new BadRequestException("Los elementos relacionados deben pertenecer al mismo diagrama.");
    }

    RelacionUML relation;
    if (request.id() != null) {
      java.util.Optional<RelacionUML> existing = relations.findById(request.id());
      if (existing.isPresent()) {
        relation = existing.get();
        if (!relation.getDiagrama().getId().equals(diagramId)) {
          throw new BadRequestException("La relación no pertenece a este diagrama.");
        }
        relation.update(
            clean(request.nombreRelacion()),
            request.tipoRelacion(),
            clean(request.multiplicidadOrigen()),
            clean(request.multiplicidadDestino())
        );
      } else {
        relation = new RelacionUML(
            diagram,
            origen,
            destino,
            clean(request.nombreRelacion()),
            request.tipoRelacion(),
            clean(request.multiplicidadOrigen()),
            clean(request.multiplicidadDestino())
        );
      }
    } else {
      relation = new RelacionUML(
          diagram,
          origen,
          destino,
          clean(request.nombreRelacion()),
          request.tipoRelacion(),
          clean(request.multiplicidadOrigen()),
          clean(request.multiplicidadDestino())
      );
    }
    RelationDetail saved = relationDetail(relations.save(relation));
    notifyCollab(diagramId, "RELATION_SAVED");
    return saved;
  }

  public void deleteRelation(UUID projectId, UUID diagramId, String email, UUID relationId) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = findDiagram(projectId, diagramId);
    requireNotLocked(diagram, email);
    RelacionUML relation = relations.findById(relationId)
        .orElseThrow(() -> new NotFoundException("Relación UML no encontrada."));
    if (!relation.getDiagrama().getId().equals(diagramId)) {
      throw new BadRequestException("La relación no pertenece a este diagrama.");
    }
    relations.delete(relation);
    notifyCollab(diagramId, "RELATION_DELETED");
  }

  private DiagramaClase findDiagram(UUID projectId, UUID diagramId) {
    DiagramaClase diagram = diagrams.findById(diagramId)
        .orElseThrow(() -> new NotFoundException("Diagrama no encontrado."));
    if (!diagram.getProject().getId().equals(projectId)) {
      throw new NotFoundException("Diagrama no encontrado en este proyecto.");
    }
    return diagram;
  }

  private DiagramSummary summary(DiagramaClase d) {
    return new DiagramSummary(
        d.getId(),
        d.getProject().getId(),
        d.getNombre(),
        d.getDescripcion(),
        d.getEstado(),
        d.getFechaCreacion(),
        d.isBloqueado(),
        d.getBloqueadoPor(),
        d.getFechaBloqueo()
    );
  }

  private ElementDetail elementDetail(ElementoUML e) {
    return new ElementDetail(e.getId(), e.getDiagrama().getId(), e.getTipoElemento(), e.getNombre(), e.getPosicionX(), e.getPosicionY(), e.getPropiedadesJson());
  }

  private RelationDetail relationDetail(RelacionUML r) {
    return new RelationDetail(r.getId(), r.getDiagrama().getId(), r.getElementoOrigen().getId(), r.getElementoDestino().getId(), r.getNombreRelacion(), r.getTipoRelacion(), r.getMultiplicidadOrigen(), r.getMultiplicidadDestino());
  }

  private String clean(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
