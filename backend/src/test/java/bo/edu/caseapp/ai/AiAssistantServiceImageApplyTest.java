package bo.edu.caseapp.ai;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.ai.AiRequests.*;
import bo.edu.caseapp.ai.AiResponses.ImageReconstructionResponse;
import bo.edu.caseapp.project.Project;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.*;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
import bo.edu.caseapp.uml.UmlRequests.SaveRelation;
import bo.edu.caseapp.uml.UmlResponses.*;
import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AiAssistantServiceImageApplyTest {
  @Test
  void addsValidatedClassesAndConnectedRelationToEditableDiagram() {
    UUID projectId = UUID.randomUUID(), diagramId = UUID.randomUUID();
    User user = new User("Ana", "ana@example.com", "hash");
    Project project = new Project(user, "Proyecto", "");
    ReflectionTestUtils.setField(project, "id", projectId);
    DiagramaClase diagram = new DiagramaClase(project, "Diagrama", "");
    ReflectionTestUtils.setField(diagram, "id", diagramId);

    DiagramaClaseRepository diagrams = mock(DiagramaClaseRepository.class);
    ElementoUMLRepository elements = mock(ElementoUMLRepository.class);
    RelacionUMLRepository relations = mock(RelacionUMLRepository.class);
    InteraccionIaRepository interactions = mock(InteraccionIaRepository.class);
    ProjectService projects = mock(ProjectService.class);
    UserRepository users = mock(UserRepository.class);
    UmlService uml = mock(UmlService.class);
    bo.edu.caseapp.uml.relational.SqlDdlParserService sql = mock(bo.edu.caseapp.uml.relational.SqlDdlParserService.class);
    bo.edu.caseapp.uml.collab.UmlWebSocketHandler socket = mock(bo.edu.caseapp.uml.collab.UmlWebSocketHandler.class);

    when(diagrams.findById(diagramId)).thenReturn(Optional.of(diagram));
    when(users.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(user));
    when(elements.findByDiagramaId(diagramId)).thenReturn(List.of());
    when(relations.findByDiagramaId(diagramId)).thenReturn(List.of());
    Map<String, UUID> ids = new HashMap<>();
    List<ElementDetail> persistedElements = new ArrayList<>();
    List<RelationDetail> persistedRelations = new ArrayList<>();
    when(uml.saveElement(eq(projectId), eq(diagramId), eq("ana@example.com"), any()))
        .thenAnswer(call -> {
          SaveElement request = call.getArgument(3);
          UUID id = UUID.randomUUID(); ids.put(request.nombre(), id);
          ElementDetail saved = new ElementDetail(id, diagramId, request.tipoElemento(), request.nombre(), request.posicionX(), request.posicionY(), request.propiedadesJson());
          persistedElements.add(saved); return saved;
        });
    when(uml.saveRelation(eq(projectId), eq(diagramId), eq("ana@example.com"), any()))
        .thenAnswer(call -> {
          SaveRelation request = call.getArgument(3);
          RelationDetail saved = new RelationDetail(UUID.randomUUID(), diagramId, request.elementoOrigen(), request.elementoDestino(), request.nombreRelacion(), request.tipoRelacion(), request.multiplicidadOrigen(), request.multiplicidadDestino());
          persistedRelations.add(saved); return saved;
        });
    when(uml.getDiagram(projectId, diagramId, "ana@example.com"))
        .thenAnswer(call -> new DiagramFullView(null, List.copyOf(persistedElements), List.copyOf(persistedRelations)));

    AiAssistantService service = new AiAssistantService(diagrams, elements, relations, interactions, projects, users, uml, sql, socket, "", "gemini-3.8-flash");
    ImageModel model = new ImageModel(List.of(
        new ImageClass("Cliente", TipoElementoUML.CLASE, .98, 100.0, 120.0,
            List.of(new ImageAttribute("-", "nombre", "String", .9)),
            List.of(new ImageMethod("+", "buscar", "Pedido", "id: Long", .9))),
        new ImageClass("Pedido", TipoElementoUML.CLASE, .96, 500.0, 120.0, List.of(), List.of())),
        List.of(new ImageRelation("Cliente", "Pedido", "realiza", TipoRelacionUML.ASOCIACION, "1", "*", .95)), List.of());

    ImageReconstructionResponse result = service.applyImageModel(projectId, diagramId, "ana@example.com", new ApplyImageModelRequest(model, ImageImportMode.ADD, "uml.png"));

    assertTrue(result.success());
    assertEquals(2, result.classesDetected());
    assertEquals(1, result.relationsDetected());
    assertEquals(2, result.updatedDiagram().elements().size());
    assertEquals(1, result.updatedDiagram().relations().size());
    assertTrue(persistedElements.getFirst().propiedadesJson().contains("id: Long"));
    RelationDetail relation = result.updatedDiagram().relations().getFirst();
    assertEquals(ids.get("Cliente"), relation.elementoOrigen());
    assertEquals(ids.get("Pedido"), relation.elementoDestino());
    assertEquals("1", relation.multiplicidadOrigen());
    assertEquals("*", relation.multiplicidadDestino());
    verify(uml, times(2)).saveElement(eq(projectId), eq(diagramId), eq("ana@example.com"), any());
    verify(uml).saveRelation(eq(projectId), eq(diagramId), eq("ana@example.com"), any());
    verify(elements, never()).deleteByDiagramaId(diagramId);
  }
}
