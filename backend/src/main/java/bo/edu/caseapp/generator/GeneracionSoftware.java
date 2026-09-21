package bo.edu.caseapp.generator;

import bo.edu.caseapp.uml.DiagramaClase;
import bo.edu.caseapp.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "generacion_software")
public class GeneracionSoftware {
  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "diagrama_id")
  private DiagramaClase diagrama;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id")
  private User usuario;

  @Column(name = "tipo_generacion", nullable = false, length = 30)
  private String tipoGeneracion;

  @Column(name = "configuracion_json", columnDefinition = "TEXT")
  private String configuracionJson;

  @Column(nullable = false, length = 30)
  private String estado = "COMPLETADO";

  @Column(nullable = false)
  private Instant fecha = Instant.now();

  protected GeneracionSoftware() {}

  public GeneracionSoftware(DiagramaClase diagrama, User usuario, String tipoGeneracion, String configuracionJson) {
    this.diagrama = diagrama;
    this.usuario = usuario;
    this.tipoGeneracion = tipoGeneracion;
    this.configuracionJson = configuracionJson;
  }

  public UUID getId() { return id; }
  public DiagramaClase getDiagrama() { return diagrama; }
  public User getUsuario() { return usuario; }
  public String getTipoGeneracion() { return tipoGeneracion; }
  public String getConfiguracionJson() { return configuracionJson; }
  public String getEstado() { return estado; }
  public Instant getFecha() { return fecha; }
}
