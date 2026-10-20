package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
import bo.edu.caseapp.uml.UmlRequests.SyncPositions;
import bo.edu.caseapp.uml.UmlRequests.SaveRelation;
import bo.edu.caseapp.uml.UmlRequests.UpdateDiagram;
import bo.edu.caseapp.uml.relational.SqlDdlParserService;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class UmlControllerValidationTest {

  private UmlService service;
  private SqlDdlParserService sqlDdlParserService;
  private UmlController controller;
  private Authentication auth;
  private HttpServletResponse response;

  private final UUID projectId = UUID.randomUUID();
  private final UUID diagramId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    service = mock(UmlService.class);
    sqlDdlParserService = mock(SqlDdlParserService.class);
    controller = new UmlController(service, sqlDdlParserService);
    auth = mock(Authentication.class);
    when(auth.getName()).thenReturn("test@example.com");
    response = mock(HttpServletResponse.class);
  }

  @Test
  void saveElement_withoutRevision_throwsBadRequestException() {
    SaveElement req = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "Cliente", 0.0, 0.0, "{}", null);
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.saveElement(projectId, diagramId, auth, req, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void updateElement_withoutRevision_throwsBadRequestException() {
    UUID elemId = UUID.randomUUID();
    SaveElement req = new SaveElement(elemId, TipoElementoUML.CLASE, "ClienteMod", 10.0, 10.0, "{}", null);
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.updateElement(projectId, diagramId, elemId, auth, req, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void deleteElement_withoutRevision_throwsBadRequestException() {
    UUID elemId = UUID.randomUUID();
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.deleteElement(projectId, diagramId, elemId, auth, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void saveRelation_withoutRevision_throwsBadRequestException() {
    SaveRelation req = new SaveRelation(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "rel",
        TipoRelacionUML.ASOCIACION, "1", "1", null);
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.saveRelation(projectId, diagramId, auth, req, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void deleteRelation_withoutRevision_throwsBadRequestException() {
    UUID relId = UUID.randomUUID();
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.deleteRelation(projectId, diagramId, relId, auth, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void syncPositions_withoutRevision_throwsBadRequestException() {
    SyncPositions req = new SyncPositions(List.of(), null);
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.syncPositions(projectId, diagramId, auth, req, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void updateDiagram_withoutRevision_throwsBadRequestException() {
    UpdateDiagram req = new UpdateDiagram("Nuevo", "Desc", null);
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.update(projectId, diagramId, auth, req, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void deleteDiagram_withoutRevision_throwsBadRequestException() {
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.delete(projectId, diagramId, auth, null, null)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(service);
  }

  @Test
  void importSql_withoutRevision_throwsBadRequestException() {
    UmlController.ImportSqlRequest req = new UmlController.ImportSqlRequest("CREATE TABLE test (id INT);");
    BadRequestException ex = assertThrows(BadRequestException.class, () ->
        controller.importSql(projectId, diagramId, auth, req, null, null, response)
    );
    assertTrue(ex.getMessage().contains("revisión esperada"));
    verifyNoInteractions(sqlDdlParserService);
  }

  @Test
  void saveElement_withHeaderRevision_succeeds() {
    SaveElement req = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "Cliente", 0.0, 0.0, "{}", null);
    when(service.createElement(eq(projectId), eq(diagramId), eq("test@example.com"), any())).thenReturn(
        new UmlResponses.ElementDetail(req.id(), diagramId, req.tipoElemento(), req.nombre(), 0.0, 0.0, "{}")
    );
    when(service.getDiagramRevision(diagramId)).thenReturn(2L);

    controller.saveElement(projectId, diagramId, auth, req, 1L, null, response);

    verify(service).createElement(eq(projectId), eq(diagramId), eq("test@example.com"), argThat(s -> s.expectedRevision().equals(1L)));
    verify(response).setHeader("X-Diagram-Revision", "2");
  }

  @Test
  void saveElement_withIfMatchHeader_succeeds() {
    SaveElement req = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "Cliente", 0.0, 0.0, "{}", null);
    when(service.createElement(eq(projectId), eq(diagramId), eq("test@example.com"), any())).thenReturn(
        new UmlResponses.ElementDetail(req.id(), diagramId, req.tipoElemento(), req.nombre(), 0.0, 0.0, "{}")
    );
    when(service.getDiagramRevision(diagramId)).thenReturn(6L);

    controller.saveElement(projectId, diagramId, auth, req, null, "\"5\"", response);

    verify(service).createElement(eq(projectId), eq(diagramId), eq("test@example.com"), argThat(s -> s.expectedRevision().equals(5L)));
    verify(response).setHeader("X-Diagram-Revision", "6");
  }
}
