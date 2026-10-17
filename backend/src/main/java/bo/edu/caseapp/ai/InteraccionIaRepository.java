package bo.edu.caseapp.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteraccionIaRepository extends JpaRepository<InteraccionIa, UUID> {
  @EntityGraph(attributePaths = "usuario")
  List<InteraccionIa> findByDiagramaIdAndUsuarioEmailIgnoreCaseOrderByFechaDesc(UUID diagramaId, String email);
}
