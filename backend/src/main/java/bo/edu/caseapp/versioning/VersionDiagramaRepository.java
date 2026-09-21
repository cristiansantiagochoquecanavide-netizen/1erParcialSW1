package bo.edu.caseapp.versioning;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VersionDiagramaRepository extends JpaRepository<VersionDiagrama, UUID> {
  List<VersionDiagrama> findByDiagramaIdOrderByNumeroVersionDesc(UUID diagramaId);

  @Query("SELECT COALESCE(MAX(v.numeroVersion), 0) FROM VersionDiagrama v WHERE v.diagrama.id = :diagramaId")
  int findMaxVersionNumber(UUID diagramaId);

  Optional<VersionDiagrama> findByIdAndDiagramaId(UUID id, UUID diagramaId);
}
