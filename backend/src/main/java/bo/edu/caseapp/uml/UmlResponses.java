package bo.edu.caseapp.uml;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class UmlResponses {
  private UmlResponses() { }

  public record DiagramSummary(
      UUID id,
      UUID projectId,
      String nombre,
      String descripcion,
      String estado,
      Instant fechaCreacion
  ) { }

  public record ElementDetail(
      UUID id,
      UUID diagramaId,
      TipoElementoUML tipoElemento,
      String nombre,
      double posicionX,
      double posicionY,
      String propiedadesJson
  ) { }

  public record RelationDetail(
      UUID id,
      UUID diagramaId,
      UUID elementoOrigen,
      UUID elementoDestino,
      String nombreRelacion,
      TipoRelacionUML tipoRelacion,
      String multiplicidadOrigen,
      String multiplicidadDestino
  ) { }

  public record DiagramFullView(
      DiagramSummary diagram,
      List<ElementDetail> elements,
      List<RelationDetail> relations
  ) { }
}
