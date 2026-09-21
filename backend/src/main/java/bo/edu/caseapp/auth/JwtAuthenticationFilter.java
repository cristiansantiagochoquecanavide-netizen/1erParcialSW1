package bo.edu.caseapp.auth;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
  private final JwtService jwtService; private final UserDetailsService userDetailsService;
  public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) { this.jwtService = jwtService; this.userDetailsService = userDetailsService; }
  @Override protected void doFilterInternal(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) { chain.doFilter(request, response); return; }
    try {
      String email = jwtService.extractEmail(header.substring(7));
      if (SecurityContextHolder.getContext().getAuthentication() == null) {
        var principal = userDetailsService.loadUserByUsername(email);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    } catch (RuntimeException ignored) { }
    chain.doFilter(request, response);
  }
}
