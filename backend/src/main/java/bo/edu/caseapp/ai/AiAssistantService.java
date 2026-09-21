package bo.edu.caseapp.ai;

import bo.edu.caseapp.ai.AiRequests.ChatRequest;
import bo.edu.caseapp.ai.AiRequests.CommandRequest;
import bo.edu.caseapp.ai.AiResponses.ChatResponse;
import bo.edu.caseapp.ai.AiResponses.CommandResponse;
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
