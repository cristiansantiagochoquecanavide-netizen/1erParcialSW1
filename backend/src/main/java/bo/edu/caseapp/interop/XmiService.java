package bo.edu.caseapp.interop;

import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.error.NotFoundException;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.*;
import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import bo.edu.caseapp.uml.collab.UmlWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

@Service
@Transactional
public class XmiService {
  private final DiagramaClaseRepository diagramRepo;
  private final ElementoUMLRepository elementRepo;
  private final RelacionUMLRepository relationRepo;
  private final ProjectService projectService;
  private final UmlService umlService;
  private final UmlWebSocketHandler wsHandler;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public XmiService(
      DiagramaClaseRepository diagramRepo,
      ElementoUMLRepository elementRepo,
      RelacionUMLRepository relationRepo,
      ProjectService projectService,
      UmlService umlService,
      UmlWebSocketHandler wsHandler
  ) {
    this.diagramRepo = diagramRepo;
    this.elementRepo = elementRepo;
    this.relationRepo = relationRepo;
    this.projectService = projectService;
    this.umlService = umlService;
    this.wsHandler = wsHandler;
  }

  @Transactional(readOnly = true)
  public String exportXmi(UUID projectId, UUID diagramId, String email) {
    projectService.requireAccess(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);
    List<ElementoUML> elements = elementRepo.findByDiagramaId(diagramId);
    List<RelacionUML> relations = relationRepo.findByDiagramaId(diagramId);

    // Mapear nombres de clases a su xmi:id y pre-parsear propiedades JSON
    Map<String, String> classNameToIdMap = new HashMap<>();
    Map<UUID, JsonNode> propsMap = new HashMap<>();
    for (ElementoUML el : elements) {
      classNameToIdMap.put(el.getNombre().trim().toLowerCase(), "el_" + el.getId());
      try {
        if (el.getPropiedadesJson() != null && !el.getPropiedadesJson().isBlank()) {
          propsMap.put(el.getId(), objectMapper.readTree(el.getPropiedadesJson()));
        }
      } catch (Exception ignored) {}
    }

    // Recopilar todos los tipos de datos únicos de atributos y operaciones
    Map<String, String> typeToIdMap = new LinkedHashMap<>();
    for (ElementoUML el : elements) {
      JsonNode root = propsMap.get(el.getId());
      if (root == null) continue;
      JsonNode attrs = root.path("attributes");
      if (attrs.isArray()) {
        for (JsonNode a : attrs) {
          String rawType = a.path("type").asText("String").trim();
          if (rawType.isBlank()) rawType = "String";
          if (!classNameToIdMap.containsKey(rawType.toLowerCase())) {
            typeToIdMap.putIfAbsent(rawType, "dt_" + sanitizeId(rawType));
          }
        }
      }
      JsonNode methods = root.path("methods");
      if (methods.isArray()) {
        for (JsonNode m : methods) {
          String retType = m.path("returnType").asText("void").trim();
          if (retType.isBlank()) retType = "void";
          if (!classNameToIdMap.containsKey(retType.toLowerCase()) && !"void".equalsIgnoreCase(retType)) {
            typeToIdMap.putIfAbsent(retType, "dt_" + sanitizeId(retType));
          }
        }
      }
    }

    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<xmi:XMI xmi:version=\"2.1\" xmlns:uml=\"http://schema.omg.org/spec/UML/2.1\" xmlns:xmi=\"http://schema.omg.org/spec/XMI/2.1\">\n");
    sb.append("  <xmi:Documentation exporter=\"Enterprise Architect\" exporterVersion=\"6.5\"/>\n");
    sb.append("  <uml:Model xmi:type=\"uml:Model\" xmi:id=\"model_").append(diagram.getId()).append("\" name=\"").append(escapeXml(diagram.getNombre())).append("\">\n");

    // 1. Declarar tipos primitivos en el modelo UML para resolución por IDREF
    for (Map.Entry<String, String> entry : typeToIdMap.entrySet()) {
      String typeName = entry.getKey();
      String typeId = entry.getValue();
      sb.append("    <packagedElement xmi:type=\"uml:PrimitiveType\" xmi:id=\"")
        .append(typeId).append("\" name=\"").append(escapeXml(typeName)).append("\"/>\n");
    }

    // 2. Clases y elementos con sus atributos tipados y operaciones
    for (ElementoUML el : elements) {
      String umlType = switch (el.getTipoElemento()) {
        case INTERFAZ -> "uml:Interface";
        case ENUMERACION -> "uml:Enumeration";
        default -> "uml:Class";
      };

      sb.append("    <packagedElement xmi:type=\"").append(umlType).append("\" xmi:id=\"el_").append(el.getId()).append("\" name=\"").append(escapeXml(el.getNombre())).append("\"");
      if (el.getTipoElemento() == TipoElementoUML.CLASE_ABSTRACTA) {
        sb.append(" isAbstract=\"true\"");
      }
      sb.append(">\n");

      JsonNode root = propsMap.get(el.getId());
      if (root != null) {
        JsonNode attrs = root.path("attributes");
        if (attrs.isArray()) {
          for (JsonNode a : attrs) {
            String attrId = a.path("id").asText(UUID.randomUUID().toString());
            String name = a.path("name").asText().trim();
            if (name.isBlank()) continue;
            String rawType = a.path("type").asText("String").trim();
            if (rawType.isBlank()) rawType = "String";
            String vis = a.path("visibility").asText("+");
            String visibility = switch (vis) {
              case "-" -> "private";
              case "#" -> "protected";
              case "~" -> "package";
              default -> "public";
            };

            String resolvedTypeId = classNameToIdMap.getOrDefault(
                rawType.toLowerCase(),
                typeToIdMap.getOrDefault(rawType, "dt_" + sanitizeId(rawType))
            );

            sb.append("      <ownedAttribute xmi:type=\"uml:Property\" xmi:id=\"attr_").append(attrId)
              .append("\" name=\"").append(escapeXml(name))
              .append("\" visibility=\"").append(visibility)
              .append("\" type=\"").append(resolvedTypeId).append("\">\n");
            sb.append("        <type xmi:type=\"uml:PrimitiveType\" xmi:idref=\"").append(resolvedTypeId).append("\"/>\n");
            sb.append("      </ownedAttribute>\n");
          }
        }

        JsonNode methods = root.path("methods");
        if (methods.isArray()) {
          for (JsonNode m : methods) {
            String opId = m.path("id").asText(UUID.randomUUID().toString());
            String opName = m.path("name").asText().trim();
            if (opName.isBlank()) continue;
            String retType = m.path("returnType").asText("void").trim();
            if (retType.isBlank()) retType = "void";
            String vis = m.path("visibility").asText("+");
            String visibility = switch (vis) {
              case "-" -> "private";
              case "#" -> "protected";
              case "~" -> "package";
              default -> "public";
            };

            sb.append("      <ownedOperation xmi:type=\"uml:Operation\" xmi:id=\"op_").append(opId)
              .append("\" name=\"").append(escapeXml(opName))
              .append("\" visibility=\"").append(visibility)
              .append("\">\n");

            if (!"void".equalsIgnoreCase(retType)) {
              String retTypeId = classNameToIdMap.getOrDefault(
                  retType.toLowerCase(),
                  typeToIdMap.getOrDefault(retType, "dt_" + sanitizeId(retType))
              );
              sb.append("        <ownedParameter xmi:type=\"uml:Parameter\" xmi:id=\"param_ret_").append(opId)
                .append("\" direction=\"return\" type=\"").append(retTypeId).append("\">\n");
              sb.append("          <type xmi:type=\"uml:PrimitiveType\" xmi:idref=\"").append(retTypeId).append("\"/>\n");
              sb.append("        </ownedParameter>\n");
            }
            sb.append("      </ownedOperation>\n");
          }
        }
      }

      sb.append("    </packagedElement>\n");
    }

    // 3. Relaciones con cardinalidades completas en <uml:Model>
    for (RelacionUML rel : relations) {
      if (rel.getElementoOrigen() == null || rel.getElementoDestino() == null) continue;
      String origId = "el_" + rel.getElementoOrigen().getId();
      String destId = "el_" + rel.getElementoDestino().getId();

      if (rel.getTipoRelacion() == TipoRelacionUML.HERENCIA) {
        sb.append("    <packagedElement xmi:type=\"uml:Generalization\" xmi:id=\"rel_").append(rel.getId()).append("\" specific=\"").append(origId).append("\" general=\"").append(destId).append("\"/>\n");
      } else {
        String assocType = rel.getTipoRelacion() == TipoRelacionUML.COMPOSICION ? "composite" : rel.getTipoRelacion() == TipoRelacionUML.AGREGACION ? "shared" : "none";
        String multOrig = rel.getMultiplicidadOrigen() != null ? rel.getMultiplicidadOrigen().trim() : "";
        String multDest = rel.getMultiplicidadDestino() != null ? rel.getMultiplicidadDestino().trim() : "";

        sb.append("    <packagedElement xmi:type=\"uml:Association\" xmi:id=\"rel_").append(rel.getId()).append("\" name=\"").append(escapeXml(rel.getNombreRelacion() != null ? rel.getNombreRelacion() : "")).append("\">\n");
        sb.append("      <memberEnd xmi:idref=\"end_orig_").append(rel.getId()).append("\"/>\n");
        sb.append("      <memberEnd xmi:idref=\"end_dest_").append(rel.getId()).append("\"/>\n");

        // Extremo Origen
        sb.append("      <ownedEnd xmi:type=\"uml:Property\" xmi:id=\"end_orig_").append(rel.getId()).append("\" type=\"").append(origId).append("\" aggregation=\"").append(assocType).append("\"");
        if (!multOrig.isBlank()) {
          sb.append(" multiplicity=\"").append(escapeXml(multOrig)).append("\"");
        }
        sb.append(">\n");
        if (!multOrig.isBlank()) {
          String[] bounds = parseMultiplicityBounds(multOrig);
          sb.append("        <lowerValue xmi:type=\"uml:LiteralString\" xmi:id=\"low_orig_").append(rel.getId()).append("\" value=\"").append(escapeXml(bounds[0])).append("\"/>\n");
          sb.append("        <upperValue xmi:type=\"uml:LiteralString\" xmi:id=\"upp_orig_").append(rel.getId()).append("\" value=\"").append(escapeXml(bounds[1])).append("\"/>\n");
        }
        sb.append("      </ownedEnd>\n");

        // Extremo Destino
        sb.append("      <ownedEnd xmi:type=\"uml:Property\" xmi:id=\"end_dest_").append(rel.getId()).append("\" type=\"").append(destId).append("\"");
        if (!multDest.isBlank()) {
          sb.append(" multiplicity=\"").append(escapeXml(multDest)).append("\"");
        }
        sb.append(">\n");
        if (!multDest.isBlank()) {
          String[] bounds = parseMultiplicityBounds(multDest);
          sb.append("        <lowerValue xmi:type=\"uml:LiteralString\" xmi:id=\"low_dest_").append(rel.getId()).append("\" value=\"").append(escapeXml(bounds[0])).append("\"/>\n");
          sb.append("        <upperValue xmi:type=\"uml:LiteralString\" xmi:id=\"upp_dest_").append(rel.getId()).append("\" value=\"").append(escapeXml(bounds[1])).append("\"/>\n");
        }
        sb.append("      </ownedEnd>\n");

        sb.append("    </packagedElement>\n");
      }
    }

    sb.append("  </uml:Model>\n");

    // 4. Extensión nativa de Enterprise Architect (preserva tipos de datos exactos, operaciones y geometría visual)
    sb.append("  <xmi:Extension extender=\"Enterprise Architect\" extenderID=\"6.5\">\n");
    sb.append("    <elements>\n");

    for (ElementoUML el : elements) {
      String sType = switch (el.getTipoElemento()) {
        case INTERFAZ -> "Interface";
        case ENUMERACION -> "Enumeration";
        default -> "Class";
      };
      String isAbstractStr = (el.getTipoElemento() == TipoElementoUML.CLASE_ABSTRACTA) ? "true" : "false";

      sb.append("      <element xmi:idref=\"el_").append(el.getId())
        .append("\" xmi:type=\"uml:").append(sType)
        .append("\" name=\"").append(escapeXml(el.getNombre()))
        .append("\" scope=\"public\">\n");
      sb.append("        <properties sType=\"").append(sType)
        .append("\" nType=\"0\" scope=\"public\" isRoot=\"false\" isLeaf=\"false\" isAbstract=\"")
        .append(isAbstractStr).append("\"/>\n");
      sb.append("        <project author=\"Herramienta CASE\" version=\"1.0\" status=\"Proposed\"/>\n");

      JsonNode root = propsMap.get(el.getId());
      if (root != null) {
        JsonNode attrs = root.path("attributes");
        if (attrs.isArray() && attrs.size() > 0) {
          sb.append("        <attributes>\n");
          int pos = 0;
          for (JsonNode a : attrs) {
            String attrId = a.path("id").asText(UUID.randomUUID().toString());
            String name = a.path("name").asText().trim();
            if (name.isBlank()) continue;
            String rawType = a.path("type").asText("String").trim();
            if (rawType.isBlank()) rawType = "String";
            String vis = a.path("visibility").asText("+");
            String scope = switch (vis) {
              case "-" -> "Private";
              case "#" -> "Protected";
              case "~" -> "Package";
              default -> "Public";
            };

            sb.append("          <attribute xmi:idref=\"attr_").append(attrId)
              .append("\" name=\"").append(escapeXml(name))
              .append("\" scope=\"").append(scope)
              .append("\" type=\"").append(escapeXml(rawType)).append("\">\n");
            sb.append("            <properties type=\"").append(escapeXml(rawType))
              .append("\" precision=\"0\" scale=\"0\" collection=\"false\" position=\"").append(pos)
              .append("\" lowerBound=\"1\" upperBound=\"1\" duplicates=\"0\" changeability=\"changeable\"/>\n");
            sb.append("            <coords/>\n");
            sb.append("            <containment containment=\"Not Specified\" position=\"").append(pos).append("\"/>\n");
            sb.append("          </attribute>\n");
            pos++;
          }
          sb.append("        </attributes>\n");
        }

        JsonNode methods = root.path("methods");
        if (methods.isArray() && methods.size() > 0) {
          sb.append("        <operations>\n");
          for (JsonNode m : methods) {
            String opId = m.path("id").asText(UUID.randomUUID().toString());
            String opName = m.path("name").asText().trim();
            if (opName.isBlank()) continue;
            String retType = m.path("returnType").asText("void").trim();
            if (retType.isBlank()) retType = "void";
            String vis = m.path("visibility").asText("+");
            String scope = switch (vis) {
              case "-" -> "Private";
              case "#" -> "Protected";
              case "~" -> "Package";
              default -> "Public";
            };

            sb.append("          <operation xmi:idref=\"op_").append(opId)
              .append("\" name=\"").append(escapeXml(opName))
              .append("\" type=\"").append(escapeXml(retType)).append("\">\n");
            sb.append("            <properties type=\"").append(escapeXml(retType))
              .append("\" scope=\"").append(scope).append("\"/>\n");
            sb.append("          </operation>\n");
          }
          sb.append("        </operations>\n");
        }
      }

      sb.append("      </element>\n");
    }

    sb.append("    </elements>\n");

    // Conectores en la extensión de EA
    if (!relations.isEmpty()) {
      sb.append("    <connectors>\n");
      for (RelacionUML rel : relations) {
        if (rel.getElementoOrigen() == null || rel.getElementoDestino() == null) continue;
        String origId = "el_" + rel.getElementoOrigen().getId();
        String destId = "el_" + rel.getElementoDestino().getId();
        String origName = rel.getElementoOrigen().getNombre();
        String destName = rel.getElementoDestino().getNombre();
        String multOrig = rel.getMultiplicidadOrigen() != null ? rel.getMultiplicidadOrigen().trim() : "";
        String multDest = rel.getMultiplicidadDestino() != null ? rel.getMultiplicidadDestino().trim() : "";
        String relName = rel.getNombreRelacion() != null ? rel.getNombreRelacion() : "";

        String eaType = switch (rel.getTipoRelacion()) {
          case HERENCIA -> "Generalization";
          case AGREGACION -> "Aggregation";
          case COMPOSICION -> "Composition";
          default -> "Association";
        };
        String aggAttr = rel.getTipoRelacion() == TipoRelacionUML.COMPOSICION ? "composite" :
                         rel.getTipoRelacion() == TipoRelacionUML.AGREGACION ? "shared" : "none";

        sb.append("      <connector xmi:idref=\"rel_").append(rel.getId()).append("\">\n");
        sb.append("        <source xmi:idref=\"").append(origId).append("\">\n");
        sb.append("          <model type=\"Class\" name=\"").append(escapeXml(origName)).append("\"/>\n");
        sb.append("          <type multiplicity=\"").append(escapeXml(multOrig))
          .append("\" aggregation=\"").append(aggAttr).append("\"/>\n");
        sb.append("        </source>\n");
        sb.append("        <target xmi:idref=\"").append(destId).append("\">\n");
        sb.append("          <model type=\"Class\" name=\"").append(escapeXml(destName)).append("\"/>\n");
        sb.append("          <type multiplicity=\"").append(escapeXml(multDest))
          .append("\" aggregation=\"none\"/>\n");
        sb.append("        </target>\n");
        sb.append("        <properties ea_type=\"").append(eaType)
          .append("\" direction=\"Unspecified\" name=\"").append(escapeXml(relName)).append("\"/>\n");
        sb.append("      </connector>\n");
      }
      sb.append("    </connectors>\n");
    }

    // Diagrama con geometría exacta para Enterprise Architect
    sb.append("    <diagrams>\n");
    sb.append("      <diagram xmi:id=\"ea_diag_").append(diagram.getId()).append("\">\n");
    sb.append("        <model package=\"model_").append(diagram.getId())
      .append("\" localID=\"1\" type=\"Logical\" name=\"").append(escapeXml(diagram.getNombre())).append("\"/>\n");
    sb.append("        <properties name=\"").append(escapeXml(diagram.getNombre())).append("\" type=\"Logical\"/>\n");
    sb.append("        <project author=\"Herramienta CASE\" version=\"1.0\" created=\"2026-09-22 00:00:00\" modified=\"2026-09-22 00:00:00\"/>\n");
    sb.append("        <elements>\n");

    int seq = 1;
    for (ElementoUML el : elements) {
      int left = Math.max(30, (int) Math.round(el.getPosicionX()));
      int top = Math.max(30, (int) Math.round(el.getPosicionY()));
      int width = 200;
      int attrCount = 0;
      int methodCount = 0;
      JsonNode root = propsMap.get(el.getId());
      if (root != null) {
        attrCount = root.path("attributes").size();
        methodCount = root.path("methods").size();
      }
      int height = Math.max(110, 50 + (attrCount * 22) + (methodCount * 22));
      int right = left + width;
      int bottom = top + height;
      String duid = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();

      sb.append("          <element subject=\"el_").append(el.getId())
        .append("\" geometry=\"Left=").append(left)
        .append(";Top=").append(top)
        .append(";Right=").append(right)
        .append(";Bottom=").append(bottom)
        .append(";\" seqno=\"").append(seq)
        .append("\" style=\"DUID=").append(duid).append(";\"/>\n");
      seq++;
    }

    sb.append("        </elements>\n");
    sb.append("      </diagram>\n");
    sb.append("    </diagrams>\n");
    sb.append("  </xmi:Extension>\n");
    sb.append("</xmi:XMI>\n");

    return sb.toString();
  }

  public DiagramFullView importXmi(UUID projectId, UUID diagramId, String email, String xmlContent) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);

    try {
      // 1. Limpiar BOM y normalizar encoding del prólogo XML para evitar fallos de parser con windows-1252 de EA
      String cleanContent = xmlContent;
      if (cleanContent.startsWith("\uFEFF")) {
        cleanContent = cleanContent.substring(1);
      }
      cleanContent = cleanContent.replaceFirst("(?i)encoding=[\"'][^\"']+[\"']", "encoding=\"UTF-8\"");

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setIgnoringComments(true);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(cleanContent)));

      // 2. Recorrer de forma recursiva todo el árbol DOM para soportar cualquier estructura de Enterprise Architect
      List<Element> allElements = new ArrayList<>();
      collectAllElements(doc.getDocumentElement(), allElements);

      // 3. Primer paso: Mapear identificadores a nombres (para DataPoint/DataType/Class/Classifier de EA)
      Map<String, String> idToNameMap = new HashMap<>();
      Map<String, Element> idToDomElementMap = new HashMap<>();
      Map<String, Element> idrefToExtensionElementMap = new HashMap<>();

      for (Element el : allElements) {
        String id = getAttr(el, "xmi:id", "xmi.id", "id", "xmiId");
        String name = getAttr(el, "name", "Name", "label");
        if (!id.isBlank()) {
          idToDomElementMap.put(id, el);
          if (!name.isBlank()) {
            idToNameMap.put(id, name.trim());
          }
        }
        // EA a veces define los nombres en extensiones <element xmi:idref="EAID_..." name="..." ...>
        String idref = getAttr(el, "xmi:idref", "xmi.idref", "idref");
        if (!idref.isBlank()) {
          idrefToExtensionElementMap.put(idref, el);
          if (!name.isBlank()) {
            idToNameMap.putIfAbsent(idref, name.trim());
          }
        }
      }

      Map<String, ElementoUML> idToElementMap = new HashMap<>();
      Map<String, ElementoUML> nameToElementMap = new HashMap<>();
      Set<String> processedClassNames = new HashSet<>();

      // Mapear coordenadas de diagramas si existen (Enterprise Architect o herramientas compatibles)
      Map<String, double[]> idToPositionMap = new HashMap<>();
      for (Element dEl : allElements) {
        String local = getLocalName(dEl);
        if ("element".equalsIgnoreCase(local)) {
          String subject = getAttr(dEl, "subject");
          String geom = getAttr(dEl, "geometry");
          if (!subject.isBlank() && !geom.isBlank()) {
            double[] pos = parseCoordinatesFromGeometry(geom);
            if (pos != null) {
              idToPositionMap.put(subject, pos);
            }
          }
        }
      }

      double curX = 100.0;
      double curY = 100.0;
      int count = 0;

      // 4. Segundo paso: Identificar y crear Clases, Tablas, Interfaces y Enumeraciones
      for (Element el : allElements) {
        if (!isClassOrTableElement(el)) continue;

        String xmiId = getAttr(el, "xmi:id", "xmi.id", "id", "xmiId", "xmi:idref", "xmi.idref", "idref");
        String name = getAttr(el, "name", "Name");
        if (name.isBlank() && !xmiId.isBlank()) {
          name = idToNameMap.getOrDefault(xmiId, "");
        }
        if (name.isBlank()) {
          // Intentar obtener de nodo hijo <name>
          List<Element> nameNodes = findChildElementsByTagNames(el, "name", "Name");
          if (!nameNodes.isEmpty()) {
            name = nameNodes.get(0).getTextContent().trim();
          }
        }

        if (name.isBlank()) continue;
        String normalizedName = name.trim();
        // Ignorar clases raíz de sistema de EA (EARootClass) y tipos de datos primitivos (long, Integer, String, etc.)
        if (isPrimitiveOrSystemElement(normalizedName)) {
          continue;
        }
        if (processedClassNames.contains(normalizedName.toLowerCase())) {
          // Ya procesada para evitar duplicaciones por extensiones de EA
          continue;
        }

        TipoElementoUML tipo = determineElementType(el);

        // Extraer atributos / columnas de la clase o tabla
        ObjectNode propNode = objectMapper.createObjectNode();
        ArrayNode attrs = propNode.putArray("attributes");

        List<Element> attrElements = findDescendantElementsByTagNames(el,
            "ownedAttribute", "Attribute", "attribute", "column", "property");

        // Si no se encontraron atributos en el elemento principal pero existe en la extensión de EA
        if (attrElements.isEmpty() && !xmiId.isBlank() && idrefToExtensionElementMap.containsKey(xmiId)) {
          Element extEl = idrefToExtensionElementMap.get(xmiId);
          attrElements = findDescendantElementsByTagNames(extEl, "attribute", "Attribute");
        }

        for (Element a : attrElements) {
          // Evitar propiedades que representen extremos de asociación en UML 2.1
          if (!getAttr(a, "association").isBlank()) continue;

          String aName = getAttr(a, "name", "Name");
          if (aName.isBlank()) continue;

          String aVisRaw = getAttr(a, "visibility", "Visibility", "scope", "Scope");
          String visibility = switch (aVisRaw.toLowerCase()) {
            case "private", "-" -> "-";
            case "protected", "#" -> "#";
            case "package", "package_private", "~" -> "~";
            default -> "+";
          };

          String attrType = resolveAttributeType(a, idToNameMap);

          attrs.addObject()
              .put("id", UUID.randomUUID().toString())
              .put("visibility", visibility)
              .put("name", aName.trim())
              .put("type", attrType);
        }

        // Extraer métodos / operaciones
        ArrayNode methods = propNode.putArray("methods");
        List<Element> opElements = findDescendantElementsByTagNames(el,
            "ownedOperation", "Operation", "operation", "method");

        // Si no se encontraron operaciones en el elemento principal pero existe en la extensión de EA
        if (opElements.isEmpty() && !xmiId.isBlank() && idrefToExtensionElementMap.containsKey(xmiId)) {
          Element extEl = idrefToExtensionElementMap.get(xmiId);
          opElements = findDescendantElementsByTagNames(extEl, "operation", "Operation");
        }

        for (Element op : opElements) {
          String opName = getAttr(op, "name", "Name");
          if (opName.isBlank()) continue;

          String opVisRaw = getAttr(op, "visibility", "Visibility", "scope", "Scope");
          String visibility = switch (opVisRaw.toLowerCase()) {
            case "private", "-" -> "-";
            case "protected", "#" -> "#";
            case "package", "~" -> "~";
            default -> "+";
          };

          String returnType = resolveOperationReturnType(op, idToNameMap);
          String params = resolveOperationParameters(op, idToNameMap);

          methods.addObject()
              .put("id", UUID.randomUUID().toString())
              .put("visibility", visibility)
              .put("name", opName.trim())
              .put("returnType", returnType)
              .put("parameters", params);
        }

        // Posicionamiento: usar coordenadas geométricas si existen, o cuadrícula ordenada
        double posX = curX + ((count % 4) * 280.0);
        double posY = curY + ((count / 4) * 240.0);
        if (!xmiId.isBlank()) {
          if (idToPositionMap.containsKey(xmiId)) {
            double[] pos = idToPositionMap.get(xmiId);
            posX = pos[0];
            posY = pos[1];
          } else if (xmiId.startsWith("el_") && idToPositionMap.containsKey(xmiId.substring(3))) {
            double[] pos = idToPositionMap.get(xmiId.substring(3));
            posX = pos[0];
            posY = pos[1];
          } else if (idToPositionMap.containsKey("el_" + xmiId)) {
            double[] pos = idToPositionMap.get("el_" + xmiId);
            posX = pos[0];
            posY = pos[1];
          }
        }

        ElementoUML nuevo = new ElementoUML(
            diagram,
            tipo,
            normalizedName,
            posX,
            posY,
            propNode.toString()
        );
        nuevo = elementRepo.save(nuevo);

        if (!xmiId.isBlank()) {
          idToElementMap.put(xmiId, nuevo);
          idToElementMap.put("el_" + nuevo.getId(), nuevo);
        }
        nameToElementMap.put(normalizedName.toLowerCase(), nuevo);
        processedClassNames.add(normalizedName.toLowerCase());
        count++;
      }

      // 5. Tercer paso: Importar Generalizaciones / Herencias (XMI 1.1 y XMI 2.1)
      Set<String> processedInheritances = new HashSet<>();
      for (Element el : allElements) {
        String local = getLocalName(el);
        String type = getAttr(el, "xmi:type", "type");

        if ("Generalization".equalsIgnoreCase(local) || type.toLowerCase().endsWith("generalization")) {
          String childId = getAttr(el, "specific", "subtype", "child");
          String parentId = getAttr(el, "general", "supertype", "parent");

          // En EA XMI 1.1 o UML 2.1 a veces el child es el elemento contenedor
          if (childId.isBlank() && el.getParentNode() instanceof Element parentEl) {
            childId = getAttr(parentEl, "xmi:id", "xmi.id", "id");
          }

          // Si parentId viene en nodo hijo <UML:Generalization.parent><UML:Class xmi.idref="..."/></UML:Generalization.parent>
          if (parentId.isBlank()) {
            List<Element> parentRefs = findDescendantElementsByTagNames(el, "Classifier", "Class");
            for (Element pr : parentRefs) {
              String ref = getAttr(pr, "xmi:idref", "xmi.idref", "idref");
              if (!ref.isBlank()) {
                parentId = ref;
                break;
              }
            }
          }

          ElementoUML childEl = resolveElement(childId, idToElementMap, nameToElementMap);
          ElementoUML parentEl = resolveElement(parentId, idToElementMap, nameToElementMap);

          if (childEl != null && parentEl != null && !childEl.getId().equals(parentEl.getId())) {
            String key = childEl.getId() + "->" + parentEl.getId();
            if (!processedInheritances.contains(key)) {
              processedInheritances.add(key);
              String relName = getAttr(el, "name", "Name");
              RelacionUML herencia = new RelacionUML(
                  diagram,
                  childEl,
                  parentEl,
                  relName.isBlank() ? null : relName,
                  TipoRelacionUML.HERENCIA,
                  null,
                  null
              );
              relationRepo.save(herencia);
            }
          }
        }
      }

      // 6. Cuarto paso: Importar Asociaciones, Composiciones y Agregaciones (EA XMI 1.1 y 2.1)
      Set<String> processedAssociations = new HashSet<>();
      for (Element el : allElements) {
        String local = getLocalName(el);
        String type = getAttr(el, "xmi:type", "type");

        if ("Association".equalsIgnoreCase(local) || type.toLowerCase().endsWith("association")) {
          String relName = getAttr(el, "name", "Name");

          // Buscar los extremos de la asociación
          List<Element> ends = findDescendantElementsByTagNames(el, "ownedEnd", "AssociationEnd");

          // Caso estándar XMI 2.1 / 1.1 con 2 extremos definidos
          if (ends.size() >= 2) {
            Element end1 = ends.get(0);
            Element end2 = ends.get(1);

            String end1Ref = getAttr(end1, "type", "participant");
            if (end1Ref.isBlank()) {
              List<Element> pNodes = findDescendantElementsByTagNames(end1, "Classifier", "Class", "participant");
              for (Element p : pNodes) {
                String ref = getAttr(p, "xmi:idref", "xmi.idref", "idref", "type");
                if (!ref.isBlank()) { end1Ref = ref; break; }
              }
            }

            String end2Ref = getAttr(end2, "type", "participant");
            if (end2Ref.isBlank()) {
              List<Element> pNodes = findDescendantElementsByTagNames(end2, "Classifier", "Class", "participant");
              for (Element p : pNodes) {
                String ref = getAttr(p, "xmi:idref", "xmi.idref", "idref", "type");
                if (!ref.isBlank()) { end2Ref = ref; break; }
              }
            }

            ElementoUML origEl = resolveElement(end1Ref, idToElementMap, nameToElementMap);
            ElementoUML destEl = resolveElement(end2Ref, idToElementMap, nameToElementMap);

            if (origEl != null && destEl != null) {
              String key = origEl.getId().compareTo(destEl.getId()) <= 0
                  ? origEl.getId() + "_" + destEl.getId()
                  : destEl.getId() + "_" + origEl.getId();

              if (!processedAssociations.contains(key) || !relName.isBlank()) {
                processedAssociations.add(key);

                String multOrig = extractMultiplicityFromEnd(end1);
                String multDest = extractMultiplicityFromEnd(end2);

                String agg1 = getAttr(end1, "aggregation").toLowerCase();
                String agg2 = getAttr(end2, "aggregation").toLowerCase();

                TipoRelacionUML tipoRel = TipoRelacionUML.ASOCIACION;
                if (agg1.contains("comp") || agg2.contains("comp")) {
                  tipoRel = TipoRelacionUML.COMPOSICION;
                } else if (agg1.contains("shared") || agg2.contains("shared") || agg1.contains("aggreg") || agg2.contains("aggreg")) {
                  tipoRel = TipoRelacionUML.AGREGACION;
                }

                RelacionUML relacion = new RelacionUML(
                    diagram,
                    origEl,
                    destEl,
                    relName.isBlank() ? null : relName,
                    tipoRel,
                    multOrig.isBlank() ? null : multOrig,
                    multDest.isBlank() ? null : multDest
                );
                relationRepo.save(relacion);
              }
            }
          }
        }
      }

      wsHandler.broadcastDiagramChanged(diagramId, "XMI_IMPORTED");
      return umlService.getDiagram(projectId, diagramId, email);

    } catch (Exception e) {
      throw new BadRequestException("Error al parsear el archivo XMI: " + e.getMessage());
    }
  }

  // Exportación e Importación en Formato JSON Propio Completo (CU13 / Requerimiento 11)
  @Transactional(readOnly = true)
  public String exportJson(UUID projectId, UUID diagramId, String email) {
    projectService.requireAccess(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);
    List<ElementoUML> elements = elementRepo.findByDiagramaId(diagramId);
    List<RelacionUML> relations = relationRepo.findByDiagramaId(diagramId);

    try {
      ObjectNode root = objectMapper.createObjectNode();
      root.put("format", "CASE_UML_DIAGRAM_JSON");
      root.put("version", "2.0");
      root.put("exportedAt", Instant.now().toString());

      ObjectNode diagramNode = root.putObject("diagram");
      diagramNode.put("id", diagram.getId().toString());
      diagramNode.put("nombre", diagram.getNombre());
      diagramNode.put("descripcion", diagram.getDescripcion());

      ArrayNode elementsArray = root.putArray("elements");
      for (ElementoUML el : elements) {
        ObjectNode elNode = elementsArray.addObject();
        elNode.put("id", el.getId().toString());
        elNode.put("nombre", el.getNombre());
        elNode.put("tipoElemento", el.getTipoElemento().name());
        elNode.put("posicionX", el.getPosicionX());
        elNode.put("posicionY", el.getPosicionY());
        if (el.getPropiedadesJson() != null && !el.getPropiedadesJson().isBlank()) {
          elNode.set("properties", objectMapper.readTree(el.getPropiedadesJson()));
        }
      }

      ArrayNode relationsArray = root.putArray("relations");
      for (RelacionUML rel : relations) {
        if (rel.getElementoOrigen() == null || rel.getElementoDestino() == null) continue;
        ObjectNode relNode = relationsArray.addObject();
        relNode.put("id", rel.getId().toString());
        relNode.put("tipoRelacion", rel.getTipoRelacion().name());
        relNode.put("nombreRelacion", rel.getNombreRelacion());
        relNode.put("elementoOrigenNombre", rel.getElementoOrigen().getNombre());
        relNode.put("elementoDestinoNombre", rel.getElementoDestino().getNombre());
        relNode.put("multiplicidadOrigen", rel.getMultiplicidadOrigen());
        relNode.put("multiplicidadDestino", rel.getMultiplicidadDestino());
      }

      return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    } catch (Exception e) {
      throw new BadRequestException("Error al exportar diagrama a JSON: " + e.getMessage());
    }
  }

  public DiagramFullView importJson(UUID projectId, UUID diagramId, String email, String jsonContent) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);

    try {
      JsonNode root = objectMapper.readTree(jsonContent);
      JsonNode elementsNode = root.path("elements");
      JsonNode relationsNode = root.path("relations");

      if (!elementsNode.isArray()) {
        throw new BadRequestException("El JSON debe contener un arreglo 'elements'.");
      }

      Map<String, ElementoUML> createdMap = new HashMap<>();

      // 1. Guardar elementos
      for (JsonNode elNode : elementsNode) {
        String name = elNode.path("nombre").asText();
        String tipoStr = elNode.path("tipoElemento").asText("CLASE");
        double posX = elNode.path("posicionX").asDouble(150.0);
        double posY = elNode.path("posicionY").asDouble(120.0);
        JsonNode props = elNode.path("properties");
        String propsJson = props.isMissingNode() ? "{}" : props.toString();

        TipoElementoUML tipo = TipoElementoUML.valueOf(tipoStr);
        ElementoUML el = new ElementoUML(diagram, tipo, name, posX, posY, propsJson);
        el = elementRepo.save(el);
        createdMap.put(name.toLowerCase(), el);
      }

      // 2. Guardar relaciones
      if (relationsNode.isArray()) {
        for (JsonNode rNode : relationsNode) {
          String origName = rNode.path("elementoOrigenNombre").asText();
          String destName = rNode.path("elementoDestinoNombre").asText();
          String tipoStr = rNode.path("tipoRelacion").asText("ASOCIACION");
          String relName = rNode.path("nombreRelacion").isTextual() ? rNode.path("nombreRelacion").asText() : null;
          String multOrig = rNode.path("multiplicidadOrigen").isTextual() ? rNode.path("multiplicidadOrigen").asText() : null;
          String multDest = rNode.path("multiplicidadDestino").isTextual() ? rNode.path("multiplicidadDestino").asText() : null;

          ElementoUML orig = createdMap.get(origName.toLowerCase());
          ElementoUML dest = createdMap.get(destName.toLowerCase());

          if (orig != null && dest != null) {
            TipoRelacionUML tipoRel = TipoRelacionUML.valueOf(tipoStr);
            RelacionUML rel = new RelacionUML(diagram, orig, dest, relName, tipoRel, multOrig, multDest);
            relationRepo.save(rel);
          }
        }
      }

      wsHandler.broadcastDiagramChanged(diagramId, "JSON_IMPORTED");
      return umlService.getDiagram(projectId, diagramId, email);

    } catch (Exception e) {
      throw new BadRequestException("Error al importar diagrama desde JSON: " + e.getMessage());
    }
  }

  private String extractMultiplicityFromEnd(Element end) {
    if (end == null) return "";
    String attr = getAttr(end, "multiplicity", "Multiplicity");
    if (!attr.isBlank()) return attr.trim();

    String lower = "";
    String upper = "";

    List<Element> lowerNodes = findDescendantElementsByTagNames(end, "lowerValue", "lower");
    if (!lowerNodes.isEmpty()) {
      lower = getAttr(lowerNodes.get(0), "value", "Value");
    }

    List<Element> upperNodes = findDescendantElementsByTagNames(end, "upperValue", "upper");
    if (!upperNodes.isEmpty()) {
      upper = getAttr(upperNodes.get(0), "value", "Value");
    }

    // Enterprise Architect XMI 1.1: <UML:MultiplicityRange lower="0" upper="-1"/>
    if (lower.isBlank() && upper.isBlank()) {
      List<Element> ranges = findDescendantElementsByTagNames(end, "MultiplicityRange", "multiplicityRange");
      if (!ranges.isEmpty()) {
        lower = getAttr(ranges.get(0), "lower", "Lower");
        upper = getAttr(ranges.get(0), "upper", "Upper");
      }
    }

    if ("-1".equals(upper)) upper = "*";

    if (!lower.isBlank() && !upper.isBlank()) {
      if (lower.equals(upper)) return lower;
      return lower + ".." + upper;
    } else if (!upper.isBlank()) {
      return upper;
    } else if (!lower.isBlank()) {
      return lower;
    }
    return "";
  }

  private void collectAllElements(org.w3c.dom.Node node, List<Element> result) {
    if (node == null) return;
    if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
      result.add((Element) node);
    }
    NodeList children = node.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      collectAllElements(children.item(i), result);
    }
  }

  private String getLocalName(org.w3c.dom.Node node) {
    if (node == null) return "";
    String name = node.getNodeName();
    if (name == null) return "";
    int idx = name.indexOf(':');
    return (idx >= 0) ? name.substring(idx + 1) : name;
  }

  private String getAttr(Element el, String... candidateNames) {
    if (el == null) return "";
    for (String cand : candidateNames) {
      if (el.hasAttribute(cand)) {
        String val = el.getAttribute(cand);
        if (val != null && !val.isBlank()) return val.trim();
      }
    }
    org.w3c.dom.NamedNodeMap attrs = el.getAttributes();
    if (attrs != null) {
      for (int i = 0; i < attrs.getLength(); i++) {
        org.w3c.dom.Node attrNode = attrs.item(i);
        String attrNodeName = attrNode.getNodeName();
        String localAttr = attrNodeName.contains(":") ? attrNodeName.substring(attrNodeName.indexOf(":") + 1) : attrNodeName;
        for (String cand : candidateNames) {
          String cleanCand = cand.contains(":") ? cand.substring(cand.indexOf(":") + 1) : cand;
          if (localAttr.equalsIgnoreCase(cleanCand) || attrNodeName.equalsIgnoreCase(cand)) {
            String val = attrNode.getNodeValue();
            if (val != null && !val.isBlank()) return val.trim();
          }
        }
      }
    }
    return "";
  }

  private static final Set<String> IGNORED_TYPE_NAMES = Set.of(
      "earootclass", "ea_root_class", "earoot", "easystem", "eadiagram", "eamodel",
      "int", "integer", "long", "longint", "bigint", "int64", "int32", "int16", "int8",
      "short", "byte", "tinyint", "smallint",
      "uint", "uint8", "uint16", "uint32", "uint64", "ulong", "ushort", "ubyte",
      "float", "float4", "float8", "double", "decimal", "numeric", "number", "real", "money", "smallmoney",
      "char", "nchar", "varchar", "varchar2", "nvarchar", "nvarchar2", "text", "ntext", "string", "str",
      "character", "character varying",
      "bool", "boolean", "bit",
      "date", "datetime", "datetime2", "timestamp", "timestamptz", "time", "year", "smalldatetime",
      "void", "object", "any", "unknown", "null", "undefined",
      "blob", "clob", "binary", "varbinary", "image", "bytea", "raw",
      "uuid", "guid", "json", "jsonb", "xml"
  );

  public static boolean isPrimitiveOrSystemElement(String name) {
    if (name == null || name.isBlank()) return true;
    String clean = name.trim().toLowerCase();
    if (clean.equals("earootclass") || clean.startsWith("earoot") || clean.startsWith("ea_root")) {
      return true;
    }
    // Quitar prefijos comunes de EA o de tipos generados (EAJava_, EASQL_, dt_, uml_)
    if (clean.startsWith("eajava_")) clean = clean.substring(7);
    if (clean.startsWith("easql_")) clean = clean.substring(6);
    if (clean.startsWith("ea_")) clean = clean.substring(3);
    if (clean.startsWith("dt_")) clean = clean.substring(3);
    if (clean.startsWith("uml_")) clean = clean.substring(4);

    return IGNORED_TYPE_NAMES.contains(clean);
  }

  private boolean isClassOrTableElement(Element el) {
    if (el == null) return false;
    String local = getLocalName(el);
    String type = getAttr(el, "xmi:type", "type");
    String cleanType = type.contains(":") ? type.substring(type.indexOf(":") + 1) : type;

    // Descartar etiquetas que no son entidades de clase ni tabla
    if (local.equalsIgnoreCase("Package") || cleanType.equalsIgnoreCase("Package")
        || local.equalsIgnoreCase("Model") || cleanType.equalsIgnoreCase("Model")
        || local.equalsIgnoreCase("elements") || local.equalsIgnoreCase("connectors")
        || local.equalsIgnoreCase("diagrams") || local.equalsIgnoreCase("diagram")
        || local.equalsIgnoreCase("diagramElement") || local.equalsIgnoreCase("attributes")
        || local.equalsIgnoreCase("operations") || local.equalsIgnoreCase("properties")
        || local.equalsIgnoreCase("project") || local.equalsIgnoreCase("Documentation")
        || local.equalsIgnoreCase("XMI.header") || local.equalsIgnoreCase("XMI.content")
        || local.equalsIgnoreCase("DataType") || cleanType.equalsIgnoreCase("DataType")
        || local.equalsIgnoreCase("PrimitiveType") || cleanType.equalsIgnoreCase("PrimitiveType")
        || local.equalsIgnoreCase("Association") || cleanType.equalsIgnoreCase("Association")
        || local.equalsIgnoreCase("Generalization") || cleanType.equalsIgnoreCase("Generalization")
        || local.equalsIgnoreCase("Dependency") || cleanType.equalsIgnoreCase("Dependency")
        || local.equalsIgnoreCase("Attribute") || cleanType.equalsIgnoreCase("Property")
        || local.equalsIgnoreCase("Operation") || cleanType.equalsIgnoreCase("Parameter")
        || local.equalsIgnoreCase("Stereotype") || cleanType.equalsIgnoreCase("Tag")) {
      return false;
    }

    // Descartar estereotipos de tipos de datos primitivos
    if (hasStereotype(el, "datatype", "primitive", "type")) {
      return false;
    }

    // Descartar si atributos internos de EA señalan DataType o PrimitiveType
    String sType = getAttr(el, "sType", "s_type", "tType", "t_type");
    if (sType.equalsIgnoreCase("DataType") || sType.equalsIgnoreCase("PrimitiveType")) {
      return false;
    }
    List<Element> propNodes = findChildElementsByTagNames(el, "properties", "model");
    for (Element p : propNodes) {
      String psType = getAttr(p, "sType", "s_type", "tType", "t_type");
      if (psType.equalsIgnoreCase("DataType") || psType.equalsIgnoreCase("PrimitiveType")) {
        return false;
      }
    }

    // Descartar si el nombre es una clase raíz de EA o un tipo primitivo conocido
    String name = getAttr(el, "name", "Name");
    if (!name.isBlank() && isPrimitiveOrSystemElement(name)) {
      return false;
    }

    // Aceptación directa por nombre de etiqueta o tipo
    if (local.equalsIgnoreCase("Class") || local.equalsIgnoreCase("Table")
        || local.equalsIgnoreCase("Interface") || local.equalsIgnoreCase("Enumeration")) {
      return true;
    }

    if (cleanType.equalsIgnoreCase("Class") || cleanType.equalsIgnoreCase("Table")
        || cleanType.equalsIgnoreCase("Interface") || cleanType.equalsIgnoreCase("Enumeration")) {
      return true;
    }

    // Estereotipo de Enterprise Architect para tablas de base de datos
    return hasStereotype(el, "table", "entity", "class");
  }

  private boolean hasStereotype(Element el, String... candidateStereotypes) {
    String stAttr = getAttr(el, "stereotype", "Stereotype").toLowerCase();
    for (String cand : candidateStereotypes) {
      if (stAttr.contains(cand.toLowerCase())) return true;
    }
    List<Element> stereoNodes = findDescendantElementsByTagNames(el, "Stereotype", "stereotype");
    for (Element s : stereoNodes) {
      String sName = getAttr(s, "name", "Name").toLowerCase();
      for (String cand : candidateStereotypes) {
        if (sName.contains(cand.toLowerCase())) return true;
      }
    }
    return false;
  }

  private TipoElementoUML determineElementType(Element el) {
    String local = getLocalName(el);
    String type = getAttr(el, "xmi:type", "type");
    String isAbstract = getAttr(el, "isAbstract", "is_abstract");

    if (local.equalsIgnoreCase("Interface") || type.toLowerCase().endsWith("interface")) {
      return TipoElementoUML.INTERFAZ;
    }
    if (local.equalsIgnoreCase("Enumeration") || type.toLowerCase().endsWith("enumeration")) {
      return TipoElementoUML.ENUMERACION;
    }
    if ("true".equalsIgnoreCase(isAbstract)) {
      return TipoElementoUML.CLASE_ABSTRACTA;
    }
    return TipoElementoUML.CLASE;
  }

  private List<Element> findChildElementsByTagNames(Element parent, String... tagNames) {
    List<Element> result = new ArrayList<>();
    if (parent == null) return result;
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      org.w3c.dom.Node child = children.item(i);
      if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
        String local = getLocalName(child);
        for (String t : tagNames) {
          if (local.equalsIgnoreCase(t) || child.getNodeName().equalsIgnoreCase(t)) {
            result.add((Element) child);
            break;
          }
        }
      }
    }
    return result;
  }

  private List<Element> findDescendantElementsByTagNames(Element parent, String... tagNames) {
    List<Element> result = new ArrayList<>();
    if (parent == null) return result;
    List<Element> all = new ArrayList<>();
    collectAllElements(parent, all);
    // Ignorar el elemento padre
    for (int i = 1; i < all.size(); i++) {
      Element el = all.get(i);
      String local = getLocalName(el);
      for (String t : tagNames) {
        if (local.equalsIgnoreCase(t) || el.getNodeName().equalsIgnoreCase(t)) {
          result.add(el);
          break;
        }
      }
    }
    return result;
  }

  private String resolveAttributeType(Element attrEl, Map<String, String> idToNameMap) {
    // 1. Tipo en atributo directo
    String typeAttr = getAttr(attrEl, "type", "typeRef", "datatype");
    if (!typeAttr.isBlank()) {
      if (idToNameMap.containsKey(typeAttr)) {
        return cleanTypeName(idToNameMap.get(typeAttr));
      }
      return cleanTypeName(typeAttr);
    }

    // 2. Tipo en nodo hijo o propiedades de EA
    List<Element> typeNodes = findDescendantElementsByTagNames(attrEl,
        "type", "Classifier", "DataType", "PrimitiveType", "StructuralFeature.type", "properties", "property");
    for (Element tn : typeNodes) {
      String ref = getAttr(tn, "xmi:idref", "xmi.idref", "idref");
      if (!ref.isBlank() && idToNameMap.containsKey(ref)) {
        return cleanTypeName(idToNameMap.get(ref));
      }
      String typeVal = getAttr(tn, "type", "Type");
      if (!typeVal.isBlank()) {
        if (idToNameMap.containsKey(typeVal)) {
          return cleanTypeName(idToNameMap.get(typeVal));
        }
        return cleanTypeName(typeVal);
      }
      String href = getAttr(tn, "href", "Href");
      if (href.contains("#")) {
        return cleanTypeName(href.substring(href.indexOf("#") + 1));
      }
      String name = getAttr(tn, "name", "Name");
      if (!name.isBlank()) {
        return cleanTypeName(name);
      }
    }

    return "String";
  }

  private String cleanTypeName(String raw) {
    if (raw == null || raw.isBlank()) return "String";
    String s = raw.trim();
    if (s.startsWith("EAJava_")) s = s.substring(7);
    if (s.startsWith("EASQL_")) s = s.substring(6);
    if (s.startsWith("dt_")) s = s.substring(3);
    if (s.contains(":")) s = s.substring(s.lastIndexOf(":") + 1);
    if (s.equalsIgnoreCase("int") || s.equalsIgnoreCase("integer") || s.equalsIgnoreCase("int4") || s.equalsIgnoreCase("int32")) return "Integer";
    if (s.equalsIgnoreCase("long") || s.equalsIgnoreCase("bigint") || s.equalsIgnoreCase("int8") || s.equalsIgnoreCase("int64")) return "long";
    if (s.equalsIgnoreCase("varchar") || s.equalsIgnoreCase("char") || s.equalsIgnoreCase("text") || s.equalsIgnoreCase("string") || s.equalsIgnoreCase("nvarchar")) return "String";
    if (s.equalsIgnoreCase("bool") || s.equalsIgnoreCase("boolean") || s.equalsIgnoreCase("bit")) return "Boolean";
    if (s.equalsIgnoreCase("double") || s.equalsIgnoreCase("float") || s.equalsIgnoreCase("numeric") || s.equalsIgnoreCase("decimal") || s.equalsIgnoreCase("real")) return "Decimal";
    if (s.equalsIgnoreCase("date") || s.equalsIgnoreCase("datetime") || s.equalsIgnoreCase("timestamp") || s.equalsIgnoreCase("datetime2")) return "DateTime";
    return s;
  }

  private String sanitizeId(String name) {
    if (name == null || name.isBlank()) return "String";
    return name.replaceAll("[^a-zA-Z0-9_]", "_");
  }

  private double[] parseCoordinatesFromGeometry(String geom) {
    try {
      double left = -1;
      double top = -1;
      String[] parts = geom.split(";");
      for (String part : parts) {
        String[] kv = part.split("=");
        if (kv.length == 2) {
          String k = kv[0].trim().toLowerCase();
          String v = kv[1].trim();
          if ("left".equals(k) || "l".equals(k)) {
            left = Math.abs(Double.parseDouble(v));
          } else if ("top".equals(k) || "t".equals(k)) {
            top = Math.abs(Double.parseDouble(v));
          }
        }
      }
      if (left >= 0 && top >= 0) {
        return new double[]{left, top};
      }
    } catch (Exception ignored) {}
    return null;
  }

  private String resolveOperationReturnType(Element opEl, Map<String, String> idToNameMap) {
    List<Element> params = findDescendantElementsByTagNames(opEl, "Parameter", "ownedParameter", "parameter");
    for (Element p : params) {
      String kind = getAttr(p, "kind", "direction", "name").toLowerCase();
      if ("return".equals(kind)) {
        return resolveAttributeType(p, idToNameMap);
      }
    }
    return "void";
  }

  private String resolveOperationParameters(Element opEl, Map<String, String> idToNameMap) {
    List<Element> params = findDescendantElementsByTagNames(opEl, "Parameter", "ownedParameter", "parameter");
    List<String> list = new ArrayList<>();
    for (Element p : params) {
      String kind = getAttr(p, "kind", "direction", "name").toLowerCase();
      if ("return".equals(kind)) continue;
      String pName = getAttr(p, "name", "Name");
      if (pName.isBlank()) continue;
      String pType = resolveAttributeType(p, idToNameMap);
      list.add(pName + ": " + pType);
    }
    return String.join(", ", list);
  }

  private ElementoUML resolveElement(String idOrName, Map<String, ElementoUML> idMap, Map<String, ElementoUML> nameMap) {
    if (idOrName == null || idOrName.isBlank()) return null;
    ElementoUML el = idMap.get(idOrName.trim());
    if (el != null) return el;
    return nameMap.get(idOrName.trim().toLowerCase());
  }

  private String[] parseMultiplicityBounds(String mult) {
    if (mult == null || mult.isBlank()) return new String[]{"1", "1"};
    String s = mult.trim();
    if (s.contains("..")) {
      String[] parts = s.split("\\.\\.");
      return new String[]{parts[0].trim(), parts[1].trim()};
    }
    if (s.equals("*")) return new String[]{"0", "*"};
    return new String[]{s, s};
  }

  private DiagramaClase validateDiagram(UUID projectId, UUID diagramId) {
    DiagramaClase diagram = diagramRepo.findById(diagramId)
        .orElseThrow(() -> new NotFoundException("Diagrama no encontrado."));
    if (!diagram.getProject().getId().equals(projectId)) {
      throw new NotFoundException("El diagrama no pertenece a este proyecto.");
    }
    return diagram;
  }

  private String escapeXml(String input) {
    if (input == null) return "";
    return input.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
