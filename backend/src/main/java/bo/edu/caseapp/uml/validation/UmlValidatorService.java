package bo.edu.caseapp.uml.validation;

import bo.edu.caseapp.uml.ElementoUML;
import bo.edu.caseapp.uml.RelacionUML;
import bo.edu.caseapp.uml.TipoElementoUML;
import bo.edu.caseapp.uml.TipoRelacionUML;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class UmlValidatorService {
  private final ObjectMapper objectMapper = new ObjectMapper();

  public ValidationReport validate(List<ElementoUML> elements, List<RelacionUML> relations) {
    List<ValidationIssue> issues = new ArrayList<>();
    Map<UUID, ElementoUML> elementMap = new HashMap<>();
    Map<String, UUID> nameMap = new HashMap<>();

    // 1. Validar Clases y Nombres
    for (ElementoUML el : elements) {
      elementMap.put(el.getId(), el);
      String rawName = el.getNombre() != null ? el.getNombre().trim() : "";

      if (rawName.isBlank()) {
        issues.add(new ValidationIssue(
            "ERROR",
            el.getId(),
            "Sin nombre",
            "El elemento no tiene un nombre asignado.",
            "Asigne un nombre representativo en PascalCase (ej. Cliente, Producto)."
        ));
      } else {
        String lower = rawName.toLowerCase(Locale.ROOT);
        if (nameMap.containsKey(lower)) {
          issues.add(new ValidationIssue(
              "ERROR",
              el.getId(),
              rawName,
              "Existe más de un elemento con el nombre '" + rawName + "'.",
              "Los nombres de las clases en el mismo diagrama deben ser únicos."
          ));
        } else {
          nameMap.put(lower, el.getId());
        }

        if (!rawName.matches("^[A-Za-z_][A-Za-z0-9_]*$")) {
          issues.add(new ValidationIssue(
              "WARNING",
              el.getId(),
              rawName,
              "El nombre '" + rawName + "' contiene caracteres no convencionales para identificadores de software.",
              "Use solo letras, números y guiones bajos (ej. MiClase)."
          ));
        }
      }

      // Validar atributos y métodos desde propiedades_json
      validatePropertiesJson(el, issues);
    }

    // 2. Validar Relaciones
    Map<UUID, List<UUID>> inheritanceGraph = new HashMap<>();

    for (RelacionUML rel : relations) {
      ElementoUML origen = rel.getElementoOrigen();
      ElementoUML destino = rel.getElementoDestino();

      if (origen == null || !elementMap.containsKey(origen.getId())) {
        issues.add(new ValidationIssue(
            "ERROR",
            rel.getId(),
            "Relación",
            "La relación tiene un elemento de origen no válido o inexistente.",
            "Elimine o reconecte la relación a un elemento existente."
        ));
        continue;
      }

      if (destino == null || !elementMap.containsKey(destino.getId())) {
        issues.add(new ValidationIssue(
            "ERROR",
            rel.getId(),
            "Relación",
            "La relación tiene un elemento de destino no válido o inexistente.",
            "Elimine o reconecte la relación a un elemento existente."
        ));
        continue;
      }

      // Auto-relación en herencia
      if (rel.getTipoRelacion() == TipoRelacionUML.HERENCIA) {
        if (origen.getId().equals(destino.getId())) {
          issues.add(new ValidationIssue(
              "ERROR",
              origen.getId(),
              origen.getNombre(),
              "Una clase no puede heredar de sí misma.",
              "Elimine la relación de herencia reflexiva."
          ));
        } else {
          inheritanceGraph.computeIfAbsent(origen.getId(), k -> new ArrayList<>()).add(destino.getId());
        }
      }

      // Validar formato de multiplicidades si existen
      validateMultiplicity(rel.getId(), origen.getNombre(), rel.getMultiplicidadOrigen(), "origen", issues);
      validateMultiplicity(rel.getId(), destino.getNombre(), rel.getMultiplicidadDestino(), "destino", issues);
    }

    // 3. Detectar ciclos de herencia circular (ej. A -> B -> A)
    detectInheritanceCycles(inheritanceGraph, elementMap, issues);

    int errorCount = (int) issues.stream().filter(i -> "ERROR".equals(i.severity())).count();
    int warningCount = (int) issues.stream().filter(i -> "WARNING".equals(i.severity())).count();
    boolean valid = errorCount == 0;

    return new ValidationReport(valid, errorCount, warningCount, issues);
  }

  private void validatePropertiesJson(ElementoUML el, List<ValidationIssue> issues) {
    if (el.getPropiedadesJson() == null || el.getPropiedadesJson().isBlank()) {
      return;
    }

    try {
      JsonNode root = objectMapper.readTree(el.getPropiedadesJson());
      JsonNode attributes = root.path("attributes");

      Set<String> attrNames = new HashSet<>();
      boolean hasId = false;

      if (attributes.isArray()) {
        for (JsonNode attr : attributes) {
          String name = attr.path("name").asText("").trim();
          String type = attr.path("type").asText("").trim();

          if (name.isBlank()) {
            issues.add(new ValidationIssue(
                "WARNING",
                el.getId(),
                el.getNombre(),
                "Contiene un atributo sin nombre.",
                "Especifique un nombre para cada atributo."
            ));
          } else {
            String lower = name.toLowerCase(Locale.ROOT);
            if (attrNames.contains(lower)) {
              issues.add(new ValidationIssue(
                  "ERROR",
                  el.getId(),
                  el.getNombre(),
                  "El atributo '" + name + "' está duplicado en la misma clase.",
                  "Cada atributo dentro de una clase debe tener un nombre único."
              ));
            } else {
              attrNames.add(lower);
            }

            if (lower.equals("id") || lower.equals("codigo") || lower.equals("identificador")) {
              hasId = true;
            }
          }

          if (type.isBlank()) {
            issues.add(new ValidationIssue(
                "WARNING",
                el.getId(),
                el.getNombre(),
                "El atributo '" + name + "' no tiene tipo de dato especificado.",
                "Asigne un tipo de dato (ej. String, Integer, Double, Boolean, Date, UUID)."
            ));
          }
        }
      }

      // Si es CLASE o CLASE_ABSTRACTA y no tiene ID
      if ((el.getTipoElemento() == TipoElementoUML.CLASE || el.getTipoElemento() == TipoElementoUML.CLASE_ABSTRACTA) && !hasId) {
        issues.add(new ValidationIssue(
            "INFO",
            el.getId(),
            el.getNombre(),
            "La clase no tiene un atributo identificador 'id'.",
            "Se autogenerará 'id UUID PRIMARY KEY' al transformar a modelo relacional."
        ));
      }
    } catch (Exception ex) {
      issues.add(new ValidationIssue(
          "WARNING",
          el.getId(),
          el.getNombre(),
          "No se pudieron analizar las propiedades JSON: " + ex.getMessage(),
          "Revise la configuración de atributos y métodos del elemento."
      ));
    }
  }

  private void validateMultiplicity(UUID relId, String nodeName, String mult, String side, List<ValidationIssue> issues) {
    if (mult == null || mult.isBlank()) return;
    String trimmed = mult.trim();
    boolean valid = trimmed.matches("^(1|0\\.\\.1|\\*|0\\.\\.\\*|1\\.\\.\\*|[0-9]+)$");
    if (!valid) {
      issues.add(new ValidationIssue(
          "WARNING",
          relId,
          nodeName,
          "Multiplicidad '" + trimmed + "' en el extremo " + side + " no sigue el estándar UML.",
          "Use valores válidos como 1, 0..1, *, 0..*, 1..*."
      ));
    }
  }

  private void detectInheritanceCycles(
      Map<UUID, List<UUID>> graph,
      Map<UUID, ElementoUML> elementMap,
      List<ValidationIssue> issues
  ) {
    Set<UUID> visited = new HashSet<>();
    Set<UUID> recursionStack = new HashSet<>();

    for (UUID node : graph.keySet()) {
      if (!visited.contains(node)) {
        detectCycleDFS(node, graph, visited, recursionStack, elementMap, issues);
      }
    }
  }

  private void detectCycleDFS(
      UUID current,
      Map<UUID, List<UUID>> graph,
      Set<UUID> visited,
      Set<UUID> stack,
      Map<UUID, ElementoUML> elementMap,
      List<ValidationIssue> issues
  ) {
    visited.add(current);
    stack.add(current);

    List<UUID> neighbors = graph.getOrDefault(current, Collections.emptyList());
    for (UUID neighbor : neighbors) {
      if (!visited.contains(neighbor)) {
        detectCycleDFS(neighbor, graph, visited, stack, elementMap, issues);
      } else if (stack.contains(neighbor)) {
        String name1 = elementMap.containsKey(current) ? elementMap.get(current).getNombre() : "Elemento";
        String name2 = elementMap.containsKey(neighbor) ? elementMap.get(neighbor).getNombre() : "Elemento";
        issues.add(new ValidationIssue(
            "ERROR",
            current,
            name1,
            "Se detectó un ciclo de herencia circular entre '" + name1 + "' y '" + name2 + "'.",
            "UML no permite que una clase sea simultáneamente ancestro y descendiente de otra."
        ));
      }
    }
    stack.remove(current);
  }
}
