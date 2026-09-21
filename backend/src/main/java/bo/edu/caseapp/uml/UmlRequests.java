package bo.edu.caseapp.uml;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public final class UmlRequests {
  private UmlRequests() { }

  public record CreateDiagram(
      @NotBlank @Size(max = 120) String nombre,
      @Size(max = 500) String descripcion
  ) { }

  public record UpdateDiagram(
      @NotBlank @Size(max = 120) String nombre,
      @Size(max = 500) String descripcion
  ) { }

  public record SaveElement(
      UUID id,
      @NotNull TipoElementoUML tipoElemento,
      @NotBlank @Size(max = 120) String nombre,
      @NotNull Double posicionX,
      @NotNull Double posicionY,
      String propiedadesJson
  ) { }

  public record SaveRelation(
      UUID id,
      @NotNull UUID elementoOrigen,
      @NotNull UUID elementoDestino,
      @Size(max = 120) String nombreRelacion,
      @NotNull TipoRelacionUML tipoRelacion,
      @Size(max = 20) String multiplicidadOrigen,
      @Size(max = 20) String multiplicidadDestino
  ) { }

  public record PositionUpdate(
      @NotNull UUID id,
      @NotNull Double posicionX,
      @NotNull Double posicionY
  ) { }

  public record SyncPositions(
      @NotNull List<PositionUpdate> positions
  ) { }
}
