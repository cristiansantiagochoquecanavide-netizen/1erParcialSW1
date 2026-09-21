package bo.edu.caseapp.interop;

import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.error.NotFoundException;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.*;
import bo.edu.caseapp.uml.UmlResponses.DiagramFullView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringReader;
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
  private final ObjectMapper objectMapper = new ObjectMapper();

  public XmiService(
      DiagramaClaseRepository diagramRepo,
      ElementoUMLRepository elementRepo,
      RelacionUMLRepository relationRepo,
      ProjectService projectService,
      UmlService umlService
  ) {
    this.diagramRepo = diagramRepo;
    this.elementRepo = elementRepo;
    this.relationRepo = relationRepo;
    this.projectService = projectService;
    this.umlService = umlService;
  }

  @Transactional(readOnly = true)
  public String exportXmi(UUID projectId, UUID diagramId, String email) {
    projectService.requireAccess(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);
    List<ElementoUML> elements = elementRepo.findByDiagramaId(diagramId);
    List<RelacionUML> relations = relationRepo.findByDiagramaId(diagramId);

    StringBuilder sb = new StringBuilder();
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<xmi:XMI xmi:version=\"2.1\" xmlns:uml=\"http://schema.omg.org/spec/UML/2.1\" xmlns:xmi=\"http://schema.omg.org/spec/XMI/2.1\">\n");
    sb.append("  <xmi:Documentation exporter=\"Herramienta CASE Colaborativa\" exporterVersion=\"1.0\"/>\n");
    sb.append("  <uml:Model xmi:type=\"uml:Model\" xmi:id=\"model_").append(diagram.getId()).append("\" name=\"").append(escapeXml(diagram.getNombre())).append("\">\n");

    // Clases y elementos
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

      // Atributos desde propiedades_json
      try {
        JsonNode root = objectMapper.readTree(el.getPropiedadesJson());
        JsonNode attrs = root.path("attributes");
        if (attrs.isArray()) {
          for (JsonNode a : attrs) {
            String attrId = a.path("id").asText(UUID.randomUUID().toString());
            String name = a.path("name").asText();
            String type = a.path("type").asText("String");
            String vis = a.path("visibility").asText("+");
            String visibility = switch (vis) {
              case "-" -> "private";
              case "#" -> "protected";
              case "~" -> "package";
              default -> "public";
            };

            sb.append("      <ownedAttribute xmi:type=\"uml:Property\" xmi:id=\"attr_").append(attrId)
              .append("\" name=\"").append(escapeXml(name))
              .append("\" visibility=\"").append(visibility)
              .append("\">\n");
            sb.append("        <type xmi:type=\"uml:PrimitiveType\" href=\"http://schema.omg.org/spec/UML/2.1#").append(escapeXml(type)).append("\"/>\n");
            sb.append("      </ownedAttribute>\n");
          }
        }
      } catch (Exception ignored) {}

      sb.append("    </packagedElement>\n");
    }

    // Relaciones
    for (RelacionUML rel : relations) {
      if (rel.getElementoOrigen() == null || rel.getElementoDestino() == null) continue;
      String origId = "el_" + rel.getElementoOrigen().getId();
      String destId = "el_" + rel.getElementoDestino().getId();

      if (rel.getTipoRelacion() == TipoRelacionUML.HERENCIA) {
        sb.append("    <packagedElement xmi:type=\"uml:Generalization\" xmi:id=\"rel_").append(rel.getId()).append("\" specific=\"").append(origId).append("\" general=\"").append(destId).append("\"/>\n");
      } else {
        String assocType = rel.getTipoRelacion() == TipoRelacionUML.COMPOSICION ? "composite" : rel.getTipoRelacion() == TipoRelacionUML.AGREGACION ? "shared" : "none";
        sb.append("    <packagedElement xmi:type=\"uml:Association\" xmi:id=\"rel_").append(rel.getId()).append("\" name=\"").append(escapeXml(rel.getNombreRelacion() != null ? rel.getNombreRelacion() : "")).append("\">\n");
        sb.append("      <memberEnd xmi:idref=\"end_orig_").append(rel.getId()).append("\"/>\n");
        sb.append("      <memberEnd xmi:idref=\"end_dest_").append(rel.getId()).append("\"/>\n");
        sb.append("      <ownedEnd xmi:type=\"uml:Property\" xmi:id=\"end_orig_").append(rel.getId()).append("\" type=\"").append(origId).append("\" aggregation=\"").append(assocType).append("\"/>\n");
        sb.append("      <ownedEnd xmi:type=\"uml:Property\" xmi:id=\"end_dest_").append(rel.getId()).append("\" type=\"").append(destId).append("\"/>\n");
        sb.append("    </packagedElement>\n");
      }
    }

    sb.append("  </uml:Model>\n");
    sb.append("</xmi:XMI>\n");

    return sb.toString();
  }

  public DiagramFullView importXmi(UUID projectId, UUID diagramId, String email, String xmlContent) {
    projectService.requireCanEdit(projectId, email);
    DiagramaClase diagram = validateDiagram(projectId, diagramId);

    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document doc = builder.parse(new InputSource(new StringReader(xmlContent)));

      NodeList elements = doc.getElementsByTagName("packagedElement");
      double curX = 120.0;
      double curY = 100.0;

      for (int i = 0; i < elements.getLength(); i++) {
        Element el = (Element) elements.item(i);
        String type = el.getAttribute("xmi:type");
        String name = el.getAttribute("name");

        if (name == null || name.isBlank()) continue;

        TipoElementoUML tipo = TipoElementoUML.CLASE;
        if ("uml:Interface".equalsIgnoreCase(type)) {
          tipo = TipoElementoUML.INTERFAZ;
        } else if ("uml:Enumeration".equalsIgnoreCase(type)) {
          tipo = TipoElementoUML.ENUMERACION;
        } else if ("true".equalsIgnoreCase(el.getAttribute("isAbstract"))) {
          tipo = TipoElementoUML.CLASE_ABSTRACTA;
        }

        // Parsear atributos si existen
        ObjectNode propNode = objectMapper.createObjectNode();
        ArrayNode attrs = propNode.putArray("attributes");
        attrs.addObject().put("id", UUID.randomUUID().toString()).put("visibility", "+").put("name", "id").put("type", "UUID");

        NodeList ownedAttrs = el.getElementsByTagName("ownedAttribute");
        for (int j = 0; j < ownedAttrs.getLength(); j++) {
          Element a = (Element) ownedAttrs.item(j);
          String aName = a.getAttribute("name");
          if (aName != null && !aName.isBlank() && !aName.equalsIgnoreCase("id")) {
            attrs.addObject().put("id", UUID.randomUUID().toString()).put("visibility", "+").put("name", aName).put("type", "String");
          }
        }
        propNode.putArray("methods");

        ElementoUML nuevo = new ElementoUML(diagram, tipo, name.trim(), curX, curY, propNode.toString());
        elementRepo.save(nuevo);

        curX += 240.0;
        if (curX > 800) {
          curX = 120.0;
          curY += 200.0;
        }
      }

      return umlService.getDiagram(projectId, diagramId, email);

    } catch (Exception e) {
      throw new BadRequestException("Error al parsear el archivo XMI: " + e.getMessage());
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

  private String escapeXml(String input) {
    if (input == null) return "";
    return input.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
