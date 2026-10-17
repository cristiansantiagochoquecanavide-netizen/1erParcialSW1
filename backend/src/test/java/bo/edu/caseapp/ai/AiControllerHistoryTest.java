package bo.edu.caseapp.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.uml.DiagramaClase;
import bo.edu.caseapp.uml.UmlService;
import bo.edu.caseapp.user.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class AiControllerHistoryTest {
  private AiAssistantService aiService;
  private InteraccionIaRepository interactions;
  private UmlService umlService;
  private AiController controller;
  private UUID projectId;
  private UUID diagramId;

  @BeforeEach
  void setUp() {
    aiService = mock(AiAssistantService.class);
    interactions = mock(InteraccionIaRepository.class);
    umlService = mock(UmlService.class);
    controller = new AiController(aiService, interactions, umlService);
    projectId = UUID.randomUUID();
    diagramId = UUID.randomUUID();
  }

  @Test
  void historyReturnsOnlyAuthenticatedUsersInteractionsWithAttribution() {
    Authentication auth = authentication("ana@example.com");
    User ana = new User("Ana", "ana@example.com", "hash");
    Project project = new Project(ana, "Proyecto", null);
    DiagramaClase diagram = new DiagramaClase(project, "Diagrama", null);
    InteraccionIa saved = new InteraccionIa(diagram, ana, "COMANDO_TEXT", "Crear clase Cliente", "Creada", null);
    UUID userId = UUID.randomUUID();
    org.springframework.test.util.ReflectionTestUtils.setField(ana, "id", userId);
    org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
    when(interactions.findByDiagramaIdAndUsuarioEmailIgnoreCaseOrderByFechaDesc(diagramId, "ana@example.com"))
        .thenReturn(List.of(saved));

    var history = controller.history(projectId, diagramId, auth);

    verify(umlService).requireDiagramAccess(projectId, diagramId, "ana@example.com");
    verify(interactions).findByDiagramaIdAndUsuarioEmailIgnoreCaseOrderByFechaDesc(diagramId, "ana@example.com");
    verifyNoMoreInteractions(interactions);
    assertEquals(1, history.size());
    assertEquals(userId, history.get(0).usuarioId());
    assertEquals("Ana", history.get(0).usuarioNombre());
  }

  @Test
  void historyRejectsUserWithoutProjectAccessBeforeReadingInteractions() {
    Authentication auth = authentication("bob@example.com");
    doThrow(new ForbiddenException("No tienes acceso a este proyecto."))
        .when(umlService).requireDiagramAccess(projectId, diagramId, "bob@example.com");

    assertThrows(ForbiddenException.class, () -> controller.history(projectId, diagramId, auth));
    verifyNoInteractions(interactions);
  }

  @Test
  void historyQueryIsScopedToRequestingCollaborator() {
    Authentication auth = authentication("bob@example.com");
    when(interactions.findByDiagramaIdAndUsuarioEmailIgnoreCaseOrderByFechaDesc(diagramId, "bob@example.com"))
        .thenReturn(List.of());

    assertEquals(List.of(), controller.history(projectId, diagramId, auth));
    verify(umlService).requireDiagramAccess(projectId, diagramId, "bob@example.com");
    verify(interactions).findByDiagramaIdAndUsuarioEmailIgnoreCaseOrderByFechaDesc(diagramId, "bob@example.com");
    verifyNoMoreInteractions(interactions);
  }

  private Authentication authentication(String email) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(email);
    return authentication;
  }
}
