package bo.edu.caseapp.uml.relational;

import bo.edu.caseapp.uml.ElementoUML;
import bo.edu.caseapp.uml.RelacionUML;
import bo.edu.caseapp.uml.TipoElementoUML;
import bo.edu.caseapp.uml.TipoRelacionUML;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RelationalModelService {
  private final ObjectMapper objectMapper = new ObjectMapper();

  public RelationalSchema generateSchema(List<ElementoUML> elements, List<RelacionUML> relations) {
    Map<UUID, ElementoUML> elementMap = new HashMap<>();
    Map<UUID, RelationalTableBuilder> tableBuilders = new LinkedHashMap<>();

    // 1. Filtrar clases persistentes y crear tablas iniciales
    for (ElementoUML el : elements) {
      if (el.getTipoElemento() == TipoElementoUML.INTERFAZ) {
        continue; // Las interfaces puras no generan tablas físicas directas
      }

      elementMap.put(el.getId(), el);
      String tableName = toSnakeCase(el.getNombre());
      RelationalTableBuilder builder = new RelationalTableBuilder(tableName, el.getNombre());

      // Parsear atributos
      List<RelationalColumn> parsedCols = parseAttributes(el.getPropiedadesJson());
      boolean hasPk = parsedCols.stream().anyMatch(RelationalColumn::primaryKey);

      if (!hasPk) {
        // Autogenerar clave primaria id UUID
        builder.addColumn(new RelationalColumn("id", "UUID", true, false, true, "gen_random_uuid()"));
      }

      for (RelationalColumn col : parsedCols) {
        builder.addColumn(col);
      }

      tableBuilders.put(el.getId(), builder);
    }

    // 2. Procesar relaciones para derivar Claves Foráneas y Tablas Intermedias
    List<RelationalTable> junctionTables = new ArrayList<>();

    for (RelacionUML rel : relations) {
      ElementoUML origen = rel.getElementoOrigen();
      ElementoUML destino = rel.getElementoDestino();

      if (origen == null || destino == null) continue;
      RelationalTableBuilder origenBuilder = tableBuilders.get(origen.getId());
      RelationalTableBuilder destinoBuilder = tableBuilders.get(destino.getId());

      if (origenBuilder == null || destinoBuilder == null) continue;

      boolean isComposition = rel.getTipoRelacion() == TipoRelacionUML.COMPOSICION;

      if (rel.getTipoRelacion() == TipoRelacionUML.HERENCIA) {
        // Herencia: tabla hija tiene id que referencia a tabla padre
        String fkName = "fk_" + origenBuilder.name + "_" + destinoBuilder.name;
        origenBuilder.addForeignKey(new RelationalForeignKey(
            fkName,
            "id",
            destinoBuilder.name,
            "id",
            true
        ));
      } else if (rel.getTipoRelacion() == TipoRelacionUML.ASOCIACION ||
                 rel.getTipoRelacion() == TipoRelacionUML.AGREGACION ||
                 rel.getTipoRelacion() == TipoRelacionUML.COMPOSICION) {

        boolean multOrigenMany = isMultiple(rel.getMultiplicidadOrigen());
        boolean multDestinoMany = isMultiple(rel.getMultiplicidadDestino());

        if (multOrigenMany && multDestinoMany) {
          // Relación N:M -> Tabla de unión intermedia
          String junctionName = origenBuilder.name + "_" + destinoBuilder.name;
          String fk1Col = origenBuilder.name + "_id";
          String fk2Col = destinoBuilder.name + "_id";

          List<RelationalColumn> jCols = List.of(
              new RelationalColumn(fk1Col, "UUID", true, false, false, null),
              new RelationalColumn(fk2Col, "UUID", true, false, false, null)
          );

          List<RelationalForeignKey> jFks = List.of(
              new RelationalForeignKey("fk_" + junctionName + "_" + origenBuilder.name, fk1Col, origenBuilder.name, "id", true),
              new RelationalForeignKey("fk_" + junctionName + "_" + destinoBuilder.name, fk2Col, destinoBuilder.name, "id", true)
          );

          junctionTables.add(new RelationalTable(junctionName, origen.getNombre() + " - " + destino.getNombre(), jCols, jFks));

        } else if (multDestinoMany) {
          // 1:N -> FK en destino referenciando origen
          String colName = origenBuilder.name + "_id";
          String fkName = "fk_" + destinoBuilder.name + "_" + origenBuilder.name;

          destinoBuilder.addColumn(new RelationalColumn(colName, "UUID", false, false, false, null));
          destinoBuilder.addForeignKey(new RelationalForeignKey(colName, colName, origenBuilder.name, "id", isComposition));

        } else {
          // N:1 o 1:1 -> FK en origen referenciando destino
          String colName = destinoBuilder.name + "_id";
          boolean isOneToOne = !multOrigenMany && !multDestinoMany;

          origenBuilder.addColumn(new RelationalColumn(colName, "UUID", false, false, isOneToOne, null));
          origenBuilder.addForeignKey(new RelationalForeignKey(colName, colName, destinoBuilder.name, "id", isComposition));
        }
      }
    }

    // Consolidar tablas
    List<RelationalTable> finalTables = new ArrayList<>();
    for (RelationalTableBuilder b : tableBuilders.values()) {
      finalTables.add(b.build());
    }
    finalTables.addAll(junctionTables);

    // 3. Generar Script SQL DDL
    String ddl = buildDdlSql(finalTables);

    return new RelationalSchema(finalTables, ddl);
  }

  private List<RelationalColumn> parseAttributes(String propiedadesJson) {
    List<RelationalColumn> list = new ArrayList<>();
    if (propiedadesJson == null || propiedadesJson.isBlank()) return list;

    try {
      JsonNode root = objectMapper.readTree(propiedadesJson);
      JsonNode attrs = root.path("attributes");
      if (attrs.isArray()) {
        for (JsonNode a : attrs) {
          String rawName = a.path("name").asText("").trim();
          String rawType = a.path("type").asText("").trim();
          if (rawName.isBlank()) continue;

          String colName = toSnakeCase(rawName);
          String sqlType = mapToSqlType(rawType);
          boolean isPk = colName.equals("id") || colName.endsWith("_id_pk");
          boolean isUnique = colName.equals("email") || colName.equals("correo") || colName.equals("codigo");

          list.add(new RelationalColumn(colName, sqlType, isPk, false, isUnique, null));
        }
      }
    } catch (Exception ignored) { }
    return list;
  }

  private boolean isMultiple(String multiplicity) {
    if (multiplicity == null || multiplicity.isBlank()) return false;
    String clean = multiplicity.trim();
    return clean.equals("*") || clean.equals("0..*") || clean.equals("1..*") || clean.endsWith("..*");
  }

  private String mapToSqlType(String umlType) {
    if (umlType == null || umlType.isBlank()) return "VARCHAR(255)";
    String upper = umlType.trim().toUpperCase(Locale.ROOT);

    if (upper.equals("UUID")) return "UUID";
    if (upper.equals("STRING") || upper.equals("TEXT") || upper.equals("TEXTO")) return "VARCHAR(255)";
    if (upper.equals("INT") || upper.equals("INTEGER")) return "INTEGER";
    if (upper.equals("LONG")) return "BIGINT";
    if (upper.equals("DOUBLE") || upper.equals("FLOAT") || upper.equals("DECIMAL") || upper.equals("BIGDECIMAL")) return "NUMERIC(12,2)";
    if (upper.equals("BOOLEAN") || upper.equals("BOOL")) return "BOOLEAN";
    if (upper.equals("DATE") || upper.equals("LOCALDATE")) return "DATE";
    if (upper.equals("INSTANT") || upper.equals("TIMESTAMP") || upper.equals("DATETIME")) return "TIMESTAMPTZ";

    return "VARCHAR(255)";
  }

  public String toSnakeCase(String str) {
    if (str == null) return "";
    String clean = str.trim();
    return clean.replaceAll("([a-z])([A-Z]+)", "$1_$2")
                .replaceAll("[^A-Za-z0-9_]", "_")
                .toLowerCase(Locale.ROOT);
  }

  private String buildDdlSql(List<RelationalTable> tables) {
    StringBuilder sb = new StringBuilder();
    sb.append("-- ==========================================================\n");
    sb.append("-- Script DDL PostgreSQL generado automáticamente a partir del Modelo UML\n");
    sb.append("-- Herramienta CASE Colaborativa con IA\n");
    sb.append("-- ==========================================================\n\n");

    for (RelationalTable table : tables) {
      sb.append("CREATE TABLE ").append(table.name()).append(" (\n");

      List<String> defs = new ArrayList<>();
      List<String> pkCols = new ArrayList<>();

      for (RelationalColumn col : table.columns()) {
        StringBuilder colDef = new StringBuilder("  ").append(col.name()).append(" ").append(col.sqlType());
        if (!col.nullable()) colDef.append(" NOT NULL");
        if (col.unique() && !col.primaryKey()) colDef.append(" UNIQUE");
        if (col.defaultValue() != null) colDef.append(" DEFAULT ").append(col.defaultValue());
        defs.add(colDef.toString());

        if (col.primaryKey()) {
          pkCols.add(col.name());
        }
      }

      if (!pkCols.isEmpty()) {
        defs.add("  PRIMARY KEY (" + String.join(", ", pkCols) + ")");
      }

      for (RelationalForeignKey fk : table.foreignKeys()) {
        StringBuilder fkDef = new StringBuilder("  CONSTRAINT ").append(fk.constraintName())
            .append(" FOREIGN KEY (").append(fk.columnName()).append(")")
            .append(" REFERENCES ").append(fk.targetTable()).append("(").append(fk.targetColumn()).append(")");
        if (fk.cascadeDelete()) {
          fkDef.append(" ON DELETE CASCADE");
        }
        defs.add(fkDef.toString());
      }

      sb.append(String.join(",\n", defs));
      sb.append("\n);\n\n");
    }

    return sb.toString();
  }

  private static class RelationalTableBuilder {
    final String name;
    final String umlSourceClass;
    final List<RelationalColumn> columns = new ArrayList<>();
    final List<RelationalForeignKey> foreignKeys = new ArrayList<>();
    final Set<String> columnNames = new HashSet<>();

    RelationalTableBuilder(String name, String umlSourceClass) {
      this.name = name;
      this.umlSourceClass = umlSourceClass;
    }

    void addColumn(RelationalColumn col) {
      if (!columnNames.contains(col.name())) {
        columnNames.add(col.name());
        columns.add(col);
      }
    }

    void addForeignKey(RelationalForeignKey fk) {
      foreignKeys.add(fk);
    }

    RelationalTable build() {
      return new RelationalTable(name, umlSourceClass, new ArrayList<>(columns), new ArrayList<>(foreignKeys));
    }
  }
}
