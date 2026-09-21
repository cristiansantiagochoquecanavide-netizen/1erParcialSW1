package bo.edu.caseapp.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import bo.edu.caseapp.project.ProjectRequests.Create;
import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectServiceTest {
  @Test
  void creatorIsAddedAsOwner() {
    ProjectRepository projects = mock(ProjectRepository.class);
    ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    UserRepository users = mock(UserRepository.class);
    User creator = new User("Ana", "ana@example.com", "hash");
    when(users.findByEmailIgnoreCase("ana@example.com")).thenReturn(Optional.of(creator));
    when(projects.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));

    new ProjectService(projects, members, users).create("ana@example.com", new Create("Modelo", null));

    ArgumentCaptor<ProjectMember> member = ArgumentCaptor.forClass(ProjectMember.class);
    verify(members).save(member.capture());
    assertEquals(ProjectRole.PROPIETARIO, member.getValue().getRole());
    assertEquals(creator, member.getValue().getUser());
  }
}
