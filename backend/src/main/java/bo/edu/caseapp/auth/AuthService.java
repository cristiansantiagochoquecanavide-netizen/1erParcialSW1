package bo.edu.caseapp.auth;

import bo.edu.caseapp.error.BadRequestException;
import bo.edu.caseapp.user.User;
import bo.edu.caseapp.user.UserRepository;
import java.util.Locale;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
  private final UserRepository users; private final PasswordEncoder passwordEncoder; private final AuthenticationManager authenticationManager; private final JwtService jwtService;
  public AuthService(UserRepository users, PasswordEncoder passwordEncoder, AuthenticationManager authenticationManager, JwtService jwtService) { this.users = users; this.passwordEncoder = passwordEncoder; this.authenticationManager = authenticationManager; this.jwtService = jwtService; }
  public AuthResponse register(RegisterRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByEmailIgnoreCase(email)) throw new BadRequestException("El correo ya está registrado.");
    User user = users.save(new User(request.name().trim(), email, passwordEncoder.encode(request.password())));
    return response(user);
  }
  public AuthResponse login(LoginRequest request) {
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    try { authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password())); }
    catch (AuthenticationException exception) { throw new BadRequestException("Credenciales inválidas."); }
    User user = users.findByEmailIgnoreCase(email).orElseThrow(); return response(user);
  }
  private AuthResponse response(User user) { return new AuthResponse(jwtService.createToken(user.getEmail()), user.getName(), user.getEmail()); }
}
