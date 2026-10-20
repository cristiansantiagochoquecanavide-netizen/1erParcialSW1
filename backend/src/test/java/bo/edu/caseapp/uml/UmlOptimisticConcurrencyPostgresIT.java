package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.ConflictException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectMember;
import bo.edu.caseapp.project.ProjectMemberRepository;
import bo.edu.caseapp.project.ProjectRepository;
import bo.edu.caseapp.project.ProjectRole;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
import bo.edu.caseapp.uml.UmlResponses.ElementDetail;
import bo.edu.caseapp.uml.collab.UmlWebSocketHandler;
import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Prueba de integración de concurrencia optimista directamente contra PostgreSQL real.
 * Valida que dos transacciones simultáneas que compiten sobre la misma revisión:
 * 1. Solo una se confirme exitosamente.
 * 2. La otra reciba ConflictException (HTTP 409).
 * 3. No queden cambios parciales en la base de datos PostgreSQL.
 * 4. La revisión del diagrama se incremente exactamente una vez (de N a N+1).
 * 5. El evento WebSocket se anuncie únicamente para la transacción confirmada.
 */
@SpringBootTest
@ActiveProfiles("dev")
public class UmlOptimisticConcurrencyPostgresIT {

  @Autowired
  private UmlService umlService;

  @Autowired
  private DiagramaClaseRepository diagramRepo;

  @Autowired
  private ElementoUMLRepository elementRepo;

  @Autowired
  private RelacionUMLRepository relationRepo;

  @Autowired
  private ProjectRepository projectRepo;

  @Autowired
  private ProjectMemberRepository memberRepo;

  @Autowired
  private UserRepository userRepo;

  @MockBean
  private UmlWebSocketHandler collabHandler;

  private User user;
  private Project project;
  private ProjectMember member;
  private DiagramaClase diagram;

  private final String testEmail = "pg-concurrency-" + UUID.randomUUID() + "@example.com";

  @BeforeEach
  void setUp() {
    user = userRepo.save(new User("Postgres Tester", testEmail, "test-hash"));
    project = projectRepo.save(new Project(user, "Postgres Concurrency Test Project", "Test project for CAS"));
    member = memberRepo.save(new ProjectMember(project, user, ProjectRole.PROPIETARIO));

    diagram = new DiagramaClase(project, "Diagrama Concurrente PostgreSQL", "CAS verification");
    diagram.setRevision(1L);
    diagram = diagramRepo.saveAndFlush(diagram);
  }

  @AfterEach
  void tearDown() {
    try {
      if (diagram != null && diagram.getId() != null) {
        relationRepo.deleteAll(relationRepo.findByDiagramaId(diagram.getId()));
        elementRepo.deleteAll(elementRepo.findByDiagramaId(diagram.getId()));
        diagramRepo.deleteById(diagram.getId());
      }
      if (member != null && member.getId() != null) {
        memberRepo.deleteById(member.getId());
      }
      if (project != null && project.getId() != null) {
        projectRepo.deleteById(project.getId());
      }
      if (user != null && user.getId() != null) {
        userRepo.deleteById(user.getId());
      }
    } catch (Exception ignored) {}
  }

  @Test
  void concurrentTransactions_againstPostgres_onlyOneWinsAndOtherGets409() throws Exception {
    UUID projectId = project.getId();
    UUID diagramId = diagram.getId();
    long initialRevision = diagram.getRevision(); // 1L

    UUID elemIdA = UUID.randomUUID();
    UUID elemIdB = UUID.randomUUID();

    SaveElement reqA = new SaveElement(elemIdA, TipoElementoUML.CLASE, "ClaseA_PG", 10.0, 10.0, "{}", initialRevision);
    SaveElement reqB = new SaveElement(elemIdB, TipoElementoUML.CLASE, "ClaseB_PG", 20.0, 20.0, "{}", initialRevision);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch startGate = new CountDownLatch(1);

    Future<ElementDetail> taskA = pool.submit(() -> {
      startGate.await();
      return umlService.createElement(projectId, diagramId, testEmail, reqA);
    });

    Future<ElementDetail> taskB = pool.submit(() -> {
      startGate.await();
      return umlService.createElement(projectId, diagramId, testEmail, reqB);
    });

    // Disparar ambas transacciones simultáneamente contra PostgreSQL
    startGate.countDown();

    int successCount = 0;
    int conflictCount = 0;
    UUID winningElementId = null;

    try {
      ElementDetail resA = taskA.get();
      successCount++;
      winningElementId = resA.id();
    } catch (Exception e) {
      Throwable root = e.getCause();
      if (root instanceof ConflictException) conflictCount++;
      else fail("Transacción A falló con excepción inesperada: " + root);
    }

    try {
      ElementDetail resB = taskB.get();
      successCount++;
      winningElementId = resB.id();
    } catch (Exception e) {
      Throwable root = e.getCause();
      if (root instanceof ConflictException) conflictCount++;
      else fail("Transacción B falló con excepción inesperada: " + root);
    }

    pool.shutdown();

    // 1. Exactamente una operación se confirmó
    assertEquals(1, successCount, "Exactamente una operación concurrente debe confirmarse");

    // 2. La otra operación recibió HTTP 409 ConflictException
    assertEquals(1, conflictCount, "La operación concurrente rezagada debe recibir ConflictException (HTTP 409)");

    // 3. Verificar estado en PostgreSQL real
    DiagramaClase updatedDiagram = diagramRepo.findById(diagramId).orElseThrow();

    // 4. La revisión del diagrama aumentó una sola vez (de 1 a 2)
    assertEquals(initialRevision + 1L, updatedDiagram.getRevision(),
        "La revisión en PostgreSQL debe aumentar exactamente una vez (de " + initialRevision + " a " + (initialRevision + 1) + ")");

    // 5. No quedaron cambios parciales: exactamente 1 elemento existe en la tabla de PostgreSQL
    List<ElementoUML> persistedElements = elementRepo.findByDiagramaId(diagramId);
    assertEquals(1, persistedElements.size(), "No deben quedar cambios parciales; solo debe existir el elemento ganador");
    assertEquals(winningElementId, persistedElements.get(0).getId(), "El elemento en PostgreSQL debe ser el que ganó la carrera");

    // 6. El evento WebSocket correspondió al cambio confirmado con la revisión incrementada
    verify(collabHandler, times(1)).broadcastDiagramChanged(eq(diagramId), eq("ELEMENT_SAVED"), eq(initialRevision + 1L));
  }

  @Test
  void twoUsersScenario_userACommitsFirst_userBRejectedWith409_afterSyncUserBCommitsOnNewRevision() {
    UUID projectId = project.getId();
    UUID diagramId = diagram.getId();

    // Crear un segundo usuario colaborador B
    User userB = userRepo.save(new User("User B", "collab-b-" + UUID.randomUUID() + "@example.com", "pass"));
    memberRepo.save(new ProjectMember(project, userB, ProjectRole.EDITOR));

    // Paso 1: Ambos usuarios cargan el diagrama inicialmente en revisión 1
    long initialRev = umlService.getDiagramRevision(diagramId);
    assertEquals(1L, initialRev);

    // Paso 2: Usuario A crea una clase 'ClaseA' basada en la revisión 1 y guarda
    UUID elemAId = UUID.randomUUID();
    SaveElement reqA = new SaveElement(elemAId, TipoElementoUML.CLASE, "ClaseA", 10.0, 10.0, "{}", 1L);
    ElementDetail savedA = umlService.createElement(projectId, diagramId, testEmail, reqA);
    assertNotNull(savedA);

    // El servidor avanza la revisión a 2
    long revAfterA = umlService.getDiagramRevision(diagramId);
    assertEquals(2L, revAfterA);

    // Paso 3: Usuario B intenta guardar 'ClaseB' basándose en la revisión 1 (obsoleta)
    UUID elemBId = UUID.randomUUID();
    SaveElement reqB_obsolete = new SaveElement(elemBId, TipoElementoUML.CLASE, "ClaseB", 50.0, 50.0, "{}", 1L);
    ConflictException conflictEx = assertThrows(ConflictException.class, () ->
        umlService.createElement(projectId, diagramId, userB.getEmail(), reqB_obsolete)
    );
    assertTrue(conflictEx.getMessage().contains("Conflicto de concurrencia"));

    // Paso 4: Comprobar que el cambio de A permanece guardado y B no dejó cambios parciales
    List<ElementoUML> currentElements = elementRepo.findByDiagramaId(diagramId);
    assertEquals(1, currentElements.size());
    assertEquals("ClaseA", currentElements.get(0).getNombre());
    assertEquals(2L, umlService.getDiagramRevision(diagramId));

    // Paso 5: Usuario B actualiza el estado (sincroniza viendo la revisión 2)
    UmlResponses.DiagramFullView fullView = umlService.getDiagram(projectId, diagramId, userB.getEmail());
    assertEquals(2L, fullView.diagram().revision());
    assertEquals(1, fullView.elements().size());

    // Paso 6: Con el estado actualizado, Usuario B envía 'ClaseB' con la nueva revisión 2
    SaveElement reqB_updated = new SaveElement(elemBId, TipoElementoUML.CLASE, "ClaseB", 50.0, 50.0, "{}", 2L);
    ElementDetail savedB = umlService.createElement(projectId, diagramId, userB.getEmail(), reqB_updated);
    assertNotNull(savedB);

    // Paso 7: La revisión aumentó a 3 y ambos cambios existen en PostgreSQL sin duplicación
    assertEquals(3L, umlService.getDiagramRevision(diagramId));
    List<ElementoUML> finalElements = elementRepo.findByDiagramaId(diagramId);
    assertEquals(2, finalElements.size());
    assertTrue(finalElements.stream().anyMatch(e -> e.getNombre().equals("ClaseA")));
    assertTrue(finalElements.stream().anyMatch(e -> e.getNombre().equals("ClaseB")));
  }

  @Test
  void stableUuidIdempotentRetry_andContentConflict_verifiedAgainstPostgres() {
    UUID projectId = project.getId();
    UUID diagramId = diagram.getId();

    UUID stableElemId = UUID.randomUUID();
    SaveElement createReq = new SaveElement(stableElemId, TipoElementoUML.CLASE, "ClaseEstable", 100.0, 100.0, "{}", 1L);

    // 1. Creación exitosa con UUID estable
    ElementDetail firstAttempt = umlService.createElement(projectId, diagramId, testEmail, createReq);
    assertEquals(stableElemId, firstAttempt.id());
    assertEquals(2L, umlService.getDiagramRevision(diagramId));

    // 2. Reintento idéntico con el mismo UUID y mismo contenido (idempotente)
    ElementDetail retryAttempt = umlService.createElement(projectId, diagramId, testEmail, createReq);
    assertEquals(stableElemId, retryAttempt.id());
    // No duplica la clase en PostgreSQL
    List<ElementoUML> inDb = elementRepo.findByDiagramaId(diagramId);
    assertEquals(1, inDb.size());

    // 3. Intento de reusar el UUID con contenido diferente produce Conflicto (409)
    SaveElement conflictingReq = new SaveElement(stableElemId, TipoElementoUML.CLASE, "ClaseConflictoDiferente", 200.0, 200.0, "{}", 2L);
    assertThrows(ConflictException.class, () ->
        umlService.createElement(projectId, diagramId, testEmail, conflictingReq)
    );
  }
}
