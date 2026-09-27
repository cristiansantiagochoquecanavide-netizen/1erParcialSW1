package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectMember;
import bo.edu.caseapp.project.ProjectRole;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
import bo.edu.caseapp.uml.relational.RelationalModelService;
import bo.edu.caseapp.uml.validation.UmlValidatorService;
import bo.edu.caseapp.user.User;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UmlServiceLockTest {
  private DiagramaClaseRepository diagrams;
  private ElementoUMLRepository elements;
  private RelacionUMLRepository relations;
  private ProjectService projectService;
  private UmlValidatorService validatorService;
  private RelationalModelService relationalModelService;
  private UmlService service;

  private UUID projectId;
  private UUID diagramId;
  private Project project;
  private DiagramaClase diagram;
  private String userA;
  private String userB;

  @BeforeEach
  void setUp() {
    diagrams = mock(DiagramaClaseRepository.class);
    elements = mock(ElementoUMLRepository.class);
    relations = mock(RelacionUMLRepository.class);
    projectService = mock(ProjectService.class);
    validatorService = new UmlValidatorService();
    relationalModelService = new RelationalModelService();

    service = new UmlService(
        diagrams,
        elements,
        relations,
        projectService,
        validatorService,
        relationalModelService
    );

    projectId = UUID.randomUUID();
    diagramId = UUID.randomUUID();
    User owner = new User("Ana", "ana@example.com", "hash");
    project = new Project(owner, "Proyecto Test", "Desc");
    org.springframework.test.util.ReflectionTestUtils.setField(project, "id", projectId);

    diagram = new DiagramaClase(project, "Diagrama 1", "Desc");
    org.springframework.test.util.ReflectionTestUtils.setField(diagram, "id", diagramId);

    userA = "ana@example.com";
    userB = "bob@example.com";
  }

  @Test
  void saveElement_throwsForbidden_whenLockedByAnotherUser() {
    diagram.bloquear(userB);
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireCanEdit(projectId, userA)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));

    SaveElement request = new SaveElement(
        null,
        TipoElementoUML.CLASE,
        "NuevaClase",
        100.0,
        100.0,
        "{}"
    );

    ForbiddenException ex = assertThrows(ForbiddenException.class, () ->
        service.saveElement(projectId, diagramId, userA, request)
    );

    assertTrue(ex.getMessage().contains("bloqueado por bob@example.com"));
  }

  @Test
  void saveElement_succeeds_whenLockedBySameUser() {
    diagram.bloquear(userA);
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireCanEdit(projectId, userA)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    when(elements.save(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    SaveElement request = new SaveElement(
        null,
        TipoElementoUML.CLASE,
        "NuevaClase",
        100.0,
        100.0,
        "{}"
    );

    assertDoesNotThrow(() -> service.saveElement(projectId, diagramId, userA, request));
  }

  @Test
  void acquireAndReleaseLock_success() {
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireCanEdit(projectId, userA)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));

    service.acquireLock(projectId, diagramId, userA);
    assertTrue(diagram.isBloqueado());
    assertEquals(userA, diagram.getBloqueadoPor());

    service.releaseLock(projectId, diagramId, userA);
    assertFalse(diagram.isBloqueado());
    assertNull(diagram.getBloqueadoPor());
  }

  @Test
  void isBloqueado_autoTimeoutAfter2Minutes() {
    diagram.bloquear(userB);
    // Simular que el bloqueo fue hace 3 minutos (180 segundos)
    org.springframework.test.util.ReflectionTestUtils.setField(diagram, "fechaBloqueo", java.time.Instant.now().minusSeconds(180));

    assertFalse(diagram.isBloqueado(), "El bloqueo debería expirar después del timeout de 2 minutos");
  }
}
