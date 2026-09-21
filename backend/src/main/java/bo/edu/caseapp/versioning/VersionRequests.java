package bo.edu.caseapp.versioning;

import jakarta.validation.constraints.Size;

public final class VersionRequests {
  private VersionRequests() {}

  public record CreateVersion(@Size(max = 255) String descripcion) {}
}
