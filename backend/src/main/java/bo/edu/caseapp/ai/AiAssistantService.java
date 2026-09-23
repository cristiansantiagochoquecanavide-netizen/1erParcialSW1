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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AiAssistantService {
  private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
  private final DiagramaClaseRepository diagramRepo;
  private final ElementoUMLRepository elementRepo;
  private final RelacionUMLRepository relationRepo;
  private final InteraccionIaRepository iaRepo;
  private final ProjectService projectService;
  private final UserRepository userRepo;
  private final UmlService umlService;
  private final bo.edu.caseapp.uml.relational.SqlDdlParserService sqlDdlParserService;
  private final bo.edu.caseapp.uml.collab.UmlWebSocketHandler wsHandler;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public AiAssistantService(
      DiagramaClaseRepository diagramRepo,
      ElementoUMLRepository elementRepo,
      RelacionUMLRepository relationRepo,
      InteraccionIaRepository iaRepo,
      ProjectService projectService,
      UserRepository userRepo,
      UmlService umlService,
      bo.edu.caseapp.uml.relational.SqlDdlParserService sqlDdlParserService,
      bo.edu.caseapp.uml.collab.UmlWebSocketHandler wsHandler
  ) {
    this.diagramRepo = diagramRepo;
    this.elementRepo = elementRepo;
    this.relationRepo = relationRepo;
    this.iaRepo = iaRepo;
    this.projectService = projectService;
    this.userRepo = userRepo;
    this.umlService = umlService;
    this.sqlDdlParserService = sqlDdlParserService;
    this.wsHandler = wsHandler;
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
      // 0. RECONOCIMIENTO DE TABLAS SQL (CU11 / Requerimiento 8)
      if (normalized.contains("create table")) {
        int idx = normalized.indexOf("create table");
        String sqlPart = raw.substring(idx);
        DiagramFullView fullView = sqlDdlParserService.parseAndImportSql(projectId, diagramId, email, sqlPart);
        actionType = "SQL_IMPORTADO";
        message = "Script SQL DDL reconocido y procesado exitosamente. Tablas, atributos, tipos, claves primarias y relaciones foráneas creadas en el modelo.";
        InteraccionIa ia = new InteraccionIa(diagram, user, request.source() != null ? request.source() : "TEXT", raw, message, actionType);
        iaRepo.save(ia);
        return new CommandResponse(true, actionType, message, fullView);
      }

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

        // Parsear atributos si fueron especificados (sin atributos por defecto)
        ObjectNode propNode = objectMapper.createObjectNode();
        ArrayNode attrs = propNode.putArray("attributes");

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

    // 1. Si el usuario proporcionó clave de Google Gemini, priorizar visión multimodal de IA en el backend
    String geminiKey = request.geminiApiKey() != null && !request.geminiApiKey().isBlank()
        ? request.geminiApiKey().trim()
        : System.getenv("GEMINI_API_KEY");
    if (geminiKey != null && !geminiKey.isBlank()) {
      log.info("Iniciando análisis multimodal con Google Gemini Vision en backend...");
      tryGeminiVision(request.imageBase64(), geminiKey, detectedClasses, detectedRelations);
    }

    // 2. Si no se usó Gemini o no detectó clases, usar lo detectado por el cliente
    if (detectedClasses.isEmpty() && request.clientDetectedClasses() != null && !request.clientDetectedClasses().isEmpty()) {
      for (AiRequests.ClientDetectedClass cdc : request.clientDetectedClasses()) {
        if (cdc.name() == null || cdc.name().isBlank()) continue;
        DetectedClass dc = new DetectedClass(cdc.name().trim());
        if (cdc.posX() != null) dc.posX = cdc.posX();
        if (cdc.posY() != null) dc.posY = cdc.posY();
        if (cdc.attributes() != null) {
          for (AiRequests.ClientDetectedAttr cda : cdc.attributes()) {
            if (cda.name() != null && !cda.name().isBlank()) {
              dc.attributes.add(new DetectedAttr(cda.visibility(), cda.name().trim(), cda.type() != null ? cda.type().trim() : "String"));
            }
          }
        }
        if (cdc.methods() != null) {
          for (AiRequests.ClientDetectedMethod cdm : cdc.methods()) {
            if (cdm.name() != null && !cdm.name().isBlank()) {
              dc.methods.add(new DetectedMethod(cdm.visibility(), cdm.name().trim(), cdm.returnType() != null ? cdm.returnType().trim() : "void"));
            }
          }
        }
        detectedClasses.add(dc);
      }
      if (request.clientDetectedRelations() != null) {
        for (AiRequests.ClientDetectedRelation cdr : request.clientDetectedRelations()) {
          if (cdr.source() != null && cdr.target() != null && !cdr.source().isBlank() && !cdr.target().isBlank()) {
            TipoRelacionUML tipoRel = TipoRelacionUML.ASOCIACION;
            if (cdr.tipo() != null) {
              try {
                tipoRel = TipoRelacionUML.valueOf(cdr.tipo().toUpperCase());
              } catch (Exception ignored) {}
            }
            detectedRelations.add(new DetectedRelation(
                cdr.source().trim(),
                cdr.target().trim(),
                cdr.name(),
                tipoRel,
                cdr.multSource() != null ? cdr.multSource().trim() : "1",
                cdr.multTarget() != null ? cdr.multTarget().trim() : "*"
            ));
          }
        }
      }
    }

    // 3. Si aún no hay clases detectadas, intentar OCR nativo local en el servidor (Tesseract)
    if (detectedClasses.isEmpty()) {
      tryNativeTesseract(request.imageBase64(), detectedClasses, detectedRelations);
    }

    // 4. Intentar modelo de visión local Ollama si está configurado
    if (detectedClasses.isEmpty()) {
      tryOllamaVision(request.imageBase64(), hint, detectedClasses, detectedRelations);
    }

    // 5. Si aún no hay clases detectadas pero el usuario proporcionó nombres en la pista
    if (detectedClasses.isEmpty() && (hint.contains(",") || hint.contains(";"))) {
      extractFromUserHint(hint, detectedClasses, detectedRelations);
    }

    // Si después de todos los análisis no se detectó nada, retornar aviso claro sin inyectar datos falsos
    if (detectedClasses.isEmpty()) {
      String message = "No se pudieron reconocer clases en la imagen. Asegúrate de que el diagrama sea legible o ingresa una clave de Google Gemini Vision para análisis multimodal.";
      DiagramFullView currentDiagram = umlService.getDiagram(projectId, diagramId, email);
      return new ImageReconstructionResponse(false, message, 0, 0, currentDiagram);
    }

    // 6. Crear o actualizar los elementos en el lienzo UML
    Map<String, UUID> classIdByName = new HashMap<>();
    List<ElementoUML> existingElements = elementRepo.findByDiagramaId(diagramId);
    for (ElementoUML ex : existingElements) {
      classIdByName.put(ex.getNombre().toLowerCase(), ex.getId());

      // Limpiar doble paréntesis en métodos ya almacenados en base de datos
      try {
        JsonNode pNode = objectMapper.readTree(ex.getPropiedadesJson());
        if (pNode.has("methods") && pNode.get("methods").isArray()) {
          boolean changed = false;
          for (JsonNode mNode : pNode.get("methods")) {
            String mName = mNode.path("name").asText("");
            if (mName.endsWith("()")) {
              ((ObjectNode) mNode).put("name", mName.substring(0, mName.length() - 2).trim());
              changed = true;
            }
          }
          if (changed) {
            ex.setPropiedadesJson(objectMapper.writeValueAsString(pNode));
            elementRepo.save(ex);
          }
        }
      } catch (Exception ignored) {}
    }

    double baseX = 100.0;
    double baseY = 80.0;
    int col = 0;
    int row = 0;

    for (DetectedClass dc : detectedClasses) {
      String dcNameLower = dc.name.toLowerCase();
      if (classIdByName.containsKey(dcNameLower)) {
        // Actualizar la posición de la clase existente con la posición detectada en la imagen
        if (dc.posX != null && dc.posY != null) {
          UUID exId = classIdByName.get(dcNameLower);
          for (ElementoUML ex : existingElements) {
            if (ex.getId().equals(exId)) {
              ex.updatePosition(dc.posX, dc.posY);
              elementRepo.save(ex);
              break;
            }
          }
        }
        continue;
      }

      ObjectNode propNode = objectMapper.createObjectNode();
      ArrayNode attrs = propNode.putArray("attributes");
      for (DetectedAttr a : dc.attributes) {
        attrs.addObject()
            .put("id", UUID.randomUUID().toString())
            .put("visibility", a.visibility != null ? a.visibility : "-")
            .put("name", a.name)
            .put("type", capitalizeType(a.type));
      }

      ArrayNode methods = propNode.putArray("methods");
      for (DetectedMethod m : dc.methods) {
        String cleanMethodName = m.name != null ? m.name.replaceAll("\\(\\s*\\)$", "").trim() : "metodo";
        methods.addObject()
            .put("id", UUID.randomUUID().toString())
            .put("visibility", m.visibility != null ? m.visibility : "+")
            .put("name", cleanMethodName)
            .put("returnType", capitalizeType(m.returnType));
      }

      double posX = dc.posX != null ? dc.posX : baseX + (col * 320.0);
      double posY = dc.posY != null ? dc.posY : baseY + (row * 240.0);
      col++;
      if (col > 2) {
        col = 0;
        row++;
      }

      SaveElement se = new SaveElement(null, dc.tipo != null ? dc.tipo : TipoElementoUML.CLASE, dc.name, posX, posY, propNode.toString());
      UmlResponses.ElementDetail saved = umlService.saveElement(projectId, diagramId, email, se);
      classIdByName.put(dc.name.toLowerCase(), saved.id());
    }

    // 7. Crear relaciones detectadas (filtrando diagonales no existentes)
    int relsCreated = 0;
    Set<String> seenPairs = new HashSet<>();

    for (DetectedRelation dr : detectedRelations) {
      if (dr.source == null || dr.target == null || dr.source.equalsIgnoreCase(dr.target)) continue;

      String s = dr.source.trim();
      String t = dr.target.trim();
      String sLower = s.toLowerCase();
      String tLower = t.toLowerCase();
      String pairKey = sLower.compareTo(tLower) < 0 ? sLower + ":::" + tLower : tLower + ":::" + sLower;
      if (seenPairs.contains(pairKey)) continue;
      seenPairs.add(pairKey);

      UUID origenId = classIdByName.get(sLower);
      UUID destinoId = classIdByName.get(tLower);

      if (origenId != null && destinoId != null && !origenId.equals(destinoId)) {
        boolean alreadyExists = relationRepo.findByDiagramaId(diagramId).stream()
            .anyMatch(r -> (r.getElementoOrigen().getId().equals(origenId) && r.getElementoDestino().getId().equals(destinoId)) ||
                           (r.getElementoOrigen().getId().equals(destinoId) && r.getElementoDestino().getId().equals(origenId)));

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

    // Notificar cambios al lienzo en tiempo real
    wsHandler.broadcastDiagramChanged(diagramId, "IMAGE_RECONSTRUCTED");

    // 7. Registrar en historial de IA
    String message = "Reconstrucción completada exitosamente. Se insertaron " + detectedClasses.size() + " clases y " + relsCreated + " relaciones a partir de la imagen «" + filename + "».";
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

  private boolean tryGeminiVision(String base64Image, String apiKey, List<DetectedClass> outClasses, List<DetectedRelation> outRelations) {
    if (apiKey == null || apiKey.isBlank()) return false;
    try {
      String cleanBase64 = base64Image.contains(",")
          ? base64Image.substring(base64Image.indexOf(",") + 1)
          : base64Image;

      String mimeType = base64Image.startsWith("data:image/png") ? "image/png" : "image/jpeg";

      String prompt = "You are an expert UML Reverse Engineering system. Analyze this UML diagram image in detail.\n" +
          "Extract EVERY class shown and ONLY the relationships that have an EXPLICIT connecting line drawn between the two classes.\n" +
          "Do NOT invent, assume or add any relationships between classes that do not have an explicit connecting line!\n" +
          "For each class: name, attributes (visibility: '-'|'+'|'#', name, type), methods (visibility, name, returnType), estimated posX (left=100, center=480, right=860) and posY (top=80, bottom=420).\n" +
          "For each relationship: source, target, name (e.g. 'realiza', 'tiene', 'corresponde a'), tipo ('ASOCIACION'), multSource, multTarget.\n" +
          "Return ONLY valid JSON matching this format: {\"classes\": [...], \"relations\": [...]}";

      ObjectNode rootNode = objectMapper.createObjectNode();
      ArrayNode contents = rootNode.putArray("contents");
      ObjectNode content = contents.addObject();
      ArrayNode parts = content.putArray("parts");
      parts.addObject().put("text", prompt);

      ObjectNode inlineData = parts.addObject().putObject("inline_data");
      inlineData.put("mime_type", mimeType);
      inlineData.put("data", cleanBase64);

      ObjectNode genConfig = rootNode.putObject("generationConfig");
      genConfig.put("response_mime_type", "application/json");

      String[] models = {"gemini-2.0-flash", "gemini-1.5-flash"};
      java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
          .connectTimeout(java.time.Duration.ofSeconds(12))
          .build();

      for (String model : models) {
        java.net.URI uri = java.net.URI.create("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey.trim());
        java.net.http.HttpRequest httpReq = java.net.http.HttpRequest.newBuilder(uri)
            .header("Content-Type", "application/json")
            .timeout(java.time.Duration.ofSeconds(25))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(rootNode.toString()))
            .build();

        java.net.http.HttpResponse<String> resp = client.send(httpReq, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200) {
          JsonNode resJson = objectMapper.readTree(resp.body());
          String text = resJson.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
          if (!text.isBlank()) {
            int jsonStart = text.indexOf('{');
            int jsonEnd = text.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
              JsonNode parsed = objectMapper.readTree(text.substring(jsonStart, jsonEnd + 1));
              parseJsonIntoDetectedElements(parsed, outClasses, outRelations);
              if (!outClasses.isEmpty()) {
                log.info("Gemini Vision [{}] reconoció exitosamente {} clases y {} relaciones.", model, outClasses.size(), outRelations.size());
                return true;
              }
            }
          }
        } else {
          log.warn("Gemini Vision [{}] status {}: {}", model, resp.statusCode(), resp.body());
        }
      }
    } catch (Exception e) {
      log.warn("Error consultando Gemini Vision en backend: {}", e.getMessage());
    }
    return false;
  }

  private void parseJsonIntoDetectedElements(JsonNode parsed, List<DetectedClass> outClasses, List<DetectedRelation> outRelations) {
    if (parsed.has("classes") && parsed.get("classes").isArray()) {
      for (JsonNode cNode : parsed.get("classes")) {
        String cName = cNode.path("name").asText("").trim();
        if (cName.isBlank()) continue;
        DetectedClass dc = new DetectedClass(cName);
        if (cNode.has("posX")) dc.posX = cNode.path("posX").asDouble();
        if (cNode.has("posY")) dc.posY = cNode.path("posY").asDouble();
        if (cNode.has("attributes") && cNode.get("attributes").isArray()) {
          for (JsonNode aNode : cNode.get("attributes")) {
            String aName = aNode.path("name").asText("").trim();
            if (!aName.isBlank()) {
              dc.attributes.add(new DetectedAttr(
                  aNode.path("visibility").asText("-"),
                  aName,
                  aNode.path("type").asText("String")
              ));
            }
          }
        }
        if (cNode.has("methods") && cNode.get("methods").isArray()) {
          for (JsonNode mNode : cNode.get("methods")) {
            String mName = mNode.path("name").asText("").trim();
            if (!mName.isBlank()) {
              dc.methods.add(new DetectedMethod(
                  mNode.path("visibility").asText("+"),
                  mName,
                  mNode.path("returnType").asText("void")
              ));
            }
          }
        }
        outClasses.add(dc);
      }
    }
    if (parsed.has("relations") && parsed.get("relations").isArray()) {
      Set<String> seenPairs = new HashSet<>();
      for (JsonNode rNode : parsed.get("relations")) {
        String src = rNode.path("source").asText("").trim();
        String tgt = rNode.path("target").asText("").trim();
        if (!src.isBlank() && !tgt.isBlank() && !src.equalsIgnoreCase(tgt)) {
          String sLower = src.toLowerCase();
          String tLower = tgt.toLowerCase();
          String pairKey = sLower.compareTo(tLower) < 0 ? sLower + ":::" + tLower : tLower + ":::" + sLower;
          if (seenPairs.contains(pairKey)) continue;
          seenPairs.add(pairKey);

          TipoRelacionUML tipoRel = TipoRelacionUML.ASOCIACION;
          String tipoStr = rNode.path("tipo").asText("ASOCIACION");
          try {
            tipoRel = TipoRelacionUML.valueOf(tipoStr.toUpperCase());
          } catch (Exception ignored) {}
          outRelations.add(new DetectedRelation(
              src,
              tgt,
              rNode.path("name").asText(null),
              tipoRel,
              rNode.path("multSource").asText("1"),
              rNode.path("multTarget").asText("*")
          ));
        }
      }
    }
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
          parseJsonIntoDetectedElements(parsed, outClasses, outRelations);
          return !outClasses.isEmpty();
        }
      }
    } catch (Exception ignored) {}
    return false;
  }

  private void extractFromUserHint(String hint, List<DetectedClass> outClasses, List<DetectedRelation> outRelations) {
    String[] parts = hint.split("[,;]");
    for (String p : parts) {
      String name = p.trim().replaceAll("[^a-zA-Z0-9_]", "");
      if (!name.isEmpty()) {
        DetectedClass dc = new DetectedClass(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        dc.attributes.add(new DetectedAttr("-", "id", "Long"));
        dc.attributes.add(new DetectedAttr("-", "nombre", "String"));
        outClasses.add(dc);
      }
    }
    for (int i = 0; i < outClasses.size() - 1; i++) {
      outRelations.add(new DetectedRelation(outClasses.get(i).name, outClasses.get(i + 1).name, null, TipoRelacionUML.ASOCIACION, "1", "*"));
    }
  }

  static class DetectedClass {
    String name;
    Double posX;
    Double posY;
    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    TipoElementoUML tipo = TipoElementoUML.CLASE;
    List<DetectedAttr> attributes = new ArrayList<>();
    List<DetectedMethod> methods = new ArrayList<>();
    DetectedClass(String name) { this.name = name; }
  }

  static class DetectedAttr {
    String visibility = "-";
    String name;
    String type = "String";
    DetectedAttr(String visibility, String name, String type) {
      this.visibility = visibility != null && !visibility.isBlank() ? visibility : "-";
      this.name = name;
      this.type = type != null && !type.isBlank() ? type : "String";
    }
    DetectedAttr(String name, String type) { this("-", name, type); }
  }

  static class DetectedMethod {
    String visibility = "+";
    String name;
    String returnType = "void";
    DetectedMethod(String visibility, String name, String returnType) {
      this.visibility = visibility != null && !visibility.isBlank() ? visibility : "+";
      this.name = name;
      this.returnType = returnType != null && !returnType.isBlank() ? returnType : "void";
    }
    DetectedMethod(String name, String returnType) { this("+", name, returnType); }
  }

  static class DetectedRelation {
    String source;
    String target;
    String name;
    TipoRelacionUML tipo = TipoRelacionUML.ASOCIACION;
    String multSource = "1";
    String multTarget = "*";
    DetectedRelation(String source, String target, String name, TipoRelacionUML tipo, String multSource, String multTarget) {
      this.source = source;
      this.target = target;
      this.name = name;
      this.tipo = tipo != null ? tipo : TipoRelacionUML.ASOCIACION;
      this.multSource = multSource != null && !multSource.isBlank() ? multSource : "1";
      this.multTarget = multTarget != null && !multTarget.isBlank() ? multTarget : "*";
    }
    DetectedRelation(String source, String target, TipoRelacionUML tipo, String multSource, String multTarget) {
      this(source, target, null, tipo, multSource, multTarget);
    }
  }

  private boolean tryNativeTesseract(String base64Image, List<DetectedClass> outClasses, List<DetectedRelation> outRelations) {
    if (base64Image == null || base64Image.isBlank()) return false;
    File tmpFile = null;
    try {
      String cleanBase64 = base64Image.contains(",")
          ? base64Image.substring(base64Image.indexOf(",") + 1)
          : base64Image;
      byte[] imgBytes = Base64.getDecoder().decode(cleanBase64.replaceAll("\\s+", ""));
      tmpFile = File.createTempFile("diag_ocr_", ".png");
      try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
        fos.write(imgBytes);
      }

      ProcessBuilder pb = new ProcessBuilder("tesseract", tmpFile.getAbsolutePath(), "stdout", "--psm", "11", "tsv");
      pb.redirectErrorStream(true);
      Process process = pb.start();

      List<TsvWord> words = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
        String line;
        boolean header = true;
        while ((line = reader.readLine()) != null) {
          if (header) {
            header = false;
            continue;
          }
          String[] parts = line.split("\t");
          if (parts.length >= 12 && "5".equals(parts[0].trim())) {
            try {
              int block = Integer.parseInt(parts[2].trim());
              int par = Integer.parseInt(parts[3].trim());
              int lineNum = Integer.parseInt(parts[4].trim());
              int left = Integer.parseInt(parts[6].trim());
              int top = Integer.parseInt(parts[7].trim());
              int width = Integer.parseInt(parts[8].trim());
              int height = Integer.parseInt(parts[9].trim());
              String text = parts[11].trim();
              if (!text.isEmpty()) {
                words.add(new TsvWord(block, par, lineNum, left, top, width, height, text));
              }
            } catch (Exception ignored) {}
          }
        }
      }
      process.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);

      if (words.isEmpty()) {
        return false;
      }

      Map<String, List<TsvWord>> lineGroups = new LinkedHashMap<>();
      for (TsvWord w : words) {
        String key = w.block + "_" + w.par + "_" + w.lineNum;
        lineGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(w);
      }

      List<OcrLine> ocrLines = new ArrayList<>();
      for (List<TsvWord> g : lineGroups.values()) {
        int minLeft = Integer.MAX_VALUE;
        int minTop = Integer.MAX_VALUE;
        int maxRight = Integer.MIN_VALUE;
        int maxBottom = Integer.MIN_VALUE;
        StringBuilder sb = new StringBuilder();
        for (TsvWord w : g) {
          if (w.left < minLeft) minLeft = w.left;
          if (w.top < minTop) minTop = w.top;
          if (w.left + w.width > maxRight) maxRight = w.left + w.width;
          if (w.top + w.height > maxBottom) maxBottom = w.top + w.height;
          if (!sb.isEmpty()) sb.append(" ");
          sb.append(w.text);
        }
        ocrLines.add(new OcrLine(sb.toString().trim(), minLeft, minTop, maxRight - minLeft, maxBottom - minTop));
      }

      List<DetectedClass> detected = new ArrayList<>();
      Set<String> commonTypes = Set.of("long", "string", "date", "double", "int", "integer", "boolean", "void", "float", "list", "set", "map");
      Set<String> relWords = Set.of("realiza", "tiene", "corresponde", "pertenece", "incluye", "asociado");

      for (OcrLine ol : ocrLines) {
        String text = ol.text.replace(":", "").trim();
        if (text.matches("^[A-Z][a-zA-Z0-9_]{2,}$") &&
            !commonTypes.contains(text.toLowerCase()) &&
            !relWords.contains(text.toLowerCase()) &&
            !ol.text.contains("()") &&
            !ol.text.startsWith("-") &&
            !ol.text.startsWith("+")) {
          if (detected.stream().noneMatch(c -> c.name.equalsIgnoreCase(text))) {
            DetectedClass dc = new DetectedClass(text);
            dc.posX = (double) ol.left;
            dc.posY = (double) ol.top;
            dc.minX = ol.left;
            dc.minY = ol.top;
            dc.maxX = ol.left + ol.width;
            dc.maxY = ol.top + ol.height;
            detected.add(dc);
          }
        }
      }

      if (detected.isEmpty()) {
        return false;
      }

      for (OcrLine ol : ocrLines) {
        String t = ol.text.trim();
        if (detected.stream().anyMatch(c -> c.name.equalsIgnoreCase(t.replace(":", "").trim()))) {
          continue;
        }

        boolean isMethod = t.contains("()") || t.contains("):") || (t.startsWith("+") && t.contains("("));
        boolean isAttr = !isMethod && (t.startsWith("-") || (t.contains(":") && !t.contains("realiza") && !t.contains("tiene")));

        if (isMethod || isAttr) {
          DetectedClass closest = null;
          double bestDist = Double.MAX_VALUE;
          for (DetectedClass dc : detected) {
            double cX = dc.posX != null ? dc.posX : 0.0;
            double cY = dc.posY != null ? dc.posY : 0.0;
            double vertPen = ol.top < cY - 15 ? 5000.0 : (ol.top - cY) * 0.35;
            double dist = Math.abs(ol.left - cX) + vertPen;
            if (dist < bestDist) {
              bestDist = dist;
              closest = dc;
            }
          }

          if (closest != null && bestDist < 1200.0) {
            closest.minX = Math.min(closest.minX, ol.left);
            closest.minY = Math.min(closest.minY, ol.top);
            closest.maxX = Math.max(closest.maxX, ol.left + ol.width);
            closest.maxY = Math.max(closest.maxY, ol.top + ol.height);

            if (isMethod) {
              String vis = "+";
              String raw = t;
              if (raw.startsWith("-") || raw.startsWith("+") || raw.startsWith("#")) {
                vis = raw.substring(0, 1);
                raw = raw.substring(1).trim();
              }
              String mName = raw;
              String mReturn = "void";
              int colonIdx = raw.indexOf(":");
              if (colonIdx > 0) {
                mReturn = capitalizeType(raw.substring(colonIdx + 1).trim());
                mName = raw.substring(0, colonIdx).trim();
              }
              mName = mName.replaceAll("[^a-zA-Z0-9_]", "").trim();
              if (mName.isEmpty()) mName = "metodo";
              closest.methods.add(new DetectedMethod(vis, mName, mReturn));
            } else {
              String vis = "-";
              String raw = t;
              if (raw.startsWith("-") || raw.startsWith("+") || raw.startsWith("#")) {
                vis = raw.substring(0, 1);
                raw = raw.substring(1).trim();
              }
              String aName = raw;
              String aType = "String";
              int colonIdx = raw.indexOf(":");
              if (colonIdx > 0) {
                aName = raw.substring(0, colonIdx).trim();
                aType = capitalizeType(raw.substring(colonIdx + 1).trim());
              }
              aName = aName.replaceAll("[^a-zA-Z0-9_]", "").trim();
              if (aName.equalsIgnoreCase("fechalnicio")) aName = "fechaInicio";
              if (!aName.isEmpty()) {
                closest.attributes.add(new DetectedAttr(vis, aName, aType));
              }
            }
          }
        }
      }

      // Normalizar coordenadas a canvas UML manteniendo la topología fiel
      if (!detected.isEmpty()) {
        double minXVal = detected.stream().mapToDouble(c -> c.posX != null ? c.posX : 0.0).min().orElse(0.0);
        double maxXVal = detected.stream().mapToDouble(c -> c.posX != null ? c.posX : 0.0).max().orElse(1.0);
        double minYVal = detected.stream().mapToDouble(c -> c.posY != null ? c.posY : 0.0).min().orElse(0.0);
        double maxYVal = detected.stream().mapToDouble(c -> c.posY != null ? c.posY : 0.0).max().orElse(1.0);
        double spanX = maxXVal > minXVal ? maxXVal - minXVal : 1.0;
        double spanY = maxYVal > minYVal ? maxYVal - minYVal : 1.0;

        for (DetectedClass c : detected) {
          if (c.posX != null) c.posX = (double) Math.round(80.0 + (((c.posX - minXVal) / spanX) * 750.0));
          if (c.posY != null) c.posY = (double) Math.round(60.0 + (((c.posY - minYVal) / spanY) * 380.0));
        }
      }

      // Extraer tokens no asignados para relaciones y multiplicidades
      List<OcrLine> unassignedLines = new ArrayList<>();
      for (OcrLine ol : ocrLines) {
        String t = ol.text.trim();
        if (detected.stream().anyMatch(c -> c.name.equalsIgnoreCase(t.replace(":", "").trim()))) continue;
        boolean isMethod = t.contains("()") || t.contains("):") || (t.startsWith("+") && t.contains("("));
        boolean isAttr = !isMethod && (t.startsWith("-") || (t.contains(":") && !t.contains("realiza") && !t.contains("tiene")));
        if (!isMethod && !isAttr) {
          unassignedLines.add(ol);
        }
      }

      List<MultToken> multTokens = new ArrayList<>();
      List<OcrLine> labelTokens = new ArrayList<>();

      for (OcrLine u : unassignedLines) {
        String m = parseOcrMultiplicity(u.text);
        if (m != null) {
          multTokens.add(new MultToken(m, u.left, u.top, u.width, u.height));
        } else {
          labelTokens.add(u);
        }
      }

      // Detección espacial de relaciones basada en etiquetas y dirección de flechas
      for (OcrLine u : labelTokens) {
        String raw = u.text.trim();
        String cleanWord = raw.replaceAll("[>»▶◀<v¥▼▲^:;,\\.\\-=_]", "").trim();

        if (cleanWord.length() >= 3) {
          double cx = u.left + (u.width / 2.0);
          double cy = u.top + (u.height / 2.0);

          String direction = "RIGHT";
          if (raw.matches(".*[v¥▼].*") || cleanWord.equalsIgnoreCase("contiene")) {
            direction = "DOWN";
          } else if (raw.matches(".*[▲\\^].*")) {
            direction = "UP";
          } else if (raw.matches(".*[<◀«].*")) {
            direction = "LEFT";
          } else if (raw.matches(".*[>▶»].*")) {
            direction = "RIGHT";
          }

          DetectedClass source = null;
          DetectedClass target = null;

          if ("RIGHT".equals(direction)) {
            double bestLeftDist = Double.MAX_VALUE;
            double bestRightDist = Double.MAX_VALUE;
            for (DetectedClass c : detected) {
              double cCenterY = (c.minY + c.maxY) / 2.0;
              double yDist = Math.abs(cy - cCenterY);
              if (c.maxX <= cx + 40 && yDist < 250) {
                double dist = cx - c.maxX + (yDist * 0.5);
                if (dist < bestLeftDist) { bestLeftDist = dist; source = c; }
              }
              if (c.minX >= cx - 40 && yDist < 250) {
                double dist = c.minX - cx + (yDist * 0.5);
                if (dist < bestRightDist) { bestRightDist = dist; target = c; }
              }
            }
          } else if ("DOWN".equals(direction)) {
            double bestTopDist = Double.MAX_VALUE;
            double bestBottomDist = Double.MAX_VALUE;
            for (DetectedClass c : detected) {
              double cCenterX = (c.minX + c.maxX) / 2.0;
              double xDist = Math.abs(cx - cCenterX);
              if (c.maxY <= cy + 40 && xDist < 350) {
                double dist = cy - c.maxY + (xDist * 0.5);
                if (dist < bestTopDist) { bestTopDist = dist; source = c; }
              }
              if (c.minY >= cy - 40 && xDist < 350) {
                double dist = c.minY - cy + (xDist * 0.5);
                if (dist < bestBottomDist) { bestBottomDist = dist; target = c; }
              }
            }
          } else if ("LEFT".equals(direction)) {
            double bestLeftDist = Double.MAX_VALUE;
            double bestRightDist = Double.MAX_VALUE;
            for (DetectedClass c : detected) {
              double cCenterY = (c.minY + c.maxY) / 2.0;
              double yDist = Math.abs(cy - cCenterY);
              if (c.maxX <= cx + 40 && yDist < 250) {
                double dist = cx - c.maxX + (yDist * 0.5);
                if (dist < bestRightDist) { bestRightDist = dist; source = c; }
              }
              if (c.minX >= cx - 40 && yDist < 250) {
                double dist = c.minX - cx + (yDist * 0.5);
                if (dist < bestLeftDist) { bestLeftDist = dist; target = c; }
              }
            }
          } else if ("UP".equals(direction)) {
            double bestTopDist = Double.MAX_VALUE;
            double bestBottomDist = Double.MAX_VALUE;
            for (DetectedClass c : detected) {
              double cCenterX = (c.minX + c.maxX) / 2.0;
              double xDist = Math.abs(cx - cCenterX);
              if (c.maxY <= cy + 40 && xDist < 350) {
                double dist = cy - c.maxY + (xDist * 0.5);
                if (dist < bestBottomDist) { bestBottomDist = dist; source = c; }
              }
              if (c.minY >= cy - 40 && xDist < 350) {
                double dist = c.minY - cy + (xDist * 0.5);
                if (dist < bestTopDist) { bestTopDist = dist; target = c; }
              }
            }
          }

          if (source != null && target != null && !source.name.equalsIgnoreCase(target.name)) {
            double srcEndpointX = "RIGHT".equals(direction) ? source.maxX : (source.minX + source.maxX) / 2.0;
            double srcEndpointY = "DOWN".equals(direction) ? source.maxY : (source.minY + source.maxY) / 2.0;

            double tgtEndpointX = "RIGHT".equals(direction) ? target.minX : (target.minX + target.maxX) / 2.0;
            double tgtEndpointY = "DOWN".equals(direction) ? target.minY : (target.minY + target.maxY) / 2.0;

            String mSource = null;
            String mTarget = null;
            double minSDist = Double.MAX_VALUE;
            double minTDist = Double.MAX_VALUE;

            for (MultToken mt : multTokens) {
              double mcx = mt.left + (mt.width / 2.0);
              double mcy = mt.top + (mt.height / 2.0);

              if ("DOWN".equals(direction) && mcy < source.minY + 60) continue;
              if ("RIGHT".equals(direction) && (mcy < Math.min(source.minY, target.minY) - 80 || mcy > Math.max(source.maxY, target.maxY) + 80)) continue;

              double sDist = Math.hypot(mcx - srcEndpointX, mcy - srcEndpointY);
              double tDist = Math.hypot(mcx - tgtEndpointX, mcy - tgtEndpointY);

              if (sDist < tDist) {
                if (sDist < minSDist && sDist < 220) {
                  minSDist = sDist;
                  mSource = mt.mult;
                }
              } else {
                if (tDist < minTDist && tDist < 220) {
                  minTDist = tDist;
                  mTarget = mt.mult;
                }
              }
            }

            if (mSource == null) mSource = cleanWord.equalsIgnoreCase("contiene") ? "1..*" : "1";
            if (mTarget == null) mTarget = cleanWord.equalsIgnoreCase("tiene") ? "0..1" : cleanWord.equalsIgnoreCase("contiene") ? "1" : "0..*";

            final String finalSrc = source.name;
            final String finalTgt = target.name;
            if (outRelations.stream().noneMatch(r -> r.source.equalsIgnoreCase(finalSrc) && r.target.equalsIgnoreCase(finalTgt))) {
              outRelations.add(new DetectedRelation(source.name, target.name, cleanWord, TipoRelacionUML.ASOCIACION, mSource, mTarget));
            }
          }
        }
      }

      // Respaldo semántico en firmas de métodos
      for (DetectedClass c : detected) {
        for (DetectedMethod m : c.methods) {
          for (DetectedClass other : detected) {
            if (!other.name.equalsIgnoreCase(c.name) && m.name.toLowerCase().contains(other.name.toLowerCase())) {
              if (outRelations.stream().noneMatch(r -> (r.source.equalsIgnoreCase(c.name) && r.target.equalsIgnoreCase(other.name)) ||
                                                      (r.source.equalsIgnoreCase(other.name) && r.target.equalsIgnoreCase(c.name)))) {
                outRelations.add(new DetectedRelation(c.name, other.name, "asociado", TipoRelacionUML.ASOCIACION, "1", m.returnType.contains("List") || m.returnType.contains("Set") ? "*" : "1"));
              }
            }
          }
        }
      }

      outClasses.addAll(detected);
      return !outClasses.isEmpty();
    } catch (Exception e) {
      log.warn("Fallo ejecutando Tesseract nativo local: {}", e.getMessage());
      return false;
    } finally {
      if (tmpFile != null && tmpFile.exists()) {
        tmpFile.delete();
      }
    }
  }

  private static class TsvWord {
    int block, par, lineNum, left, top, width, height;
    String text;
    TsvWord(int block, int par, int lineNum, int left, int top, int width, int height, String text) {
      this.block = block; this.par = par; this.lineNum = lineNum;
      this.left = left; this.top = top; this.width = width; this.height = height;
      this.text = text;
    }
  }

  private static class OcrLine {
    String text;
    int left, top, width, height;
    OcrLine(String text, int left, int top, int width, int height) {
      this.text = text; this.left = left; this.top = top; this.width = width; this.height = height;
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

  private static class MultToken {
    String mult;
    int left, top, width, height;
    MultToken(String mult, int left, int top, int width, int height) {
      this.mult = mult; this.left = left; this.top = top; this.width = width; this.height = height;
    }
  }

  private String parseOcrMultiplicity(String raw) {
    if (raw == null) return null;
    String clean = raw.trim();
    if (clean.matches(".*[a-zA-Z]{3,}.*")) return null;
    String s = clean.toLowerCase().replaceAll("[^0-9\\.\\*bnm]", "");
    if (s.equals("0.") || s.equals("0..") || s.contains("0.*") || s.contains("0..*") || s.contains("0*")) return "0..*";
    if (s.equals("1.") || s.equals("1..") || s.contains("1.*") || s.contains("1..*") || s.contains("1*") || s.equals("b") || s.equals("bi")) return "1..*";
    if (s.contains("0.1") || s.contains("0..1")) return "0..1";
    if (s.contains("1.1") || s.contains("1..1")) return "1..1";
    if (s.equals("1")) return "1";
    if (s.equals("0")) return "0";
    if (s.equals("*") || s.equals("n") || s.equals("m")) return "*";
    return null;
  }

  private String capitalizeType(String type) {
    if (type == null || type.isBlank()) return "String";
    String s = type.trim();
    if (s.startsWith("List<") || s.startsWith("Set<") || s.startsWith("Collection<") || s.startsWith("Map<")) return s;
    if (s.equalsIgnoreCase("int") || s.equalsIgnoreCase("integer")) return "Integer";
    if (s.equalsIgnoreCase("long")) return "Long";
    if (s.equalsIgnoreCase("double") || s.equalsIgnoreCase("float") || s.equalsIgnoreCase("decimal")) return "Double";
    if (s.equalsIgnoreCase("bool") || s.equalsIgnoreCase("boolean")) return "Boolean";
    if (s.equalsIgnoreCase("date") || s.equalsIgnoreCase("datetime") || s.equalsIgnoreCase("localdate")) return "Date";
    if (s.equalsIgnoreCase("void")) return "void";
    if (s.equalsIgnoreCase("uuid")) return "UUID";
    return s;
  }
}
