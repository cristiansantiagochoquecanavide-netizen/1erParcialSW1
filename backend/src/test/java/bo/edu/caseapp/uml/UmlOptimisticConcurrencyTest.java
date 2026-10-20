package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.error.ConflictException;
import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectMember;
import bo.edu.caseapp.project.ProjectRole;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
import bo.edu.caseapp.uml.UmlRequests.SaveRelation;
import bo.edu.caseapp.uml.UmlResponses.ElementDetail;
import bo.edu.caseapp.uml.collab.UmlWebSocketHandler;
import bo.edu.caseapp.uml.relational.RelationalModelService;
import bo.edu.caseapp.uml.validation.UmlValidatorService;
import bo.edu.caseapp.user.User;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class UmlOptimisticConcurrencyTest {

  private DiagramaClaseRepository diagrams;
  private ElementoUMLRepository elements;
  private RelacionUMLRepository relations;
  private ProjectService projectService;
  private UmlWebSocketHandler collabHandler;
  private UmlService service;

  private UUID projectId;
  private UUID diagramId;
  private Project project;
  private DiagramaClase diagram;
  private String userEmail;

  @BeforeEach
  void setUp() {
    diagrams = mock(DiagramaClaseRepository.class);
    elements = mock(ElementoUMLRepository.class);
    relations = mock(RelacionUMLRepository.class);
    projectService = mock(ProjectService.class);
    collabHandler = mock(UmlWebSocketHandler.class);

    service = new UmlService(
        diagrams,
        elements,
        relations,
        projectService,
        new UmlValidatorService(),
        new RelationalModelService(),
        collabHandler
    );

    projectId = UUID.randomUUID();
    diagramId = UUID.randomUUID();
    User owner = new User("Carlos", "carlos@example.com", "hash");
    project = new Project(owner, "Proyecto Concurrencia", "Desc");
    ReflectionTestUtils.setField(project, "id", projectId);

    diagram = new DiagramaClase(project, "Diagrama Principal", "Desc");
    diagram.setRevision(1L);
    ReflectionTestUtils.setField(diagram, "id", diagramId);

    userEmail = "carlos@example.com";
    when(projectService.requireCanEdit(projectId, userEmail))
        .thenReturn(new ProjectMember(project, owner, ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void validOperation_incrementsRevisionAndBroadcastsConfirmedRevision() {
    when(diagrams.incrementRevisionIfMatches(diagramId, 1L)).thenReturn(1);
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    SaveElement request = new SaveElement(
        UUID.randomUUID(),
        TipoElementoUML.CLASE,
        "Usuario",
        100.0,
        200.0,
        "{}",
        1L
    );

    ElementDetail saved = service.createElement(projectId, diagramId, userEmail, request);

    assertNotNull(saved);
    assertEquals(2L, diagram.getRevision());
    verify(diagrams).incrementRevisionIfMatches(diagramId, 1L);
    verify(elements).saveAndFlush(any(ElementoUML.class));
    verify(collabHandler).broadcastDiagramChanged(diagramId, "ELEMENT_SAVED", 2L);
  }

  @Test
  void sequentialOperations_withMatchingRevisions_succeed() {
    when(diagrams.incrementRevisionIfMatches(diagramId, 1L)).thenReturn(1);
    when(diagrams.incrementRevisionIfMatches(diagramId, 2L)).thenReturn(1);
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    SaveElement op1 = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "ClaseA", 0.0, 0.0, "{}", 1L);
    service.createElement(projectId, diagramId, userEmail, op1);
    assertEquals(2L, diagram.getRevision());

    SaveElement op2 = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "ClaseB", 50.0, 50.0, "{}", 2L);
    service.createElement(projectId, diagramId, userEmail, op2);
    assertEquals(3L, diagram.getRevision());

    verify(diagrams).incrementRevisionIfMatches(diagramId, 1L);
    verify(diagrams).incrementRevisionIfMatches(diagramId, 2L);
  }

  @Test
  void concurrentOperations_racingOnSameRevision_onlyOneWins() throws Exception {
    AtomicLong atomicRevision = new AtomicLong(1L);
    when(diagrams.incrementRevisionIfMatches(eq(diagramId), anyLong())).thenAnswer(invocation -> {
      long expected = invocation.getArgument(1);
      // Atomic CAS mimicking conditional UPDATE: SET revision = revision + 1 WHERE revision = expected
      if (atomicRevision.compareAndSet(expected, expected + 1)) {
        return 1;
      }
      return 0;
    });
    when(diagrams.findRevisionById(diagramId)).thenAnswer(i -> Optional.of(atomicRevision.get()));
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch startLatch = new CountDownLatch(1);

    SaveElement reqUserA = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "ClaseUserA", 10.0, 10.0, "{}", 1L);
    SaveElement reqUserB = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "ClaseUserB", 20.0, 20.0, "{}", 1L);

    Future<ElementDetail> taskA = pool.submit(() -> {
      startLatch.await();
      return service.createElement(projectId, diagramId, userEmail, reqUserA);
    });

    Future<ElementDetail> taskB = pool.submit(() -> {
      startLatch.await();
      return service.createElement(projectId, diagramId, userEmail, reqUserB);
    });

    startLatch.countDown();

    boolean userASucceeded = false;
    boolean userBSucceeded = false;
    boolean conflictThrown = false;

    try {
      taskA.get();
      userASucceeded = true;
    } catch (Exception e) {
      if (e.getCause() instanceof ConflictException) conflictThrown = true;
    }

    try {
      taskB.get();
      userBSucceeded = true;
    } catch (Exception e) {
      if (e.getCause() instanceof ConflictException) conflictThrown = true;
    }

    pool.shutdown();

    // Exactly one winner and one conflict
    assertTrue(userASucceeded ^ userBSucceeded, "Exactamente una de las dos escrituras concurrentes debe triunfar");
    assertTrue(conflictThrown, "La operación perdedora debe recibir ConflictException (HTTP 409)");
    assertEquals(2L, atomicRevision.get(), "La revisión solo debe incrementarse en 1");
  }

  @Test
  void rejectedOperation_doesNotMutateModelOrPersistElement() {
    when(diagrams.incrementRevisionIfMatches(diagramId, 1L)).thenReturn(0);
    when(diagrams.findRevisionById(diagramId)).thenReturn(Optional.of(2L));

    SaveElement obsoleteRequest = new SaveElement(
        UUID.randomUUID(),
        TipoElementoUML.CLASE,
        "ClaseObsoleta",
        10.0,
        10.0,
        "{}",
        1L
    );

    ConflictException ex = assertThrows(ConflictException.class, () ->
        service.createElement(projectId, diagramId, userEmail, obsoleteRequest)
    );

    assertTrue(ex.getMessage().contains("Conflicto de concurrencia"));
    verify(elements, never()).save(any());
    verify(elements, never()).saveAndFlush(any());
    verify(collabHandler, never()).broadcastDiagramChanged(any(), any(), any());
  }

  @Test
  void obsoleteRevision_rejectedWithoutOverwritingConfirmedChanges() {
    diagram.setRevision(5L);
    when(diagrams.incrementRevisionIfMatches(diagramId, 3L)).thenReturn(0);
    when(diagrams.findRevisionById(diagramId)).thenReturn(Optional.of(5L));

    SaveElement req = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "CambioDesfasado", 10.0, 10.0, "{}", 3L);

    ConflictException ex = assertThrows(ConflictException.class, () ->
        service.createElement(projectId, diagramId, userEmail, req));

    assertTrue(ex.getMessage().contains("revisión esperada: 3"));
    assertTrue(ex.getMessage().contains("revisión actual: 5"));
    verify(elements, never()).save(any());
  }

  @Test
  void idempotentRetry_withIdenticalUuidAndContent_returnsSuccessWithoutIncrementingRevision() {
    UUID stableElementId = UUID.randomUUID();
    ElementoUML existingElement = new ElementoUML(
        stableElementId,
        diagram,
        TipoElementoUML.CLASE,
        "ClaseExistente",
        50.0,
        80.0,
        "{\"description\":\"\"}"
    );

    when(elements.findById(stableElementId)).thenReturn(Optional.of(existingElement));

    SaveElement retryRequest = new SaveElement(
        stableElementId,
        TipoElementoUML.CLASE,
        "ClaseExistente",
        50.0,
        80.0,
        "{\"description\":\"\"}",
        1L
    );

    ElementDetail result = service.saveElement(projectId, diagramId, userEmail, retryRequest);

    assertNotNull(result);
    assertEquals("ClaseExistente", result.nombre());
    // Idempotent retry must NOT call incrementRevision nor re-save
    verify(diagrams, never()).incrementRevisionIfMatches(any(), anyLong());
    verify(diagrams, never()).incrementRevisionUnconditional(any());
    verify(elements, never()).save(any());
    verify(elements, never()).saveAndFlush(any());
  }

  @Test
  void unauthorizedAccess_isRejectedWithoutAccessingRevision() {
    when(projectService.requireCanEdit(projectId, "intruder@example.com"))
        .thenThrow(new ForbiddenException("No tienes permisos de edición"));

    SaveElement request = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "Malicioso", 0.0, 0.0, "{}", 1L);

    assertThrows(ForbiddenException.class, () ->
        service.createElement(projectId, diagramId, "intruder@example.com", request));

    verify(diagrams, never()).incrementRevisionIfMatches(any(), anyLong());
    verify(diagrams, never()).incrementRevisionUnconditional(any());
  }

  @Test
  void relationEndpoints_fromForeignDiagram_rejectedWithoutIncrementingRevision() {
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    DiagramaClase foreignDiagram = new DiagramaClase(project, "Otro", "Desc");
    ReflectionTestUtils.setField(foreignDiagram, "id", UUID.randomUUID());

    ElementoUML localSource = new ElementoUML(sourceId, diagram, TipoElementoUML.CLASE, "Origen", 0.0, 0.0, "{}");
    ElementoUML foreignTarget = new ElementoUML(targetId, foreignDiagram, TipoElementoUML.CLASE, "DestinoAjeno", 0.0, 0.0, "{}");

    when(elements.findById(sourceId)).thenReturn(Optional.of(localSource));
    when(elements.findById(targetId)).thenReturn(Optional.of(foreignTarget));

    SaveRelation relationReq = new SaveRelation(
        UUID.randomUUID(),
        sourceId,
        targetId,
        "asociacion",
        TipoRelacionUML.ASOCIACION,
        "1",
        "1",
        1L
    );

    assertThrows(BadRequestException.class, () ->
        service.createRelation(projectId, diagramId, userEmail, relationReq));

    verify(diagrams, never()).incrementRevisionIfMatches(any(), anyLong());
    verify(relations, never()).save(any());
  }

  @Test
  void transactionRollback_doesNotBroadcastWebSocketEvent() {
    TransactionSynchronizationManager.initSynchronization();

    when(diagrams.incrementRevisionIfMatches(diagramId, 1L)).thenReturn(1);
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    SaveElement request = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "ClaseRollback", 0.0, 0.0, "{}", 1L);
    service.createElement(projectId, diagramId, userEmail, request);

    // Simulate transaction rolling back instead of committing
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    // Verify WebSocket broadcast was NEVER invoked because transaction rolled back!
    verify(collabHandler, never()).broadcastDiagramChanged(any(), any(), any());
  }

  @Test
  void transactionCommit_broadcastsWebSocketEvent() {
    TransactionSynchronizationManager.initSynchronization();

    when(diagrams.incrementRevisionIfMatches(diagramId, 1L)).thenReturn(1);
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    SaveElement request = new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "ClaseCommit", 0.0, 0.0, "{}", 1L);
    service.createElement(projectId, diagramId, userEmail, request);

    // Simulate transaction committing successfully
    for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }

    verify(collabHandler).broadcastDiagramChanged(diagramId, "ELEMENT_SAVED", 2L);
  }
}
