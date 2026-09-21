package bo.edu.caseapp.user;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class User {
  @Id @GeneratedValue private UUID id;
  @Column(nullable = false, length = 100) private String name;
  @Column(nullable = false, unique = true) private String email;
  @Column(name = "password_hash", nullable = false) private String passwordHash;
  @Column(nullable = false) private String status = "ACTIVE";
  @Column(name = "registered_at", nullable = false) private Instant registeredAt = Instant.now();
  protected User() { }
  public User(String name, String email, String passwordHash) { this.name = name; this.email = email; this.passwordHash = passwordHash; }
  public UUID getId() { return id; } public String getName() { return name; } public String getEmail() { return email; }
  public String getPasswordHash() { return passwordHash; } public String getStatus() { return status; }
}
