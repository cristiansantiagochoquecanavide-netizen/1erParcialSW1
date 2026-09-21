package bo.edu.caseapp.project;
import java.time.Instant;
import java.util.UUID;
public final class ProjectResponses {
  private ProjectResponses() { }
  public record ProjectSummary(UUID id, String name, String description, ProjectRole role, Instant createdAt) { }
  public record ProjectDetail(UUID id, String name, String description, ProjectRole role, Instant createdAt) { }
  public record Member(UUID id, UUID userId, String name, String email, ProjectRole role) { }
}
