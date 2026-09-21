package bo.edu.caseapp.uml;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RelacionUMLRepository extends JpaRepository<RelacionUML, UUID> {
  List<RelacionUML> findByDiagramaId(UUID diagramaId);
  void deleteByElementoOrigenIdOrElementoDestinoId(UUID origenId, UUID destinoId);
}
