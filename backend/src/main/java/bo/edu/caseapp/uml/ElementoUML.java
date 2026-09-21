package bo.edu.caseapp.uml;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "elemento_uml")
public class ElementoUML {
  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "diagrama_id", nullable = false)
  private DiagramaClase diagrama;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo_elemento", nullable = false, length = 30)
  private TipoElementoUML tipoElemento;

  @Column(nullable = false, length = 120)
  private String nombre;

  @Column(name = "posicion_x", nullable = false)
  private double posicionX;

  @Column(name = "posicion_y", nullable = false)
  private double posicionY;

  @Column(name = "propiedades_json", nullable = false, columnDefinition = "TEXT")
  private String propiedadesJson = "{}";

  protected ElementoUML() { }

  public ElementoUML(DiagramaClase diagrama, TipoElementoUML tipoElemento, String nombre, double posicionX, double posicionY, String propiedadesJson) {
    this.diagrama = diagrama;
    this.tipoElemento = tipoElemento;
    this.nombre = nombre;
    this.posicionX = posicionX;
    this.posicionY = posicionY;
    this.propiedadesJson = propiedadesJson != null && !propiedadesJson.isBlank() ? propiedadesJson : "{}";
  }

  public UUID getId() { return id; }
  public DiagramaClase getDiagrama() { return diagrama; }
  public TipoElementoUML getTipoElemento() { return tipoElemento; }
  public String getNombre() { return nombre; }
  public double getPosicionX() { return posicionX; }
  public double getPosicionY() { return posicionY; }
  public String getPropiedadesJson() { return propiedadesJson; }

  public void update(TipoElementoUML tipoElemento, String nombre, double posicionX, double posicionY, String propiedadesJson) {
    this.tipoElemento = tipoElemento;
    this.nombre = nombre;
    this.posicionX = posicionX;
    this.posicionY = posicionY;
    if (propiedadesJson != null && !propiedadesJson.isBlank()) {
      this.propiedadesJson = propiedadesJson;
    }
  }

  public void updatePosition(double posicionX, double posicionY) {
    this.posicionX = posicionX;
    this.posicionY = posicionY;
  }
}
