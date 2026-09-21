package bo.edu.caseapp.uml;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ElementoUMLRepository extends JpaRepository<ElementoUML, UUID> {
  List<ElementoUML> findByDiagramaId(UUID diagramaId);
}
