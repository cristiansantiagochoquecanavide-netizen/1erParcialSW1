package bo.edu.caseapp.uml.relational;

import java.util.List;

public record RelationalSchema(
    List<RelationalTable> tables,
    String sqlDdl
) { }
