package bo.edu.caseapp.ai;

import bo.edu.caseapp.ai.AiRequests.*;
import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.uml.TipoElementoUML;
import bo.edu.caseapp.uml.TipoRelacionUML;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Pattern;

/** Strictly validates Gemini's JSON and removes only low-confidence details before persistence. */
public final class UmlImageModelValidator {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern UML_NAME = Pattern.compile("[\\p{L}_][\\p{L}\\p{N}_]{0,119}");
  private static final Pattern MULTIPLICITY = Pattern.compile("(?i)(?:\\*|[0-9]{1,6}|[0-9]{1,6}\\.\\.(?:[0-9]{1,6}|\\*|[nNmM])|\\.\\.(?:[0-9]{1,6}|\\*|[nNmM])|[nNmM])");
  private static final double MIN_CLASS_CONFIDENCE = 0.50;
  private static final double MIN_DETAIL_CONFIDENCE = 0.45;
  private static final double MIN_RELATION_CONFIDENCE = 0.50;

  private UmlImageModelValidator() {}

  public static ImageModel parseAndValidate(String response) {
    try {
      if (response == null || response.isBlank()) {
        throw invalid("Gemini devolvió una respuesta vacía.");
      }
      String jsonText = response.trim();
      if (jsonText.contains("```")) {
        int start = jsonText.indexOf('{');
        int end = jsonText.lastIndexOf('}');
        if (start >= 0 && end > start) {
          jsonText = jsonText.substring(start, end + 1);
        }
      }

      JsonNode root = JSON.readTree(jsonText);
      requireObject(root, "La respuesta de Gemini debe ser un objeto JSON.");
      requireRequiredFields(root, Set.of("classes", "relations"), "La respuesta debe contener los arreglos classes y relations.");
      if (!root.path("classes").isArray() || !root.path("relations").isArray()) {
        throw invalid("classes y relations deben ser arreglos JSON.");
      }
      if (root.path("classes").size() > 150 || root.path("relations").size() > 500) throw invalid("El diagrama excede el límite de elementos permitido.");

      List<String> warnings = root.has("warnings") && root.path("warnings").isArray() ? readWarnings(root.path("warnings")) : new ArrayList<>();
      List<ImageClass> classes = new ArrayList<>();
      Map<String, ImageClass> byName = new LinkedHashMap<>();
      for (JsonNode node : root.path("classes")) {
        requireObject(node, "Cada clase debe ser un objeto JSON.");
        requireRequiredFields(node, Set.of("name"), "Una clase contiene campos faltantes.");
        String name = optionalText(node, "name", 120);
        String typeText = optionalText(node, "type", 40);
        if (name == null) { warnings.add("Se omitió un elemento cuyo nombre no se pudo identificar."); continue; }
        TipoElementoUML type = parseClassType(typeText);
        double confidence = readConfidence(node, "confidence");
        JsonNode attrsNode = node.path("attributes"), methodsNode = node.path("methods");
        if (!attrsNode.isMissingNode() && !attrsNode.isNull() && !attrsNode.isArray()) throw invalid("Los atributos deben ser un arreglo.");
        if (!methodsNode.isMissingNode() && !methodsNode.isNull() && !methodsNode.isArray()) throw invalid("Los métodos deben ser un arreglo.");
        if (!UML_NAME.matcher(name).matches()) {
          warnings.add("Se omitió un elemento porque su nombre no es válido o no se pudo leer claramente.");
          continue;
        }
        if (confidence < MIN_CLASS_CONFIDENCE) {
          warnings.add("Se omitió «" + name + "» por baja confianza de lectura (" + percent(confidence) + ").");
          continue;
        }

        String key = normalize(name);
        if (byName.containsKey(key)) {
          warnings.add("Se omitió la definición duplicada de «" + name + "».");
          continue;
        }
        Double x = optionalCoordinate(node, "x", warnings, name);
        Double y = optionalCoordinate(node, "y", warnings, name);
        if (x == null || y == null) { x = null; y = null; }
        List<ImageAttribute> attrs = attrsNode.isArray() ? readAttributes(attrsNode, name, warnings) : List.of();
        List<ImageMethod> methods = methodsNode.isArray() ? readMethods(methodsNode, name, warnings) : List.of();
        ImageClass umlClass = new ImageClass(name, type, confidence, x, y, attrs, methods);
        byName.put(key, umlClass);
        classes.add(umlClass);
      }
      if (classes.isEmpty()) throw invalid("Gemini no identificó clases con suficiente confianza. No se modificó el diagrama.");

      List<ImageRelation> relations = new ArrayList<>();
      Set<String> uniqueRelations = new HashSet<>();
      for (JsonNode node : root.path("relations")) {
        requireObject(node, "Cada relación debe ser un objeto JSON.");
        requireRequiredFields(node, Set.of("source", "target"), "Una relación contiene campos faltantes.");
        String source = optionalText(node, "source", 120), target = optionalText(node, "target", 120);
        String typeText = optionalText(node, "type", 40);
        if (source == null || target == null) { warnings.add("Se omitió una relación cuyo origen o destino no se pudo identificar."); continue; }
        TipoRelacionUML type = parseRelationType(typeText);
        double confidence = readConfidence(node, "confidence");
        if (confidence < MIN_RELATION_CONFIDENCE) {
          warnings.add("Se omitió la relación «" + source + " → " + target + "» por baja confianza de lectura.");
          continue;
        }
        if (!byName.containsKey(normalize(source)) || !byName.containsKey(normalize(target))) {
          warnings.add("Se omitió una relación porque su origen o destino no existe en los elementos reconocidos: «" + source + " → " + target + "».");
          continue;
        }
        String multiplicitySource = readMultiplicity(node, "multiplicitySource", source, warnings);
        String multiplicityTarget = readMultiplicity(node, "multiplicityTarget", target, warnings);
        String sourceKey = normalize(source), targetKey = normalize(target);
        if (type == TipoRelacionUML.ASOCIACION && sourceKey.compareTo(targetKey) > 0) {
          String swap = sourceKey;
          sourceKey = targetKey;
          targetKey = swap;
        }
        String key = sourceKey + "|" + targetKey + "|" + type;
        if (!uniqueRelations.add(key)) {
          warnings.add("Se omitió una relación duplicada entre «" + source + "» y «" + target + "».");
          continue;
        }
        String name = optionalText(node, "name", 120);
        relations.add(new ImageRelation(source, target, name, type, multiplicitySource, multiplicityTarget, confidence));
      }
      return new ImageModel(List.copyOf(classes), List.copyOf(relations), distinctWarnings(warnings));
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalid("Gemini no devolvió JSON UML válido. No se modificó el diagrama.");
    }
  }

  public static ImageModel validateForApply(ImageModel model) {
    if (model == null || model.classes() == null || model.classes().isEmpty() || model.relations() == null || model.warnings() == null) {
      throw invalid("El modelo UML está incompleto. No se modificó el diagrama.");
    }
    try {
      return parseAndValidate(JSON.writeValueAsString(model));
    } catch (BadRequestException exception) {
      throw exception;
    } catch (Exception exception) {
      throw invalid("No se pudo validar el modelo UML antes de guardarlo.");
    }
  }

  private static TipoElementoUML parseClassType(String value) {
    if (value == null || value.isBlank()) return TipoElementoUML.CLASE;
    String v = value.trim().toUpperCase(Locale.ROOT);
    return switch (v) {
      case "INTERFAZ", "INTERFACE" -> TipoElementoUML.INTERFAZ;
      case "CLASE_ABSTRACTA", "ABSTRACT_CLASS", "ABSTRACT", "ABSTRACTCLASS" -> TipoElementoUML.CLASE_ABSTRACTA;
      case "ENUMERACION", "ENUM", "ENUMERATION" -> TipoElementoUML.ENUMERACION;
      default -> TipoElementoUML.CLASE;
    };
  }

  private static TipoRelacionUML parseRelationType(String value) {
    if (value == null || value.isBlank()) return TipoRelacionUML.ASOCIACION;
    String v = value.trim().toUpperCase(Locale.ROOT);
    return switch (v) {
      case "HERENCIA", "INHERITANCE", "GENERALIZATION", "EXTENDS", "IMPLEMENTS" -> TipoRelacionUML.HERENCIA;
      case "AGREGACION", "AGGREGATION" -> TipoRelacionUML.AGREGACION;
      case "COMPOSICION", "COMPOSITION" -> TipoRelacionUML.COMPOSICION;
      case "DEPENDENCIA", "DEPENDENCY" -> TipoRelacionUML.DEPENDENCIA;
      default -> TipoRelacionUML.ASOCIACION;
    };
  }

  private static List<ImageAttribute> readAttributes(JsonNode array, String className, List<String> warnings) {
    List<ImageAttribute> result = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (JsonNode node : array) {
      requireObject(node, "Cada atributo debe ser un objeto JSON.");
      requireRequiredFields(node, Set.of("name"), "Un atributo contiene campos faltantes.");
      String name = optionalText(node, "name", 120);
      if (name == null) { warnings.add("Se omitió un atributo de «" + className + "» cuyo nombre no se pudo leer."); continue; }
      double confidence = readConfidence(node, "confidence");
      if (!UML_NAME.matcher(name).matches()) { warnings.add("Se omitió un atributo de «" + className + "» con nombre ilegible."); continue; }
      if (confidence < MIN_DETAIL_CONFIDENCE) { warnings.add("Se omitió el atributo «" + name + "» de «" + className + "» por baja confianza."); continue; }
      if (!names.add(normalize(name))) { warnings.add("Se omitió el atributo duplicado «" + name + "» de «" + className + "»."); continue; }
      String visibility = optionalText(node, "visibility", 1);
      if (visibility == null || !Set.of("+", "-", "#", "~").contains(visibility)) {
        visibility = "~";
      }
      String type = optionalText(node, "type", 120);
      if (type == null) { type = "?"; }
      result.add(new ImageAttribute(visibility, name, type, confidence));
    }
    return List.copyOf(result);
  }

  private static List<ImageMethod> readMethods(JsonNode array, String className, List<String> warnings) {
    List<ImageMethod> result = new ArrayList<>();
    Set<String> names = new HashSet<>();
    for (JsonNode node : array) {
      requireObject(node, "Cada método debe ser un objeto JSON.");
      requireRequiredFields(node, Set.of("name"), "Un método contiene campos faltantes.");
      String name = optionalText(node, "name", 120);
      if (name == null) { warnings.add("Se omitió un método de «" + className + "» cuyo nombre no se pudo leer."); continue; }
      double confidence = readConfidence(node, "confidence");
      if (!UML_NAME.matcher(name).matches()) { warnings.add("Se omitió un método de «" + className + "» con nombre ilegible."); continue; }
      if (confidence < MIN_DETAIL_CONFIDENCE) { warnings.add("Se omitió el método «" + name + "» de «" + className + "» por baja confianza."); continue; }
      if (!names.add(normalize(name))) { warnings.add("Se omitió el método duplicado «" + name + "» de «" + className + "»."); continue; }
      String visibility = optionalText(node, "visibility", 1);
      if (visibility == null || !Set.of("+", "-", "#", "~").contains(visibility)) {
        visibility = "~";
      }
      String returnType = optionalText(node, "returnType", 120);
      if (returnType == null) { returnType = "?"; }
      JsonNode parametersNode = node.get("parameters");
      String parameters = parametersNode != null && parametersNode.isTextual() && parametersNode.asText().length() <= 300 ? parametersNode.asText().trim() : "";
      result.add(new ImageMethod(visibility, name, returnType, parameters, confidence));
    }
    return List.copyOf(result);
  }

  private static String readMultiplicity(JsonNode node, String field, String endpoint, List<String> warnings) {
    String value = optionalText(node, field, 20);
    if (value == null) return null;
    value = normalizeMultiplicity(value);
    if (!MULTIPLICITY.matcher(value).matches()) {
      warnings.add("Se ignoró la multiplicidad no reconocida «" + value + "» en «" + endpoint + "».");
      return null;
    }
    return value;
  }

  private static String normalizeMultiplicity(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.equalsIgnoreCase("1..n") || s.equalsIgnoreCase("1..m")) return "1..*";
    if (s.equalsIgnoreCase("0..n") || s.equalsIgnoreCase("0..m")) return "0..*";
    if (s.equalsIgnoreCase("n") || s.equalsIgnoreCase("m")) return "*";
    return s;
  }

  private static Double optionalCoordinate(JsonNode node, String field, List<String> warnings, String name) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull()) return null;
    if (value.isNumber()) {
      double v = value.asDouble();
      if (Double.isFinite(v) && v >= 0 && v <= 5000) return v;
    } else if (value.isTextual()) {
      try {
        double v = Double.parseDouble(value.asText().trim());
        if (Double.isFinite(v) && v >= 0 && v <= 5000) return v;
      } catch (NumberFormatException ignored) {}
    }
    return null;
  }

  private static List<String> readWarnings(JsonNode array) {
    List<String> warnings = new ArrayList<>();
    for (JsonNode node : array) {
      if (node.isTextual() && !node.asText().isBlank() && node.asText().length() <= 500) {
        warnings.add(node.asText().trim());
      }
    }
    return warnings;
  }

  private static String optionalText(JsonNode node, String field, int maxLength) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || (value.isTextual() && value.asText().isBlank())) return null;
    if (!value.isTextual() || value.asText().length() > maxLength) return null;
    return value.asText().trim();
  }

  private static double readConfidence(JsonNode node, String field) {
    JsonNode value = node.get(field);
    if (value == null || value.isNull() || !value.isNumber()) return 0.90;
    double v = value.asDouble();
    if (!Double.isFinite(v) || v < 0 || v > 1) return 0.90;
    return v;
  }

  private static void requireObject(JsonNode node, String error) { if (node == null || !node.isObject()) throw invalid(error); }
  private static void requireRequiredFields(JsonNode node, Set<String> requiredFields, String error) {
    if (node == null || !node.isObject()) throw invalid(error);
    for (String field : requiredFields) {
      if (!node.has(field)) throw invalid(error + " Falta el campo obligatorio: «" + field + "».");
    }
  }
  private static List<String> distinctWarnings(List<String> warnings) { return warnings.stream().distinct().limit(100).toList(); }
  private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }
  private static String percent(double value) { return Math.round(value * 100) + "%"; }
  private static BadRequestException invalid(String message) { return new BadRequestException(message); }
}
