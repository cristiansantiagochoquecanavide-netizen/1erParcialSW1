package bo.edu.caseapp.uml.relational;

public record RelationalForeignKey(
    String constraintName,
    String columnName,
    String targetTable,
    String targetColumn,
    boolean cascadeDelete
) { }
