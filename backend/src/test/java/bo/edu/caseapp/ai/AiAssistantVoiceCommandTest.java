package bo.edu.caseapp.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.ai.AiRequests.CommandRequest;
import bo.edu.caseapp.ai.AiResponses.CommandResponse;
import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.*;
import bo.edu.caseapp.uml.collab.UmlWebSocketHandler;
import bo.edu.caseapp.uml.relational.SqlDdlParserService;
import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiAssistantVoiceCommandTest {
  private DiagramaClaseRepository diagramRepo;
  private ElementoUMLRepository elementRepo;
  private RelacionUMLRepository relationRepo;
  private InteraccionIaRepository iaRepo;
  private ProjectService projectService;
  private UserRepository userRepo;
  private UmlService umlService;
  private SqlDdlParserService sqlDdlParserService;
  private UmlWebSocketHandler wsHandler;

  private AiAssistantService service;

  private UUID projectId;
  private UUID diagramId;
  private String email;
  private Project project;
  private DiagramaClase diagram;
  private User user;

  @BeforeEach
  void setUp() {
    diagramRepo = mock(DiagramaClaseRepository.class);
    elementRepo = mock(ElementoUMLRepository.class);
    relationRepo = mock(RelacionUMLRepository.class);
    iaRepo = mock(InteraccionIaRepository.class);
    projectService = mock(ProjectService.class);
    userRepo = mock(UserRepository.class);
    umlService = mock(UmlService.class);
    sqlDdlParserService = mock(SqlDdlParserService.class);
    wsHandler = mock(UmlWebSocketHandler.class);

    service = new AiAssistantService(
        diagramRepo,
        elementRepo,
        relationRepo,
        iaRepo,
        projectService,
        userRepo,
        umlService,
        sqlDdlParserService,
        wsHandler,
        "",
        "gemini-3.8-flash"
    );

    projectId = UUID.randomUUID();
    diagramId = UUID.randomUUID();
    email = "test@example.com";

    user = new User("Usuario Test", email, "pass");
    project = new Project(user, "Proyecto Voz", "Desc");
    org.springframework.test.util.ReflectionTestUtils.setField(project, "id", projectId);

    diagram = new DiagramaClase(project, "Diagrama Principal", "Desc");
    org.springframework.test.util.ReflectionTestUtils.setField(diagram, "id", diagramId);

    when(diagramRepo.findById(diagramId)).thenReturn(Optional.of(diagram));
    when(userRepo.findByEmailIgnoreCase(email)).thenReturn(Optional.of(user));
  }

  @Test
  void executeCommand_crearClase_success() {
    CommandRequest request = new CommandRequest("Crear clase Cliente con nombre String, correo String", "VOICE", null, null);

    CommandResponse response = service.executeCommand(projectId, diagramId, email, request);

    assertTrue(response.success());
    assertEquals("CREAR_CLASE", response.actionType());
    assertTrue(response.message().contains("Cliente"));
    verify(umlService).saveElement(eq(projectId), eq(diagramId), eq(email), any());
  }

  @Test
  void executeCommand_agregarAtributosContextual_success() {
    ElementoUML el = new ElementoUML(diagram, TipoElementoUML.CLASE, "Cliente", 0, 0, "{\"attributes\":[]}");
    org.springframework.test.util.ReflectionTestUtils.setField(el, "id", UUID.randomUUID());
    when(elementRepo.findByDiagramaId(diagramId)).thenReturn(List.of(el));

    CommandRequest request = new CommandRequest("Ponle nombre, correo y telefono", "VOICE", el.getId().toString(), null);

    CommandResponse response = service.executeCommand(projectId, diagramId, email, request);

    assertTrue(response.success());
    assertEquals("AGREGAR_ATRIBUTO", response.actionType());
    assertTrue(response.message().contains("Cliente"));
    verify(elementRepo).save(el);
  }

  @Test
  void executeCommand_throwsForbidden_whenDiagramIsLocked() {
    diagram.bloquear("otro@example.com");
    doThrow(new ForbiddenException("El diagrama 'Diagrama Principal' está bloqueado por otro@example.com."))
        .when(umlService).requireNotLocked(diagram, email);

    CommandRequest request = new CommandRequest("Crear clase Producto", "VOICE", null, null);

    assertThrows(ForbiddenException.class, () ->
        service.executeCommand(projectId, diagramId, email, request)
    );
  }

  @Test
  void executeCommand_withObsoleteRevision_throwsConflictException_beforeModifyingModel() {
    diagram.setRevision(5L);
    CommandRequest request = new CommandRequest("Crear clase Producto", "VOICE", null, null);

    bo.edu.caseapp.error.ConflictException ex = assertThrows(
        bo.edu.caseapp.error.ConflictException.class,
        () -> service.executeCommand(projectId, diagramId, email, request, 4L)
    );

    assertTrue(ex.getMessage().contains("Conflicto de concurrencia"));
    verifyNoInteractions(elementRepo);
    verifyNoInteractions(relationRepo);
  }
}
