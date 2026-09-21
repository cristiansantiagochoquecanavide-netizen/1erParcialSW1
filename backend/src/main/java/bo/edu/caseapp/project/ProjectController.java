package bo.edu.caseapp.project;

import bo.edu.caseapp.project.ProjectRequests.*;
import bo.edu.caseapp.project.ProjectResponses.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/projects")
public class ProjectController {
  private final ProjectService service; public ProjectController(ProjectService service) { this.service = service; }
  @GetMapping public List<ProjectSummary> list(Authentication auth) { return service.list(auth.getName()); }
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public ProjectDetail create(Authentication auth, @Valid @RequestBody Create request) { return service.create(auth.getName(), request); }
  @GetMapping("/{projectId}") public ProjectDetail get(@PathVariable UUID projectId, Authentication auth) { return service.get(projectId, auth.getName()); }
  @PutMapping("/{projectId}") public ProjectDetail update(@PathVariable UUID projectId, Authentication auth, @Valid @RequestBody Update request) { return service.update(projectId, auth.getName(), request); }
  @DeleteMapping("/{projectId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable UUID projectId, Authentication auth) { service.delete(projectId, auth.getName()); }
  @GetMapping("/{projectId}/members") public List<Member> members(@PathVariable UUID projectId, Authentication auth) { return service.listMembers(projectId, auth.getName()); }
  @PostMapping("/{projectId}/members") @ResponseStatus(HttpStatus.CREATED) public Member addMember(@PathVariable UUID projectId, Authentication auth, @Valid @RequestBody AddMember request) { return service.addMember(projectId, auth.getName(), request); }
  @PutMapping("/{projectId}/members/{memberId}") public Member updateMember(@PathVariable UUID projectId, @PathVariable UUID memberId, Authentication auth, @Valid @RequestBody UpdateMember request) { return service.updateMember(projectId, memberId, auth.getName(), request); }
  @DeleteMapping("/{projectId}/members/{memberId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeMember(@PathVariable UUID projectId, @PathVariable UUID memberId, Authentication auth) { service.removeMember(projectId, memberId, auth.getName()); }
}
