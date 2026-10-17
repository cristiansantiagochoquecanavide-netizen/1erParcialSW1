package bo.edu.caseapp.uml;

import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;
import java.util.UUID;

@Entity
@Table(name = "relacion_uml")
public class RelacionUML implements Persistable<UUID> {
  @Id
  private UUID id;

  @Transient
  private boolean newEntity = true;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "diagrama_id", nullable = false)
  private DiagramaClase diagrama;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "elemento_origen", nullable = false)
  private ElementoUML elementoOrigen;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "elemento_destino", nullable = false)
  private ElementoUML elementoDestino;

  @Column(name = "nombre_relacion", length = 120)
  private String nombreRelacion;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_relacion", nullable = false, length = 30)
  private TipoRelacionUML tipoRelacion;

  @Column(name = "multiplicidad_origen", length = 20)
  private String multiplicidadOrigen;

  @Column(name = "multiplicidad_destino", length = 20)
  private String multiplicidadDestino;

  protected RelacionUML() { }

  public RelacionUML(DiagramaClase diagrama, ElementoUML elementoOrigen, ElementoUML elementoDestino, String nombreRelacion, TipoRelacionUML tipoRelacion, String multiplicidadOrigen, String multiplicidadDestino) {
    this.id = UUID.randomUUID();
    this.diagrama = diagrama;
    this.elementoOrigen = elementoOrigen;
    this.elementoDestino = elementoDestino;
    this.nombreRelacion = nombreRelacion;
    this.tipoRelacion = tipoRelacion;
    this.multiplicidadOrigen = multiplicidadOrigen;
    this.multiplicidadDestino = multiplicidadDestino;
  }

  public RelacionUML(UUID id, DiagramaClase diagrama, ElementoUML elementoOrigen, ElementoUML elementoDestino, String nombreRelacion, TipoRelacionUML tipoRelacion, String multiplicidadOrigen, String multiplicidadDestino) {
    this(diagrama, elementoOrigen, elementoDestino, nombreRelacion, tipoRelacion, multiplicidadOrigen, multiplicidadDestino);
    this.id = id;
  }

  @Override public UUID getId() { return id; }
  @Override @Transient public boolean isNew() { return newEntity; }
  @PostLoad @PostPersist void markNotNew() { this.newEntity = false; }
  public DiagramaClase getDiagrama() { return diagrama; }
  public ElementoUML getElementoOrigen() { return elementoOrigen; }
  public ElementoUML getElementoDestino() { return elementoDestino; }
  public String getNombreRelacion() { return nombreRelacion; }
  public TipoRelacionUML getTipoRelacion() { return tipoRelacion; }
  public String getMultiplicidadOrigen() { return multiplicidadOrigen; }
  public String getMultiplicidadDestino() { return multiplicidadDestino; }

  public void update(String nombreRelacion, TipoRelacionUML tipoRelacion, String multiplicidadOrigen, String multiplicidadDestino) {
    this.nombreRelacion = nombreRelacion;
    this.tipoRelacion = tipoRelacion;
    this.multiplicidadOrigen = multiplicidadOrigen;
    this.multiplicidadDestino = multiplicidadDestino;
  }

  public void update(ElementoUML elementoOrigen, ElementoUML elementoDestino, String nombreRelacion,
      TipoRelacionUML tipoRelacion, String multiplicidadOrigen, String multiplicidadDestino) {
    this.elementoOrigen = elementoOrigen;
    this.elementoDestino = elementoDestino;
    update(nombreRelacion, tipoRelacion, multiplicidadOrigen, multiplicidadDestino);
  }
}
