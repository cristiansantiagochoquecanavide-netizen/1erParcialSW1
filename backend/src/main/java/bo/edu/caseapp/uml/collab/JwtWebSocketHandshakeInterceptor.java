package bo.edu.caseapp.uml.collab;

import bo.edu.caseapp.auth.JwtService;
import bo.edu.caseapp.project.ProjectService;
import bo.edu.caseapp.uml.DiagramaClase;
import bo.edu.caseapp.uml.DiagramaClaseRepository;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class JwtWebSocketHandshakeInterceptor implements HandshakeInterceptor {
  static final String TOKEN_PROTOCOL_PREFIX = "bearer.";

  private final JwtService jwtService;
  private final ProjectService projectService;
  private final DiagramaClaseRepository diagrams;

  public JwtWebSocketHandshakeInterceptor(
      JwtService jwtService,
      ProjectService projectService,
      DiagramaClaseRepository diagrams
  ) {
    this.jwtService = jwtService;
    this.projectService = projectService;
    this.diagrams = diagrams;
  }

  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes
  ) {
    String token = request.getHeaders().getOrDefault("Sec-WebSocket-Protocol", java.util.List.of()).stream()
        .flatMap(value -> java.util.Arrays.stream(value.split(",")))
        .map(String::trim)
        .filter(protocol -> protocol.startsWith(TOKEN_PROTOCOL_PREFIX))
        .map(protocol -> protocol.substring(TOKEN_PROTOCOL_PREFIX.length()))
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse(null);
    if (token == null) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    final String email;
    try {
      email = jwtService.extractEmail(token);
    } catch (RuntimeException exception) {
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    try {
      String[] path = request.getURI().getPath().split("/");
      if (path.length < 4 || !"diagrams".equals(path[path.length - 2])) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }
      java.util.UUID diagramId = java.util.UUID.fromString(path[path.length - 1]);
      DiagramaClase diagram = diagrams.findById(diagramId).orElse(null);
      if (diagram == null) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return false;
      }

      java.util.UUID projectId = diagram.getProject().getId();
      projectService.requireAccess(projectId, email);
      attributes.put("authenticatedUserEmail", email);
      attributes.put("authorizedProjectId", projectId);
      attributes.put("authorizedDiagramId", diagramId);
      return true;
    } catch (RuntimeException exception) {
      response.setStatusCode(HttpStatus.FORBIDDEN);
      return false;
    }
  }

  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception
  ) {
    // No post-handshake work is required.
  }
}
