package bo.edu.caseapp.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteraccionIaRepository extends JpaRepository<InteraccionIa, UUID> {
  List<InteraccionIa> findByDiagramaIdOrderByFechaDesc(UUID diagramaId);
}
