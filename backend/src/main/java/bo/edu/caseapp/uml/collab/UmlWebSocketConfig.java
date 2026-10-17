package bo.edu.caseapp.uml.collab;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class UmlWebSocketConfig implements WebSocketConfigurer {
  private final UmlWebSocketHandler handler;
  private final JwtWebSocketHandshakeInterceptor authInterceptor;
  private final String[] allowedOrigins;

  public UmlWebSocketConfig(
      UmlWebSocketHandler handler,
      JwtWebSocketHandshakeInterceptor authInterceptor,
      @Value("${app.cors.allowed-origins}") String allowedOrigins
  ) {
    this.handler = handler;
    this.authInterceptor = authInterceptor;
    this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toArray(String[]::new);
    if (this.allowedOrigins.length == 0 || java.util.Arrays.asList(this.allowedOrigins).contains("*")) {
      throw new IllegalArgumentException("WebSocket requires explicit allowed origins; wildcard origins are not permitted.");
    }
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
    handshakeHandler.setSupportedProtocols("case-events-v1");
    registry.addHandler(handler, "/ws/diagrams/{diagramId}")
        .addInterceptors(authInterceptor)
        .setHandshakeHandler(handshakeHandler)
        .setAllowedOrigins(allowedOrigins);
  }
}
