package bo.edu.caseapp.project;

import bo.edu.caseapp.user.User;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "project")
public class Project {
  @Id @GeneratedValue private UUID id;
  @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "creator_id") private User creator;
  @Column(nullable = false, length = 120) private String name;
  @Column(length = 500) private String description;
  @Column(nullable = false) private String status = "ACTIVE";
  @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
  protected Project() { }
  public Project(User creator, String name, String description) { this.creator = creator; this.name = name; this.description = description; }
  public UUID getId() { return id; } public User getCreator() { return creator; } public String getName() { return name; }
  public String getDescription() { return description; } public Instant getCreatedAt() { return createdAt; }
  public void update(String name, String description) { this.name = name; this.description = description; }
}
