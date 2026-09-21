package bo.edu.caseapp.uml.relational;

import java.util.List;

public record RelationalTable(
    String name,
    String umlSourceClass,
    List<RelationalColumn> columns,
    List<RelationalForeignKey> foreignKeys
) { }
