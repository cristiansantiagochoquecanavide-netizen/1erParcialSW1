package bo.edu.caseapp.project;

import bo.edu.caseapp.user.User;
import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "project_member", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
public class ProjectMember {
  @Id @GeneratedValue private UUID id;
  @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "project_id") private Project project;
  @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "user_id") private User user;
  @Enumerated(EnumType.STRING) @Column(name = "project_role", nullable = false) private ProjectRole role;
  protected ProjectMember() { }
  public ProjectMember(Project project, User user, ProjectRole role) { this.project = project; this.user = user; this.role = role; }
  public UUID getId() { return id; } public Project getProject() { return project; } public User getUser() { return user; }
  public ProjectRole getRole() { return role; } public void setRole(ProjectRole role) { this.role = role; }
}
