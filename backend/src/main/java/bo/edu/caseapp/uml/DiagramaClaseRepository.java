package bo.edu.caseapp.uml;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiagramaClaseRepository extends JpaRepository<DiagramaClase, UUID> {
  List<DiagramaClase> findByProjectIdOrderByFechaCreacionAsc(UUID projectId);
}
