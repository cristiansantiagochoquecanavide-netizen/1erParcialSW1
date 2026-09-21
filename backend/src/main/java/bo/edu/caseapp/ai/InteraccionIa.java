package bo.edu.caseapp.ai;

import bo.edu.caseapp.uml.DiagramaClase;
import bo.edu.caseapp.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "interaccion_ia")
public class InteraccionIa {
  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "diagrama_id")
  private DiagramaClase diagrama;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id")
  private User usuario;

  @Column(name = "tipo_interaccion", nullable = false, length = 50)
  private String tipoInteraccion;

  @Column(name = "entrada_usuario", nullable = false, columnDefinition = "TEXT")
  private String entradaUsuario;

  @Column(name = "respuesta_agente", nullable = false, columnDefinition = "TEXT")
  private String respuestaAgente;

  @Column(name = "accion_json", columnDefinition = "TEXT")
  private String accionJson;

  @Column(nullable = false)
  private Instant fecha = Instant.now();

  protected InteraccionIa() {}

  public InteraccionIa(DiagramaClase diagrama, User usuario, String tipoInteraccion, String entradaUsuario, String respuestaAgente, String accionJson) {
    this.diagrama = diagrama;
    this.usuario = usuario;
    this.tipoInteraccion = tipoInteraccion;
    this.entradaUsuario = entradaUsuario;
    this.respuestaAgente = respuestaAgente;
    this.accionJson = accionJson;
  }

  public UUID getId() { return id; }
  public DiagramaClase getDiagrama() { return diagrama; }
  public User getUsuario() { return usuario; }
  public String getTipoInteraccion() { return tipoInteraccion; }
  public String getEntradaUsuario() { return entradaUsuario; }
  public String getRespuestaAgente() { return respuestaAgente; }
  public String getAccionJson() { return accionJson; }
  public Instant getFecha() { return fecha; }
}
