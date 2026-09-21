package bo.edu.caseapp.uml.relational;

public record RelationalColumn(
    String name,
    String sqlType,
    boolean primaryKey,
    boolean nullable,
    boolean unique,
    String defaultValue
) { }
