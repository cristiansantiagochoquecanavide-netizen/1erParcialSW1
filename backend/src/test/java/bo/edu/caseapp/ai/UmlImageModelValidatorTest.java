package bo.edu.caseapp.ai;

import static org.junit.jupiter.api.Assertions.*;

import bo.edu.caseapp.ai.AiRequests.*;
import bo.edu.caseapp.error.BadRequestException;
import java.util.List;
import org.junit.jupiter.api.Test;

class UmlImageModelValidatorTest {
  @Test
  void parsesClassWithAttributesAndSpanishIdentifiers() {
    ImageModel model = validate("""
        {"classes":[{"name":"Artículo","type":"CLASE","confidence":0.98,"x":160,"y":120,"attributes":[{"name":"precio","type":"Decimal","visibility":"-","confidence":0.94}],"methods":[]}],"relations":[],"warnings":[]}
        """);
    assertEquals("Artículo", model.classes().getFirst().name());
    assertEquals("precio", model.classes().getFirst().attributes().getFirst().name());
    assertEquals(160.0, model.classes().getFirst().x());
  }

  @Test
  void parsesSeveralClassesAndAssociation() {
    ImageModel model = validate("""
        {"classes":[{"name":"Cliente","type":"CLASE","confidence":0.9,"x":100,"y":100,"attributes":[],"methods":[]},{"name":"Pedido","type":"CLASE","confidence":0.9,"x":500,"y":100,"attributes":[],"methods":[]}],"relations":[{"source":"Cliente","target":"Pedido","type":"ASOCIACION","name":"realiza","multiplicitySource":"1","multiplicityTarget":"*","confidence":0.91}],"warnings":[]}
        """);
    assertEquals(2, model.classes().size());
    assertEquals(1, model.relations().size());
  }

  @Test
  void preservesInheritanceAndAbstractTypes() {
    ImageModel model = validate("""
        {"classes":[{"name":"Animal","type":"CLASE_ABSTRACTA","confidence":0.9,"x":100,"y":100,"attributes":[],"methods":[]},{"name":"Perro","type":"CLASE","confidence":0.9,"x":100,"y":400,"attributes":[],"methods":[]}],"relations":[{"source":"Perro","target":"Animal","type":"HERENCIA","name":null,"multiplicitySource":null,"multiplicityTarget":null,"confidence":0.9}],"warnings":[]}
        """);
    assertEquals("CLASE_ABSTRACTA", model.classes().getFirst().type().name());
    assertEquals("HERENCIA", model.relations().getFirst().type().name());
  }

  @Test
  void preservesMultiplicitiesAtTheirEndpoints() {
    ImageModel model = validate("""
        {"classes":[{"name":"Cliente","type":"CLASE","confidence":0.9,"x":100,"y":100,"attributes":[],"methods":[]},{"name":"Pedido","type":"CLASE","confidence":0.9,"x":500,"y":100,"attributes":[],"methods":[]}],"relations":[{"source":"Cliente","target":"Pedido","type":"ASOCIACION","name":null,"multiplicitySource":"1","multiplicityTarget":"*","confidence":0.9}],"warnings":[]}
        """);
    assertEquals("1", model.relations().getFirst().multiplicitySource());
    assertEquals("*", model.relations().getFirst().multiplicityTarget());
  }

  @Test
  void preservesMethodsAndParameters() {
    ImageModel model = validate("""
        {"classes":[{"name":"Pedido","type":"CLASE","confidence":0.9,"x":100,"y":100,"attributes":[],"methods":[{"name":"buscar","returnType":"Pedido","parameters":"id: Long, activo: Boolean","visibility":"+","confidence":0.88}]}],"relations":[],"warnings":[]}
        """);
    ImageMethod method = model.classes().getFirst().methods().getFirst();
    assertEquals("buscar", method.name());
    assertEquals("id: Long, activo: Boolean", method.parameters());
  }

  @Test
  void omitsLowConfidenceElementsAndReturnsWarning() {
    ImageModel model = validate("""
        {"classes":[{"name":"Ilegible","type":"CLASE","confidence":0.2,"x":null,"y":null,"attributes":[],"methods":[]},{"name":"Cliente","type":"CLASE","confidence":0.95,"x":null,"y":null,"attributes":[],"methods":[]}],"relations":[],"warnings":["Hay una zona parcialmente oculta."]}
        """);
    assertEquals(1, model.classes().size());
    assertTrue(model.warnings().stream().anyMatch(w -> w.contains("baja confianza")));
    assertTrue(model.warnings().stream().anyMatch(w -> w.contains("parcialmente oculta")));
  }

  @Test
  void rejectsMalformedOrUnstructuredResponsesBeforeApply() {
    assertThrows(BadRequestException.class, () -> validate("```json {\"classes\":[]} ```"));
    assertThrows(BadRequestException.class, () -> validate("{\"classes\":[{\"name\":\"A\"}],\"relations\":[],\"warnings\":[]}"));
  }

  @Test
  void warnsAndSkipsRelationsWithUnknownEndpoints() {
    ImageModel model = validate("""
        {"classes":[{"name":"Cliente","type":"CLASE","confidence":0.95,"x":100,"y":100,"attributes":[],"methods":[]}],"relations":[{"source":"Cliente","target":"Pedido","type":"ASOCIACION","name":null,"multiplicitySource":"1","multiplicityTarget":"*","confidence":0.9}],"warnings":[]}
        """);
    assertTrue(model.relations().isEmpty());
    assertTrue(model.warnings().stream().anyMatch(w -> w.contains("no existe")));
  }

  private static ImageModel validate(String json) { return UmlImageModelValidator.parseAndValidate(json); }
}
