package bo.edu.caseapp.uml.validation;

import java.util.UUID;

public record ValidationIssue(
    String severity, // "ERROR", "WARNING", "INFO"
    UUID elementoId,
    String elementoNombre,
    String mensaje,
    String sugerencia
) { }
