package bo.edu.caseapp.auth;

import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
  private final UserRepository users;
  public AppUserDetailsService(UserRepository users) { this.users = users; }
  @Override public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
    User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
    return org.springframework.security.core.userdetails.User.withUsername(user.getEmail()).password(user.getPasswordHash()).authorities("USER").build();
  }
}
