package bo.edu.caseapp.project;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
  List<ProjectMember> findByUserId(UUID userId);
  List<ProjectMember> findByProjectId(UUID projectId);
  Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);
}
