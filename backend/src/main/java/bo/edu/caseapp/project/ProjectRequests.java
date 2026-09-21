package bo.edu.caseapp.project;
import jakarta.validation.constraints.*;
public final class ProjectRequests {
  private ProjectRequests() { }
  public record Create(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description) { }
  public record Update(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description) { }
  public record AddMember(@NotBlank @Email String email, @NotNull ProjectRole role) { }
  public record UpdateMember(@NotNull ProjectRole role) { }
}
