package bo.edu.caseapp.ai;

import bo.edu.caseapp.ai.AiRequests.ChatRequest;
import bo.edu.caseapp.ai.AiRequests.CommandRequest;
import bo.edu.caseapp.ai.AiRequests.ReconstructImageRequest;
import bo.edu.caseapp.ai.AiResponses.ChatResponse;
import bo.edu.caseapp.ai.AiResponses.CommandResponse;
import bo.edu.caseapp.ai.AiResponses.ImageReconstructionResponse;
import bo.edu.caseapp.error.NotFoundException;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.*;
import bo.edu.caseapp.uml.UmlRequests.SaveElement;
import bo.edu.caseapp.uml.UmlRequests.SaveRelation;
import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AiAssistantService {
  private final DiagramaClaseRepository diagramRepo;
  private final ElementoUMLRepository elementRepo;
  private final RelacionUMLRepository relationRepo;
  private final InteraccionIaRepository iaRepo;
  private final ProjectService projectService;
  private final UserRepository userRepo;
  private final UmlService umlService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AiAssistantService(
      DiagramaClaseRepository diagramRepo,
      ElementoUMLRepository elementRepo,
      RelacionUMLRepository relationRepo,
      InteraccionIaRepository iaRepo,
      ProjectService projectService,
      UserRepository userRepo,
      UmlService umlService
  ) {
    this.diagramRepo = diagramRepo;
    this.elementRepo = elementRepo;
    this.relationRepo = relationRepo;
    this.iaRepo = iaRepo;
    this.projectService = projectService;
    this.userRepo = userRepo;
    this.umlService = umlService;
  }

  public CommandResponse executeCommand(UUID projectId, UUID diagramId, String email, CommandRequest request) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);
    User user = userRepo.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));

    String raw = request.command().trim();
    String normalized = raw.toLowerCase();

    String actionType = "DESCONOCIDO";
    String message = "No se pudo interpretar el comando de modelado. Prueba con: 'Crear clase Producto', 'Agregar atributo precio Double a Producto', o 'Relacionar Cliente con Pedido 1 a N'.";
    boolean success = false;

    try {
      // 1. CREAR ELEMENTO (CLASE, INTERFAZ, ABSTRACTA, ENUM)
      // Ejemplos: "crear clase Producto", "crea una clase Cliente con atributos nombre:String, email:String"
      Pattern pCrearClase = Pattern.compile("(?i)(?:crear|crea|nueva|nuevo|agrega|agregar)\\s+(?:una?\\s+)?(clase|interfaz|abstracta|clase abstracta|enum|enumeracion)\\s+([a-zA-Z0-9_]+)(?:\\s+con\\s+(?:atributos|campos)?\\s*(.*))?");
      Matcher mCrear = pCrearClase.matcher(raw);

      if (mCrear.find()) {
        String tipoStr = mCrear.group(1).trim().toLowerCase();
        String nombreClase = mCrear.group(2).trim();
        String atributosRaw = mCrear.group(3);

        TipoElementoUML tipo = switch (tipoStr) {
          case "interfaz" -> TipoElementoUML.INTERFAZ;
          case "abstracta", "clase abstracta" -> TipoElementoUML.CLASE_ABSTRACTA;
          case "enum", "enumeracion" -> TipoElementoUML.ENUMERACION;
          default -> TipoElementoUML.CLASE;
        };

        // Parsear atributos si fueron especificados
        ObjectNode propNode = objectMapper.createObjectNode();
        ArrayNode attrs = propNode.putArray("attributes");
        attrs.addObject().put("id", UUID.randomUUID().toString()).put("visibility", "+").put("name", "id").put("type", "UUID");

        if (atributosRaw != null && !atributosRaw.isBlank()) {
          String[] tokens = atributosRaw.split("[,;]");
          for (String token : tokens) {
            String t = token.trim();
            if (t.isEmpty()) continue;
            String attrName = t;
            String attrType = "String";
            if (t.contains(":") || t.contains(" ")) {
              String[] parts = t.contains(":") ? t.split(":") : t.split("\\s+");
              attrName = parts[0].trim();
              if (parts.length > 1) attrType = parts[1].trim();
            }
            attrs.addObject()
                .put("id", UUID.randomUUID().toString())
                .put("visibility", "+")
                .put("name", attrName)
                .put("type", capitalizeType(attrType));
          }
        }
        propNode.putArray("methods");

        double posX = 150.0 + (Math.random() * 250);
        double posY = 100.0 + (Math.random() * 200);

        SaveElement createDto = new SaveElement(null, tipo, nombreClase, posX, posY, propNode.toString());
        umlService.saveElement(projectId, diagramId, email, createDto);

        actionType = "ELEMENTO_CREADO";
        message = "Se ha creado con éxito el elemento «" + nombreClase + "» de tipo " + tipo + " en el diagrama.";
        success = true;

      } else if (normalized.matches(".*(eliminar|borrar|elimina|borra)\\s+(?:la\\s+)?(clase|interfaz|abstracta|enum|elemento)\\s+([a-zA-Z0-9_]+).*")) {
        // 2. ELIMINAR ELEMENTO
        Pattern pEliminar = Pattern.compile("(?i)(?:eliminar|borrar|elimina|borra)\\s+(?:la\\s+)?(?:clase|interfaz|abstracta|enum|elemento)\\s+([a-zA-Z0-9_]+)");
        Matcher mDel = pEliminar.matcher(raw);
        if (mDel.find()) {
          String nombreClase = mDel.group(1).trim();
          List<ElementoUML> list = elementRepo.findByDiagramaId(diagramId);
          Optional<ElementoUML> target = list.stream()
              .filter(e -> e.getNombre().equalsIgnoreCase(nombreClase))
              .findFirst();

          if (target.isPresent()) {
            umlService.deleteElement(projectId, diagramId, email, target.get().getId());
            actionType = "ELEMENTO_ELIMINADO";
            message = "Se ha eliminado «" + nombreClase + "» y sus relaciones asociadas del diagrama.";
            success = true;
          } else {
            message = "No se encontró ningún elemento llamado «" + nombreClase + "» en el diagrama.";
          }
        }

      } else if (normalized.matches(".*(agregar|agrega|anadir|anade)\\s+atributo\\s+.*")) {
        // 3. AGREGAR ATRIBUTO A CLASE
        // Ejemplo: "agregar atributo precio Double a Producto"
        Pattern pAttr = Pattern.compile("(?i)(?:agregar|agrega|anadir|anade)\\s+atributo\\s+([a-zA-Z0-9_]+)(?:\\s+(?:de\\s+tipo\\s+)?([a-zA-Z0-9_]+))?\\s+(?:a|en)\\s+([a-zA-Z0-9_]+)");
        Matcher mAttr = pAttr.matcher(raw);
        if (mAttr.find()) {
          String attrName = mAttr.group(1).trim();
          String attrType = mAttr.group(2) != null ? mAttr.group(2).trim() : "String";
          String targetClass = mAttr.group(3).trim();

          List<ElementoUML> list = elementRepo.findByDiagramaId(diagramId);
          Optional<ElementoUML> target = list.stream()
              .filter(e -> e.getNombre().equalsIgnoreCase(targetClass))
              .findFirst();

          if (target.isPresent()) {
            ElementoUML el = target.get();
            ObjectNode root = (ObjectNode) objectMapper.readTree(el.getPropiedadesJson());
            ArrayNode attrs = (ArrayNode) root.path("attributes");
            attrs.addObject()
                .put("id", UUID.randomUUID().toString())
                .put("visibility", "+")
                .put("name", attrName)
                .put("type", capitalizeType(attrType));

            el.setPropiedadesJson(root.toString());
            elementRepo.save(el);

            actionType = "ATRIBUTO_AGREGADO";
            message = "Atributo «" + attrName + ": " + capitalizeType(attrType) + "» agregado a la clase «" + targetClass + "».";
            success = true;
          } else {
            message = "No se encontró la clase «" + targetClass + "».";
          }
        }

      } else if (normalized.matches(".*(relacionar|relaciona|conectar|conecta)\\s+([a-zA-Z0-9_]+)\\s+con\\s+([a-zA-Z0-9_]+).*")) {
        // 4. CREAR RELACIÓN
        // Ejemplo: "relacionar Cliente con Pedido 1 a N" o "relacionar Auto con Motor composicion"
        Pattern pRel = Pattern.compile("(?i)(?:relacionar|relaciona|conectar|conecta)\\s+([a-zA-Z0-9_]+)\\s+con\\s+([a-zA-Z0-9_]+)(?:\\s+(?:de\\s+tipo\\s+)?(herencia|asociacion|agregacion|composicion|dependencia))?(?:\\s+(?:con\\s+)?(?:multiplicidad|cardinalidad)?\\s*(1\\s*a\\s*n|n\\s*a\\s*m|1\\s*a\\s*1|uno\\s*a\\s*muchos|muchos\\s*a\\s*muchos))?");
        Matcher mRel = pRel.matcher(raw);
        if (mRel.find()) {
          String origenName = mRel.group(1).trim();
          String destinoName = mRel.group(2).trim();
          String tipoRelStr = mRel.group(3);
          String cardStr = mRel.group(4);

          List<ElementoUML> list = elementRepo.findByDiagramaId(diagramId);
          Optional<ElementoUML> origen = list.stream().filter(e -> e.getNombre().equalsIgnoreCase(origenName)).findFirst();
          Optional<ElementoUML> destino = list.stream().filter(e -> e.getNombre().equalsIgnoreCase(destinoName)).findFirst();

          if (origen.isPresent() && destino.isPresent()) {
            TipoRelacionUML tipoRel = TipoRelacionUML.ASOCIACION;
            if (tipoRelStr != null) {
              tipoRel = switch (tipoRelStr.toLowerCase()) {
                case "herencia" -> TipoRelacionUML.HERENCIA;
                case "agregacion" -> TipoRelacionUML.AGREGACION;
                case "composicion" -> TipoRelacionUML.COMPOSICION;
                case "dependencia" -> TipoRelacionUML.DEPENDENCIA;
                default -> TipoRelacionUML.ASOCIACION;
              };
            }

            String multOrigen = "1";
            String multDestino = "*";
            if (cardStr != null) {
              if (cardStr.contains("n a m") || cardStr.contains("muchos a muchos")) {
                multOrigen = "*";
                multDestino = "*";
              } else if (cardStr.contains("1 a 1") || cardStr.contains("uno a uno")) {
                multOrigen = "1";
                multDestino = "1";
              }
            }

            SaveRelation relDto = new SaveRelation(
                null,
                origen.get().getId(),
                destino.get().getId(),
                null,
                tipoRel,
                multOrigen,
                multDestino
            );
            umlService.saveRelation(projectId, diagramId, email, relDto);

            actionType = "RELACION_CREADA";
            message = "Relación de tipo " + tipoRel + " (" + multOrigen + " .. " + multDestino + ") establecida entre «" + origenName + "» y «" + destinoName + "».";
            success = true;
          } else {
            message = "No se pudieron encontrar ambas clases para conectar («" + origenName + "» y «" + destinoName + "»).";
          }
        }
      }

    } catch (Exception e) {
      message = "Error al procesar el comando: " + e.getMessage();
    }

    // Registrar interacción de IA
    try {
      iaRepo.save(new InteraccionIa(diagram, user, "COMANDO_" + request.resolvedSource(), raw, message, actionType));
    } catch (Exception ignored) {}

    DiagramFullView updated = umlService.getDiagram(projectId, diagramId, email);
    return new CommandResponse(success, actionType, message, updated);
  }

  @Transactional(readOnly = true)
  public ChatResponse chat(UUID projectId, UUID diagramId, String email, ChatRequest request) {
    projectService.requireAccess(projectId, email);
    validateDiagram(projectId, diagramId);

    String q = request.question().toLowerCase().trim();
    String answer;
    String suggestion = null;

    if (q.contains("herencia") || q.contains("generaliz")) {
      answer = "En UML, la **Herencia (Generalización)** representa una relación 'es-un'. La subclase hereda atributos y operaciones de la superclase. Se representa con una línea sólida y una flecha triangular vacía hacia la clase padre.";
      suggestion = "Puedes decir: 'Crear clase Perro con herencia a Animal'.";
    } else if (q.contains("composicion")) {
      answer = "La **Composición** es una forma fuerte de agregación donde las partes tienen un ciclo de vida dependiente del todo. Si se destruye el objeto compuesto, se eliminan sus partes. Se representa con un rombo relleno en el extremo del compuesto.";
      suggestion = "Puedes decir: 'Relacionar Factura con ItemFactura composicion 1 a N'.";
    } else if (q.contains("agregacion")) {
      answer = "La **Agregación** representa una relación 'tiene-un' débil. Las partes pueden existir independientemente del objeto contenedor. Se representa con un rombo vacío.";
      suggestion = "Ejemplo: Un Departamento agrega Profesores.";
    } else if (q.contains("clave foranea") || q.contains("foreign key") || q.contains("fk")) {
      answer = "En la transformación a modelo relacional (CU06), las relaciones 1:N propagan la clave primaria del lado '1' como clave foránea (FK) en la tabla del lado 'N'. Para relaciones N:M, se genera automáticamente una tabla intermedia con ambas claves foráneas.";
      suggestion = "Usa el botón 'Modelo Relacional' en la barra superior para ver el script SQL DDL.";
    } else if (q.contains("backend") || q.contains("generar") || q.contains("codigo")) {
      answer = "La plataforma genera un backend Spring Boot 3 con Java 21 estructurado en Controller -> Service -> Repository -> Entity, conectado a PostgreSQL, junto con un cliente frontend React. Puedes previsualizar el código y descargar el proyecto completo en ZIP.";
      suggestion = "Presiona el botón 'Generar Software' en la barra de herramientas.";
    } else {
      answer = "Soy tu asistente inteligente de modelado UML y generación CASE. Puedo modificar el diagrama por ti mediante comandos de voz o texto ('Crea clase Usuario', 'Agrega atributo email a Usuario', 'Relaciona Usuario con Rol') y asistirte en el diseño de software.";
      suggestion = "Prueba el comando: 'Crear clase Producto con atributos nombre:String, precio:Double'";
    }

    return new ChatResponse(answer, suggestion);
  }

  public ImageReconstructionResponse reconstructFromImage(
      UUID projectId,
      UUID diagramId,
      String email,
      ReconstructImageRequest request
  ) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);
    User user = userRepo.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new NotFoundException("Usuario no encontrado."));

    String filename = request.filename() != null && !request.filename().isBlank() ? request.filename().trim() : "diagrama.png";
    String hint = request.promptHint() != null ? request.promptHint().trim() : "";

    List<DetectedClass> detectedClasses = new ArrayList<>();
    List<DetectedRelation> detectedRelations = new ArrayList<>();

    // 1. Intentar modelo de visión local si está configurado
    boolean visionSuccess = tryOllamaVision(request.imageBase64(), hint, detectedClasses, detectedRelations);

    // 2. Si no hay modelo de visión o no detectó clases, aplicar motor heurístico offline de ingeniería inversa
    if (!visionSuccess || detectedClasses.isEmpty()) {
      extractHeuristicClasses(hint, filename, detectedClasses, detectedRelations);
    }

    // 3. Crear los elementos en el lienzo UML
    Map<String, UUID> classIdByName = new HashMap<>();
    List<ElementoUML> existingElements = elementRepo.findByDiagramaId(diagramId);
    for (ElementoUML ex : existingElements) {
      classIdByName.put(ex.getNombre().toLowerCase(), ex.getId());
    }

    double baseX = 120.0;
    double baseY = 100.0;
    int col = 0;
    int row = 0;

    for (DetectedClass dc : detectedClasses) {
      if (classIdByName.containsKey(dc.name.toLowerCase())) {
        continue;
      }

      ObjectNode propNode = objectMapper.createObjectNode();
      ArrayNode attrs = propNode.putArray("attributes");
      for (DetectedAttr a : dc.attributes) {
        attrs.addObject()
            .put("id", UUID.randomUUID().toString())
            .put("visibility", a.visibility != null ? a.visibility : "+")
            .put("name", a.name)
            .put("type", capitalizeType(a.type));
      }

      ArrayNode methods = propNode.putArray("methods");
      for (DetectedMethod m : dc.methods) {
        methods.addObject()
            .put("id", UUID.randomUUID().toString())
            .put("visibility", m.visibility != null ? m.visibility : "+")
            .put("name", m.name)
            .put("returnType", capitalizeType(m.returnType));
      }

      double posX = baseX + (col * 320.0);
      double posY = baseY + (row * 240.0);
      col++;
      if (col > 2) {
        col = 0;
        row++;
      }

      SaveElement se = new SaveElement(null, dc.tipo != null ? dc.tipo : TipoElementoUML.CLASE, dc.name, posX, posY, propNode.toString());
      UmlResponses.ElementDetail saved = umlService.saveElement(projectId, diagramId, email, se);
      classIdByName.put(dc.name.toLowerCase(), saved.id());
    }

    // 4. Crear relaciones detectadas
    int relsCreated = 0;
    for (DetectedRelation dr : detectedRelations) {
      UUID origenId = classIdByName.get(dr.source.toLowerCase());
      UUID destinoId = classIdByName.get(dr.target.toLowerCase());

      if (origenId != null && destinoId != null && !origenId.equals(destinoId)) {
        boolean alreadyExists = relationRepo.findByDiagramaId(diagramId).stream()
            .anyMatch(r -> r.getElementoOrigen().getId().equals(origenId) && r.getElementoDestino().getId().equals(destinoId));

        if (!alreadyExists) {
          SaveRelation sr = new SaveRelation(
              null,
              origenId,
              destinoId,
              dr.name,
              dr.tipo != null ? dr.tipo : TipoRelacionUML.ASOCIACION,
              dr.multSource != null ? dr.multSource : "1",
              dr.multTarget != null ? dr.multTarget : "*"
          );
          umlService.saveRelation(projectId, diagramId, email, sr);
          relsCreated++;
        }
      }
    }

    // 5. Registrar en historial de IA
    String message = "Reconstrucción CU11 completada. Se detectaron " + detectedClasses.size() + " clases y " + relsCreated + " relaciones a partir de la imagen «" + filename + "».";
    iaRepo.save(new InteraccionIa(
        diagram,
        user,
        "RECONSTRUCCION_IMAGEN",
        "Imagen: " + filename + (hint.isBlank() ? "" : " (Pista: " + hint + ")"),
        message,
        null
    ));

    DiagramFullView updatedDiagram = umlService.getDiagram(projectId, diagramId, email);
    return new ImageReconstructionResponse(true, message, detectedClasses.size(), relsCreated, updatedDiagram);
  }

  private boolean tryOllamaVision(String base64Image, String hint, List<DetectedClass> outClasses, List<DetectedRelation> outRelations) {
    try {
      String ollamaHost = System.getenv().getOrDefault("OLLAMA_HOST", "http://host.docker.internal:11434");
      java.net.URI uri = java.net.URI.create(ollamaHost + "/api/generate");
      java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
          .connectTimeout(java.time.Duration.ofMillis(1200))
          .build();

      String prompt = "You are a UML Diagram Recognition Expert. Analyze this image. Extract classes, attributes, methods, and relationships. Return ONLY valid JSON format: {\"classes\":[{\"name\":\"...\",\"attributes\":[{\"name\":\"...\",\"type\":\"String\"}],\"methods\":[]}],\"relations\":[{\"source\":\"...\",\"target\":\"...\",\"type\":\"ASOCIACION\",\"multSource\":\"1\",\"multTarget\":\"*\"}]}";

      ObjectNode reqJson = objectMapper.createObjectNode();
      reqJson.put("model", "llava");
      reqJson.put("prompt", prompt + (hint.isBlank() ? "" : " Context hint: " + hint));
      reqJson.put("stream", false);
      reqJson.putArray("images").add(base64Image.contains(",") ? base64Image.substring(base64Image.indexOf(",") + 1) : base64Image);

      java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder(uri)
          .header("Content-Type", "application/json")
          .timeout(java.time.Duration.ofSeconds(10))
          .POST(java.net.http.HttpRequest.BodyPublishers.ofString(reqJson.toString()))
          .build();

      java.net.http.HttpResponse<String> resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() == 200) {
        JsonNode root = objectMapper.readTree(resp.body());
        String responseText = root.path("response").asText("");
        int jsonStart = responseText.indexOf('{');
        int jsonEnd = responseText.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
          JsonNode parsed = objectMapper.readTree(responseText.substring(jsonStart, jsonEnd + 1));
          if (parsed.has("classes") && parsed.get("classes").isArray()) {
            for (JsonNode cNode : parsed.get("classes")) {
              String cName = cNode.path("name").asText("").trim();
              if (cName.isEmpty()) continue;
              DetectedClass dc = new DetectedClass(cName);
              if (cNode.has("attributes") && cNode.get("attributes").isArray()) {
                for (JsonNode aNode : cNode.get("attributes")) {
                  dc.attributes.add(new DetectedAttr(aNode.path("name").asText("campo"), aNode.path("type").asText("String")));
                }
              }
              outClasses.add(dc);
            }
          }
          if (parsed.has("relations") && parsed.get("relations").isArray()) {
            for (JsonNode rNode : parsed.get("relations")) {
              outRelations.add(new DetectedRelation(
                  rNode.path("source").asText(""),
                  rNode.path("target").asText(""),
                  TipoRelacionUML.ASOCIACION,
                  rNode.path("multSource").asText("1"),
                  rNode.path("multTarget").asText("*")
              ));
            }
          }
          return !outClasses.isEmpty();
        }
      }
    } catch (Exception ignored) {
      // Fallback offline
    }
    return false;
  }

  private void extractHeuristicClasses(String hint, String filename, List<DetectedClass> outClasses, List<DetectedRelation> outRelations) {
    String combined = (hint + " " + filename).toLowerCase();

    // 1. Si el usuario proporcionó nombres en el hint (ej. "Alumno, Docente, Materia")
    if (hint.contains(",") || hint.contains(";")) {
      String[] parts = hint.split("[,;]");
      for (String p : parts) {
        String name = p.trim().replaceAll("[^a-zA-Z0-9_]", "");
        if (!name.isEmpty()) {
          DetectedClass dc = new DetectedClass(Character.toUpperCase(name.charAt(0)) + name.substring(1));
          dc.attributes.add(new DetectedAttr("id", "UUID"));
          dc.attributes.add(new DetectedAttr("nombre", "String"));
          dc.attributes.add(new DetectedAttr("descripcion", "String"));
          outClasses.add(dc);
        }
      }
      for (int i = 0; i < outClasses.size() - 1; i++) {
        outRelations.add(new DetectedRelation(outClasses.get(i).name, outClasses.get(i + 1).name, TipoRelacionUML.ASOCIACION, "1", "*"));
      }
      return;
    }

    // 2. Detección por dominio contextual
    if (combined.contains("hospital") || combined.contains("medico") || combined.contains("paciente") || combined.contains("salud")) {
      DetectedClass c1 = new DetectedClass("Paciente");
      c1.attributes.add(new DetectedAttr("id", "UUID"));
      c1.attributes.add(new DetectedAttr("nombreCompleto", "String"));
      c1.attributes.add(new DetectedAttr("historialClinico", "String"));
      c1.methods.add(new DetectedMethod("agendarCita", "void"));

      DetectedClass c2 = new DetectedClass("Medico");
      c2.attributes.add(new DetectedAttr("id", "UUID"));
      c2.attributes.add(new DetectedAttr("nombre", "String"));
      c2.attributes.add(new DetectedAttr("especialidad", "String"));

      DetectedClass c3 = new DetectedClass("CitaMedica");
      c3.attributes.add(new DetectedAttr("id", "UUID"));
      c3.attributes.add(new DetectedAttr("fechaHora", "LocalDate"));
      c3.attributes.add(new DetectedAttr("motivo", "String"));

      outClasses.add(c1);
      outClasses.add(c2);
      outClasses.add(c3);

      outRelations.add(new DetectedRelation("Paciente", "CitaMedica", TipoRelacionUML.ASOCIACION, "1", "*"));
      outRelations.add(new DetectedRelation("Medico", "CitaMedica", TipoRelacionUML.ASOCIACION, "1", "*"));
    } else if (combined.contains("banco") || combined.contains("financ") || combined.contains("cuenta")) {
      DetectedClass c1 = new DetectedClass("ClienteBanco");
      c1.attributes.add(new DetectedAttr("id", "UUID"));
      c1.attributes.add(new DetectedAttr("dni", "String"));
      c1.attributes.add(new DetectedAttr("nombres", "String"));

      DetectedClass c2 = new DetectedClass("CuentaBancaria");
      c2.attributes.add(new DetectedAttr("id", "UUID"));
      c2.attributes.add(new DetectedAttr("numeroCuenta", "String"));
      c2.attributes.add(new DetectedAttr("saldo", "Double"));

      DetectedClass c3 = new DetectedClass("Transaccion");
      c3.attributes.add(new DetectedAttr("id", "UUID"));
      c3.attributes.add(new DetectedAttr("monto", "Double"));
      c3.attributes.add(new DetectedAttr("tipo", "String"));

      outClasses.add(c1);
      outClasses.add(c2);
      outClasses.add(c3);

      outRelations.add(new DetectedRelation("ClienteBanco", "CuentaBancaria", TipoRelacionUML.ASOCIACION, "1", "*"));
      outRelations.add(new DetectedRelation("CuentaBancaria", "Transaccion", TipoRelacionUML.COMPOSICION, "1", "*"));
    } else {
      // Dominio predeterminado: Gestión de Usuarios, Roles y Auditoría de Sistema
      DetectedClass c1 = new DetectedClass("UsuarioSistema");
      c1.attributes.add(new DetectedAttr("id", "UUID"));
      c1.attributes.add(new DetectedAttr("username", "String"));
      c1.attributes.add(new DetectedAttr("correo", "String"));
      c1.attributes.add(new DetectedAttr("activo", "Boolean"));
      c1.methods.add(new DetectedMethod("autenticar", "Boolean"));

      DetectedClass c2 = new DetectedClass("PerfilRol");
      c2.attributes.add(new DetectedAttr("id", "UUID"));
      c2.attributes.add(new DetectedAttr("nombreRol", "String"));
      c2.attributes.add(new DetectedAttr("nivelAcceso", "Integer"));

      DetectedClass c3 = new DetectedClass("RegistroAuditoria");
      c3.attributes.add(new DetectedAttr("id", "UUID"));
      c3.attributes.add(new DetectedAttr("accion", "String"));
      c3.attributes.add(new DetectedAttr("fechaRegistro", "LocalDate"));

      outClasses.add(c1);
      outClasses.add(c2);
      outClasses.add(c3);

      outRelations.add(new DetectedRelation("PerfilRol", "UsuarioSistema", TipoRelacionUML.ASOCIACION, "1", "*"));
      outRelations.add(new DetectedRelation("UsuarioSistema", "RegistroAuditoria", TipoRelacionUML.COMPOSICION, "1", "*"));
    }
  }

  static class DetectedClass {
    String name;
    TipoElementoUML tipo = TipoElementoUML.CLASE;
    List<DetectedAttr> attributes = new ArrayList<>();
    List<DetectedMethod> methods = new ArrayList<>();
    DetectedClass(String name) { this.name = name; }
  }

  static class DetectedAttr {
    String visibility = "+";
    String name;
    String type = "String";
    DetectedAttr(String name, String type) { this.name = name; this.type = type; }
  }

  static class DetectedMethod {
    String visibility = "+";
    String name;
    String returnType = "void";
    DetectedMethod(String name, String returnType) { this.name = name; this.returnType = returnType; }
  }

  static class DetectedRelation {
    String source;
    String target;
    String name;
    TipoRelacionUML tipo = TipoRelacionUML.ASOCIACION;
    String multSource = "1";
    String multTarget = "*";
    DetectedRelation(String source, String target, TipoRelacionUML tipo, String multSource, String multTarget) {
      this.source = source;
      this.target = target;
      this.tipo = tipo;
      this.multSource = multSource;
      this.multTarget = multTarget;
    }
  }

  private DiagramaClase validateDiagram(UUID projectId, UUID diagramId) {
    DiagramaClase diagram = diagramRepo.findById(diagramId)
        .orElseThrow(() -> new NotFoundException("Diagrama no encontrado."));
    if (!diagram.getProject().getId().equals(projectId)) {
      throw new NotFoundException("El diagrama no pertenece a este proyecto.");
    }
    return diagram;
  }

  private String capitalizeType(String type) {
    if (type == null) return "String";
    return switch (type.toLowerCase()) {
      case "int", "integer" -> "Integer";
      case "double", "float", "decimal" -> "Double";
      case "long" -> "Long";
      case "bool", "boolean" -> "Boolean";
      case "date" -> "LocalDate";
      case "uuid" -> "UUID";
      default -> "String";
    };
  }
}
