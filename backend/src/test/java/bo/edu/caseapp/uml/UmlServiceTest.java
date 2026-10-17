package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.error.ConflictException;
import bo.edu.caseapp.error.NotFoundException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectMember;
import bo.edu.caseapp.project.ProjectRole;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.UmlRequests.CreateDiagram;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
import bo.edu.caseapp.uml.UmlRequests.SaveRelation;
import bo.edu.caseapp.uml.UmlResponses.DiagramSummary;
import bo.edu.caseapp.uml.UmlResponses.ElementDetail;
import bo.edu.caseapp.uml.relational.RelationalModelService;
import bo.edu.caseapp.uml.relational.RelationalSchema;
import bo.edu.caseapp.uml.validation.UmlValidatorService;
import bo.edu.caseapp.uml.validation.ValidationReport;
import bo.edu.caseapp.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UmlServiceTest {
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
  private String email;

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
    User user = new User("Ana", "ana@example.com", "hash");
    project = new Project(user, "Proyecto UML", "Desc");
    org.springframework.test.util.ReflectionTestUtils.setField(project, "id", projectId);
    diagram = new DiagramaClase(project, "Diagrama 1", "Desc Diagrama");
    org.springframework.test.util.ReflectionTestUtils.setField(diagram, "id", diagramId);

    email = "ana@example.com";
  }

  @Test
  void createDiagram_success() {
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireCanEdit(projectId, email)).thenReturn(member);
    when(diagrams.save(any(DiagramaClase.class))).thenAnswer(i -> i.getArgument(0));

    DiagramSummary summary = service.createDiagram(projectId, email, new CreateDiagram("Diagrama 1", "Desc"));

    assertNotNull(summary);
    assertEquals("Diagrama 1", summary.nombre());
    verify(diagrams).save(any(DiagramaClase.class));
  }

  @Test
  void saveElement_createsAndReturnsDetail() {
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireCanEdit(projectId, email)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    SaveElement request = new SaveElement(
        null,
        TipoElementoUML.CLASE,
        "Usuario",
        100.0,
        200.0,
        "{\"attributes\":[{\"name\":\"id\",\"type\":\"Long\"}]}"
    );

    ElementDetail detail = service.saveElement(projectId, diagramId, email, request);

    assertNotNull(detail);
    assertEquals("Usuario", detail.nombre());
    assertEquals(TipoElementoUML.CLASE, detail.tipoElemento());
    assertEquals(100.0, detail.posicionX());
    assertEquals(200.0, detail.posicionY());
    assertTrue(detail.propiedadesJson().contains("attributes"));
  }

  @Test
  void saveElement_emptyClassWithoutDefaultAttributes() {
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireCanEdit(projectId, email)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    SaveElement request = new SaveElement(
        null,
        TipoElementoUML.CLASE,
        "Producto",
        150.0,
        120.0,
        "{\"description\":\"Clase para gestionar productos del catalogo\",\"attributes\":[],\"methods\":[]}"
    );

    ElementDetail detail = service.saveElement(projectId, diagramId, email, request);

    assertNotNull(detail);
    assertEquals("Producto", detail.nombre());
    assertTrue(detail.propiedadesJson().contains("\"attributes\":[]"));
    assertTrue(detail.propiedadesJson().contains("Clase para gestionar productos"));
  }

  @Test
  void deleteElement_deletesRelatedRelationsFirst() {
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireCanEdit(projectId, email)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));

    UUID elementId = UUID.randomUUID();
    ElementoUML el = new ElementoUML(diagram, TipoElementoUML.CLASE, "Cliente", 0, 0, "{}");
    when(elements.findById(elementId)).thenReturn(Optional.of(el));

    service.deleteElement(projectId, diagramId, email, elementId);

    verify(relations).deleteByElementoOrigenIdOrElementoDestinoId(elementId, elementId);
    verify(elements).delete(el);
  }

  @Test
  void saveElement_throwsForbiddenForReader() {
    when(projectService.requireCanEdit(projectId, "lector@example.com"))
        .thenThrow(new ForbiddenException("El rol LECTOR no puede editar el proyecto."));

    SaveElement request = new SaveElement(
        null,
        TipoElementoUML.CLASE,
        "Test",
        0.0,
        0.0,
        "{}"
    );

    assertThrows(ForbiddenException.class, () ->
        service.saveElement(projectId, diagramId, "lector@example.com", request)
    );
  }

  @Test
  void validateDiagram_validatesElementsAndRelations() {
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireAccess(projectId, email)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));

    ElementoUML el1 = new ElementoUML(diagram, TipoElementoUML.CLASE, "Cliente", 0, 0, "{\"attributes\":[{\"name\":\"id\",\"type\":\"UUID\"}]}");
    ElementoUML el2 = new ElementoUML(diagram, TipoElementoUML.CLASE, "Pedido", 0, 0, "{\"attributes\":[{\"name\":\"id\",\"type\":\"UUID\"}]}");

    when(elements.findByDiagramaId(diagramId)).thenReturn(List.of(el1, el2));
    when(relations.findByDiagramaId(diagramId)).thenReturn(List.of());

    ValidationReport report = service.validateDiagram(projectId, diagramId, email);

    assertNotNull(report);
    assertTrue(report.valid());
    assertEquals(0, report.errorCount());
  }

  @Test
  void generateRelationalSchema_generatesTablesAndDdl() {
    ProjectMember member = new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO);
    when(projectService.requireAccess(projectId, email)).thenReturn(member);
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));

    ElementoUML cliente = new ElementoUML(diagram, TipoElementoUML.CLASE, "Cliente", 0, 0, "{\"attributes\":[{\"name\":\"nombre\",\"type\":\"String\"}]}");
    org.springframework.test.util.ReflectionTestUtils.setField(cliente, "id", UUID.randomUUID());
    ElementoUML pedido = new ElementoUML(diagram, TipoElementoUML.CLASE, "Pedido", 0, 0, "{\"attributes\":[{\"name\":\"total\",\"type\":\"Double\"}]}");
    org.springframework.test.util.ReflectionTestUtils.setField(pedido, "id", UUID.randomUUID());

    RelacionUML rel = new RelacionUML(diagram, cliente, pedido, "realiza", TipoRelacionUML.ASOCIACION, "1", "*");
    org.springframework.test.util.ReflectionTestUtils.setField(rel, "id", UUID.randomUUID());

    when(elements.findByDiagramaId(diagramId)).thenReturn(List.of(cliente, pedido));
    when(relations.findByDiagramaId(diagramId)).thenReturn(List.of(rel));

    RelationalSchema schema = service.generateRelationalSchema(projectId, diagramId, email);

    assertNotNull(schema);
    assertEquals(2, schema.tables().size());
    assertTrue(schema.sqlDdl().contains("CREATE TABLE cliente"));
    assertTrue(schema.sqlDdl().contains("CREATE TABLE pedido"));
    assertTrue(schema.sqlDdl().contains("cliente_id UUID"));
  }

  @Test
  void saveElementUsesClientIdForRetrySafeOfflineCreation() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    UUID clientId = UUID.randomUUID();
    ElementoUML alreadyCreated = new ElementoUML(
        clientId, diagram, TipoElementoUML.CLASE, "Cliente", 5.0, 8.0, "{}");
    when(elements.findById(clientId)).thenReturn(Optional.empty(), Optional.of(alreadyCreated));
    when(elements.saveAndFlush(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

    service.createElement(projectId, diagramId, email,
        new SaveElement(clientId, TipoElementoUML.CLASE, "Cliente", 5.0, 8.0, "{}"));
    ElementDetail retried = service.createElement(projectId, diagramId, email,
        new SaveElement(clientId, TipoElementoUML.CLASE, "Cliente", 5.0, 8.0, "{}"));

    assertEquals(clientId, retried.id());
    verify(elements, times(1)).saveAndFlush(argThat(element -> clientId.equals(element.getId()) && element.isNew()));
    verify(elements, never()).save(any(ElementoUML.class));
  }

  @Test
  void saveRelationUsesClientIdForRetrySafeOfflineCreation() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UUID relationId = UUID.randomUUID();
    ElementoUML source = new ElementoUML(sourceId, diagram, TipoElementoUML.CLASE, "A", 1, 1, "{}");
    ElementoUML target = new ElementoUML(targetId, diagram, TipoElementoUML.CLASE, "B", 2, 2, "{}");
    RelacionUML alreadyCreated = new RelacionUML(
        relationId, diagram, source, target, "usa", TipoRelacionUML.ASOCIACION, null, null);
    when(elements.findById(sourceId)).thenReturn(Optional.of(source));
    when(elements.findById(targetId)).thenReturn(Optional.of(target));
    when(relations.findById(relationId)).thenReturn(Optional.empty(), Optional.of(alreadyCreated));
    when(relations.saveAndFlush(any(RelacionUML.class))).thenAnswer(i -> i.getArgument(0));
    SaveRelation request = new SaveRelation(
        relationId, sourceId, targetId, "usa", TipoRelacionUML.ASOCIACION, null, null);

    service.createRelation(projectId, diagramId, email, request);
    var retried = service.createRelation(projectId, diagramId, email, request);

    assertEquals(relationId, retried.id());
    verify(relations, times(1)).saveAndFlush(argThat(relation -> relationId.equals(relation.getId()) && relation.isNew()));
    verify(relations, never()).save(any(RelacionUML.class));
  }

  @Test
  void saveElementUpdatesExistingElementWithoutChangingIdentity() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    UUID elementId = UUID.randomUUID();
    ElementoUML existing = new ElementoUML(elementId, diagram, TipoElementoUML.CLASE, "Antes", 1, 2, "{}");
    when(elements.findById(elementId)).thenReturn(Optional.of(existing));
    when(elements.save(existing)).thenReturn(existing);

    ElementDetail saved = service.updateElement(projectId, diagramId, email, elementId,
        new SaveElement(elementId, TipoElementoUML.CLASE, "Después", 3.0, 4.0, "{}"));

    assertEquals(elementId, saved.id());
    assertEquals("Después", saved.nombre());
    verify(elements).save(existing);
    verify(elements, never()).saveAndFlush(any());
  }

  @Test
  void createElementRejectsExistingIdWithDifferentContentInsteadOfUpdating() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    UUID elementId = UUID.randomUUID();
    ElementoUML existing = new ElementoUML(elementId, diagram, TipoElementoUML.CLASE, "Original", 1, 2, "{}");
    when(elements.findById(elementId)).thenReturn(Optional.of(existing));

    assertThrows(ConflictException.class, () -> service.createElement(projectId, diagramId, email,
        new SaveElement(elementId, TipoElementoUML.CLASE, "Different operation", 1.0, 2.0, "{}")));
    assertEquals("Original", existing.getNombre());
    verify(elements, never()).save(any());
    verify(elements, never()).saveAndFlush(any());
  }

  @Test
  void createRelationRejectsExistingIdWithDifferentContentInsteadOfUpdating() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UUID relationId = UUID.randomUUID();
    ElementoUML source = new ElementoUML(sourceId, diagram, TipoElementoUML.CLASE, "A", 0, 0, "{}");
    ElementoUML target = new ElementoUML(targetId, diagram, TipoElementoUML.CLASE, "B", 0, 0, "{}");
    RelacionUML existing = new RelacionUML(relationId, diagram, source, target, "original",
        TipoRelacionUML.ASOCIACION, null, null);
    when(elements.findById(sourceId)).thenReturn(Optional.of(source));
    when(elements.findById(targetId)).thenReturn(Optional.of(target));
    when(relations.findById(relationId)).thenReturn(Optional.of(existing));

    assertThrows(ConflictException.class, () -> service.createRelation(projectId, diagramId, email,
        new SaveRelation(relationId, sourceId, targetId, "different operation",
            TipoRelacionUML.ASOCIACION, null, null)));
    assertEquals("original", existing.getNombreRelacion());
    verify(relations, never()).save(any());
    verify(relations, never()).saveAndFlush(any());
  }

  @Test
  void saveElementRejectsIdOwnedByAnotherDiagram() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    DiagramaClase otherDiagram = new DiagramaClase(project, "Otro", "");
    org.springframework.test.util.ReflectionTestUtils.setField(otherDiagram, "id", UUID.randomUUID());
    UUID elementId = UUID.randomUUID();
    when(elements.findById(elementId)).thenReturn(Optional.of(
        new ElementoUML(elementId, otherDiagram, TipoElementoUML.CLASE, "Privado", 0, 0, "{}")));

    assertThrows(BadRequestException.class, () -> service.saveElement(projectId, diagramId, email,
        new SaveElement(elementId, TipoElementoUML.CLASE, "Ataque", 0.0, 0.0, "{}")));
    verify(elements, never()).save(any());
    verify(elements, never()).saveAndFlush(any());
  }

  @Test
  void saveElementMapsPrimaryKeyRaceToCoherentConflict() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    when(elements.saveAndFlush(any(ElementoUML.class)))
        .thenThrow(new org.springframework.dao.DataIntegrityViolationException("database detail"));

    ConflictException conflict = assertThrows(ConflictException.class, () -> service.createElement(
        projectId, diagramId, email,
        new SaveElement(UUID.randomUUID(), TipoElementoUML.CLASE, "Clase", 0.0, 0.0, "{}")));
    assertFalse(conflict.getMessage().contains("database detail"));
  }

  @Test
  void saveRelationRejectsEndpointFromAnotherDiagram() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    DiagramaClase otherDiagram = new DiagramaClase(project, "Otro", "");
    org.springframework.test.util.ReflectionTestUtils.setField(otherDiagram, "id", UUID.randomUUID());
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    when(elements.findById(sourceId)).thenReturn(Optional.of(
        new ElementoUML(sourceId, diagram, TipoElementoUML.CLASE, "Local", 0, 0, "{}")));
    when(elements.findById(targetId)).thenReturn(Optional.of(
        new ElementoUML(targetId, otherDiagram, TipoElementoUML.CLASE, "Ajeno", 0, 0, "{}")));

    assertThrows(BadRequestException.class, () -> service.saveRelation(projectId, diagramId, email,
        new SaveRelation(UUID.randomUUID(), sourceId, targetId, "usa", TipoRelacionUML.ASOCIACION,
            null, null)));
    verify(relations, never()).save(any());
    verify(relations, never()).saveAndFlush(any());
  }

  @Test
  void saveRelationRejectsIdOwnedByAnotherDiagram() {
    when(projectService.requireCanEdit(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    UUID sourceId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();
    UUID relationId = UUID.randomUUID();
    ElementoUML source = new ElementoUML(sourceId, diagram, TipoElementoUML.CLASE, "A", 0, 0, "{}");
    ElementoUML target = new ElementoUML(targetId, diagram, TipoElementoUML.CLASE, "B", 0, 0, "{}");
    DiagramaClase otherDiagram = new DiagramaClase(project, "Otro", "");
    org.springframework.test.util.ReflectionTestUtils.setField(otherDiagram, "id", UUID.randomUUID());
    when(elements.findById(sourceId)).thenReturn(Optional.of(source));
    when(elements.findById(targetId)).thenReturn(Optional.of(target));
    when(relations.findById(relationId)).thenReturn(Optional.of(new RelacionUML(
        relationId, otherDiagram, source, target, "usa", TipoRelacionUML.ASOCIACION, null, null)));

    assertThrows(BadRequestException.class, () -> service.saveRelation(projectId, diagramId, email,
        new SaveRelation(relationId, sourceId, targetId, "usa", TipoRelacionUML.ASOCIACION,
            null, null)));
    verify(relations, never()).save(any());
    verify(relations, never()).saveAndFlush(any());
  }

  @Test
  void requireDiagramAccessAllowsProjectMemberForDiagramInThatProject() {
    when(projectService.requireAccess(projectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));

    assertDoesNotThrow(() -> service.requireDiagramAccess(projectId, diagramId, email));
  }

  @Test
  void requireDiagramAccessRejectsDiagramFromDifferentProject() {
    UUID otherProjectId = UUID.randomUUID();
    when(projectService.requireAccess(otherProjectId, email)).thenReturn(
        new ProjectMember(project, project.getCreator(), ProjectRole.PROPIETARIO));
    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));

    assertThrows(NotFoundException.class, () -> service.requireDiagramAccess(otherProjectId, diagramId, email));
  }

  @Test
  void requireDiagramAccessRejectsNonMemberBeforeLoadingDiagram() {
    when(projectService.requireAccess(projectId, "outsider@example.com"))
        .thenThrow(new ForbiddenException("No tienes acceso a este proyecto."));

    assertThrows(ForbiddenException.class,
        () -> service.requireDiagramAccess(projectId, diagramId, "outsider@example.com"));
    verifyNoInteractions(diagrams);
  }
}
