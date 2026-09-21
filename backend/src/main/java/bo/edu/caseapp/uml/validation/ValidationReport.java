package bo.edu.caseapp.uml.validation;

import java.util.List;

public record ValidationReport(
    boolean valid,
    int errorCount,
    int warningCount,
    List<ValidationIssue> issues
) { }
