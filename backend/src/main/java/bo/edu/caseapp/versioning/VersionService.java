package bo.edu.caseapp.versioning;

import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.error.NotFoundException;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.*;
import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import bo.edu.caseapp.uml.UmlResponses.ElementDetail;
import bo.edu.caseapp.uml.UmlResponses.RelationDetail;
import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import bo.edu.caseapp.versioning.VersionRequests.CreateVersion;
import bo.edu.caseapp.versioning.VersionResponses.VersionDetail;
import bo.edu.caseapp.versioning.VersionResponses.VersionSummary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class VersionService {
  private final VersionDiagramaRepository versionRepo;
  private final DiagramaClaseRepository diagramRepo;
  private final ElementoUMLRepository elementRepo;
  private final RelacionUMLRepository relationRepo;
  private final ProjectService projectService;
  private final UserRepository userRepo;
  private final UmlService umlService;
  private final ObjectMapper objectMapper;

  public VersionService(
      VersionDiagramaRepository versionRepo,
      DiagramaClaseRepository diagramRepo,
      ElementoUMLRepository elementRepo,
      RelacionUMLRepository relationRepo,
      ProjectService projectService,
      UserRepository userRepo,
      UmlService umlService,
      ObjectMapper objectMapper
  ) {
    this.versionRepo = versionRepo;
    this.diagramRepo = diagramRepo;
    this.elementRepo = elementRepo;
    this.relationRepo = relationRepo;
    this.projectService = projectService;
    this.userRepo = userRepo;
    this.umlService = umlService;
    this.objectMapper = objectMapper;
  }

  @Transactional(readOnly = true)
  public List<VersionSummary> listVersions(UUID projectId, UUID diagramId, String email) {
    projectService.requireAccess(projectId, email);
    validateDiagramBelongsToProject(projectId, diagramId);
    return versionRepo.findByDiagramaIdOrderByNumeroVersionDesc(diagramId)
        .stream()
        .map(this::toSummary)
        .toList();
  }

  public VersionSummary createVersion(UUID projectId, UUID diagramId, String email, CreateVersion request) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = validateDiagramBelongsToProject(projectId, diagramId);
    User user = userRepo.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));

    DiagramFullView fullView = umlService.getDiagram(projectId, diagramId, email);

    String snapshotJson;
    try {
      snapshotJson = objectMapper.writeValueAsString(fullView);
    } catch (JsonProcessingException e) {
      throw new BadRequestException("No se pudo generar el snapshot del diagrama.");
    }

    int nextVersion = versionRepo.findMaxVersionNumber(diagramId) + 1;
    String descripcion = request.descripcion() != null && !request.descripcion().isBlank()
        ? request.descripcion().trim()
        : "Versión " + nextVersion;

    VersionDiagrama version = new VersionDiagrama(diagram, user, nextVersion, descripcion, snapshotJson);
    return toSummary(versionRepo.save(version));
  }

  public DiagramFullView restoreVersion(UUID projectId, UUID diagramId, UUID versionId, String email) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = validateDiagramBelongsToProject(projectId, diagramId);

    VersionDiagrama version = versionRepo.findByIdAndDiagramaId(versionId, diagramId)
        .orElseThrow(() -> new NotFoundException("Versión no encontrada."));

    DiagramFullView snapshot;
    try {
      snapshot = objectMapper.readValue(version.getSnapshotJson(), DiagramFullView.class);
    } catch (JsonProcessingException e) {
      throw new BadRequestException("Snapshot de versión corrupto o incompatible.");
    }

    // 1. Eliminar relaciones y elementos actuales del diagrama
    relationRepo.deleteByDiagramaId(diagramId);
    elementRepo.deleteByDiagramaId(diagramId);

    // 2. Mapeo de IDs antiguos a nuevos
    Map<UUID, ElementoUML> idMapping = new HashMap<>();
    for (ElementDetail el : snapshot.elements()) {
      ElementoUML nuevo = new ElementoUML(
          diagram,
          el.tipoElemento(),
          el.nombre(),
          el.posicionX(),
          el.posicionY(),
          el.propiedadesJson()
      );
      ElementoUML saved = elementRepo.save(nuevo);
      idMapping.put(el.id(), saved);
    }

    // 3. Recrear relaciones
    for (RelationDetail rel : snapshot.relations()) {
      ElementoUML origen = idMapping.get(rel.elementoOrigen());
      ElementoUML destino = idMapping.get(rel.elementoDestino());
      if (origen != null && destino != null) {
        RelacionUML nuevaRel = new RelacionUML(
            diagram,
            origen,
            destino,
            rel.nombreRelacion(),
            rel.tipoRelacion(),
            rel.multiplicidadOrigen(),
            rel.multiplicidadDestino()
        );
        relationRepo.save(nuevaRel);
      }
    }

    return umlService.getDiagram(projectId, diagramId, email);
  }

  private DiagramaClase validateDiagramBelongsToProject(UUID projectId, UUID diagramId) {
    DiagramaClase diagram = diagramRepo.findById(diagramId)
        .orElseThrow(() -> new NotFoundException("Diagrama no encontrado."));
    if (!diagram.getProject().getId().equals(projectId)) {
      throw new NotFoundException("Diagrama no pertenece al proyecto.");
    }
    return diagram;
  }

  private VersionSummary toSummary(VersionDiagrama v) {
    return new VersionSummary(
        v.getId(),
        v.getDiagrama().getId(),
        v.getNumeroVersion(),
        v.getDescripcion(),
        v.getUsuario().getName(),
        v.getUsuario().getEmail(),
        v.getFecha()
    );
  }
}
