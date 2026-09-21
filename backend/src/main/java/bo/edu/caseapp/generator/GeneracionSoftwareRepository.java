package bo.edu.caseapp.generator;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneracionSoftwareRepository extends JpaRepository<GeneracionSoftware, UUID> {
  List<GeneracionSoftware> findByDiagramaIdOrderByFechaDesc(UUID diagramaId);
}
