package bo.edu.caseapp.uml;

import bo.edu.caseapp.project.Project;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "diagrama_clase")
public class DiagramaClase {
  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @Column(nullable = false, length = 120)
  private String nombre;

  @Column(length = 500)
  private String descripcion;

  @Column(nullable = false, length = 20)
  private String estado = "ACTIVE";

  @Column(name = "fecha_creacion", nullable = false)
  private Instant fechaCreacion = Instant.now();

  @Column(name = "bloqueado", nullable = false)
  private boolean bloqueado = false;

  @Column(name = "bloqueado_por", length = 255)
  private String bloqueadoPor;

  @Column(name = "fecha_bloqueo")
  private Instant fechaBloqueo;

  protected DiagramaClase() { }

  public DiagramaClase(Project project, String nombre, String descripcion) {
    this.project = project;
    this.nombre = nombre;
    this.descripcion = descripcion;
  }

  public UUID getId() { return id; }
  public Project getProject() { return project; }
  public String getNombre() { return nombre; }
  public String getDescripcion() { return descripcion; }
  public String getEstado() { return estado; }
  public Instant getFechaCreacion() { return fechaCreacion; }

  public boolean isBloqueado() {
    if (!bloqueado) return false;
    // Timeout automático de seguridad a los 2 minutos (120 seg) por si falla la sesión
    if (fechaBloqueo != null && fechaBloqueo.isBefore(Instant.now().minusSeconds(120))) {
      this.bloqueado = false;
      this.bloqueadoPor = null;
      this.fechaBloqueo = null;
      return false;
    }
    return true;
  }

  public String getBloqueadoPor() { return bloqueadoPor; }
  public Instant getFechaBloqueo() { return fechaBloqueo; }

  public void update(String nombre, String descripcion) {
    this.nombre = nombre;
    this.descripcion = descripcion;
  }

  public void setEstado(String estado) {
    this.estado = estado;
  }

  public void bloquear(String email) {
    this.bloqueado = true;
    this.bloqueadoPor = email;
    this.fechaBloqueo = Instant.now();
  }

  public void desbloquear() {
    this.bloqueado = false;
    this.bloqueadoPor = null;
    this.fechaBloqueo = null;
  }
}
