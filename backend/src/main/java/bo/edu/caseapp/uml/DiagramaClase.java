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

  public void update(String nombre, String descripcion) {
    this.nombre = nombre;
    this.descripcion = descripcion;
  }

  public void setEstado(String estado) {
    this.estado = estado;
  }
}
