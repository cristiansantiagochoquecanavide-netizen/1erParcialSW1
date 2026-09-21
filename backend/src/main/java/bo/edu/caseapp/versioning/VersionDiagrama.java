package bo.edu.caseapp.versioning;

import bo.edu.caseapp.uml.DiagramaClase;
import bo.edu.caseapp.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "version_diagrama")
public class VersionDiagrama {
  @Id
  @GeneratedValue
  private UUID id;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "diagrama_id")
  private DiagramaClase diagrama;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id")
  private User usuario;

  @Column(name = "numero_version", nullable = false)
  private int numeroVersion;

  @Column(length = 255)
  private String descripcion;

  @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT")
  private String snapshotJson;

  @Column(nullable = false)
  private Instant fecha = Instant.now();

  protected VersionDiagrama() {}

  public VersionDiagrama(DiagramaClase diagrama, User usuario, int numeroVersion, String descripcion, String snapshotJson) {
    this.diagrama = diagrama;
    this.usuario = usuario;
    this.numeroVersion = numeroVersion;
    this.descripcion = descripcion;
    this.snapshotJson = snapshotJson;
  }

  public UUID getId() { return id; }
  public DiagramaClase getDiagrama() { return diagrama; }
  public User getUsuario() { return usuario; }
  public int getNumeroVersion() { return numeroVersion; }
  public String getDescripcion() { return descripcion; }
  public String getSnapshotJson() { return snapshotJson; }
  public Instant getFecha() { return fecha; }
}
