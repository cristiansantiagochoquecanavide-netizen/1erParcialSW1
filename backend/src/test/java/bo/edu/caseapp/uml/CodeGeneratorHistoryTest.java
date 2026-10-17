package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.error.NotFoundException;
import bo.edu.caseapp.generator.CodeGeneratorService;
import bo.edu.caseapp.generator.GeneracionSoftware;
import bo.edu.caseapp.generator.GeneracionSoftwareRepository;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.relational.RelationalModelService;
import bo.edu.caseapp.user.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodeGeneratorHistoryTest {
  private GeneracionSoftwareRepository generations;
  private ProjectService projects;
  private DiagramaClaseRepository diagrams;
  private CodeGeneratorService service;
  private UUID projectId;
  private UUID diagramId;
  private DiagramaClase diagram;

  @BeforeEach
  void setUp() {
    generations = mock(GeneracionSoftwareRepository.class);
    projects = mock(ProjectService.class);
    diagrams = mock(DiagramaClaseRepository.class);
    service = new CodeGeneratorService(
        diagrams, mock(ElementoUMLRepository.class), mock(RelacionUMLRepository.class),
        generations, projects, mock(UserRepository.class), new RelationalModelService());
    projectId = UUID.randomUUID();
    diagramId = UUID.randomUUID();
    diagram = mock(DiagramaClase.class);
    Project project = mock(Project.class);
    when(project.getId()).thenReturn(projectId);
    when(diagram.getProject()).thenReturn(project);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
  }

  @Test
  void authorizedUserReceivesHistoryForTheAuthorizedDiagram() {
    GeneracionSoftware record = mock(GeneracionSoftware.class);
    UUID generationId = UUID.randomUUID();
    when(record.getId()).thenReturn(generationId);
    when(record.getTipoGeneracion()).thenReturn("BACKEND");
    when(record.getEstado()).thenReturn("COMPLETADO");
    when(generations.findByDiagramaIdOrderByFechaDesc(diagramId)).thenReturn(List.of(record));

    var result = service.history(projectId, diagramId, "member@example.com");

    assertEquals(1, result.size());
    assertEquals(generationId, result.get(0).id());
    verify(projects).requireAccess(projectId, "member@example.com");
    verify(generations).findByDiagramaIdOrderByFechaDesc(diagramId);
  }

  @Test
  void userWithoutProjectAccessCannotReadHistory() {
    doThrow(new ForbiddenException("No tienes acceso a este proyecto."))
        .when(projects).requireAccess(projectId, "outsider@example.com");

    assertThrows(ForbiddenException.class,
        () -> service.history(projectId, diagramId, "outsider@example.com"));

    verifyNoInteractions(generations, diagrams);
  }

  @Test
  void authorizedMemberWhoRequestsKnownDiagramFromAnotherProjectCannotReadHistory() {
    UUID otherProjectId = UUID.randomUUID();
    Project otherProject = mock(Project.class);
    when(otherProject.getId()).thenReturn(otherProjectId);
    when(diagram.getProject()).thenReturn(otherProject);

    assertThrows(NotFoundException.class,
        () -> service.history(projectId, diagramId, "member@example.com"));

    verify(projects).requireAccess(projectId, "member@example.com");
    verifyNoInteractions(generations);
  }

  @Test
  void userCannotReadGenerationHistoryFromAnotherProject() {
    doThrow(new ForbiddenException("No tienes acceso a este proyecto."))
        .when(projects).requireAccess(projectId, "other-project-user@example.com");

    assertThrows(ForbiddenException.class,
        () -> service.history(projectId, diagramId, "other-project-user@example.com"));

    verifyNoInteractions(generations);
  }

  @Test
  void missingDiagramDoesNotRevealHistory() {
    when(diagrams.findById(diagramId)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> service.history(projectId, diagramId, "member@example.com"));

    verifyNoInteractions(generations);
  }
}
