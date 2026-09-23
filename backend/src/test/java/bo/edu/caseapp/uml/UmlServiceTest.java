package bo.edu.caseapp.uml;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.error.ForbiddenException;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectMember;
import bo.edu.caseapp.project.ProjectRole;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.UmlRequests.CreateDiagram;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
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
    when(elements.save(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

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
    when(elements.save(any(ElementoUML.class))).thenAnswer(i -> i.getArgument(0));

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
}
